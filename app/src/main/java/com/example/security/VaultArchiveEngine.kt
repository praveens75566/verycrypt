package com.example.security

import android.content.Context
import com.example.data.AppDatabase
import com.example.data.model.AuditAction
import com.example.data.model.AuditEvent
import com.example.data.model.AuditStatus
import com.example.data.model.VaultItem
import com.example.data.model.VaultNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class VaultBackupSummary(
    val notesCount: Int,
    val filesCount: Int,
    val totalBytes: Long
)

data class VaultRestoreSummary(
    val restoredNotesCount: Int,
    val restoredFilesCount: Int,
    val totalBytesRestored: Long
) {
    val restoredNotes: Int get() = restoredNotesCount
    val restoredFiles: Int get() = restoredFilesCount
}

object VaultArchiveEngine {

    private const val MANIFEST_ENTRY_NAME = "manifest.json"
    private const val FILES_DIR_PREFIX = "files/"

    suspend fun createVaultBackup(
        context: Context,
        db: AppDatabase,
        destinationUri: android.net.Uri,
        backupPassword: String
    ): VaultBackupSummary = withContext(Dispatchers.IO) {
        val outputStream = context.contentResolver.openOutputStream(destinationUri)
            ?: throw IllegalStateException("Cannot open destination output stream")
        outputStream.use { os ->
            createVaultBackup(context, db, backupPassword, os)
        }
    }

    suspend fun restoreVaultBackup(
        context: Context,
        db: AppDatabase,
        sourceUri: android.net.Uri,
        backupPassword: String
    ): VaultRestoreSummary = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(sourceUri)
            ?: throw IllegalStateException("Cannot open source input stream")
        inputStream.use { `is` ->
            restoreVaultBackup(context, db, backupPassword, `is`)
        }
    }

    suspend fun createVaultBackup(
        context: Context,
        db: AppDatabase,
        backupPassword: String,
        targetOutputStream: OutputStream
    ): VaultBackupSummary = withContext(Dispatchers.IO) {
        val notes = db.vaultNoteDao().getAllNotesSnapshot()
        val items = db.vaultDao().getAllVaultItemsSnapshot()
        val vaultDir = VaultStorage.getVaultDirectory(context)

        // 1. Prepare Manifest JSON
        val manifestObj = JSONObject().apply {
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("appName", "VeriCrypt")

            val notesArray = JSONArray()
            notes.forEach { note ->
                notesArray.put(JSONObject().apply {
                    put("id", note.id)
                    put("title", note.title)
                    put("encryptedContent", note.encryptedContent)
                    put("category", note.category)
                    put("isPinned", note.isPinned)
                    put("colorIndex", note.colorIndex)
                    put("tags", note.tags)
                    put("algorithm", note.algorithm)
                    put("keyAlias", note.keyAlias)
                    put("encryptedSecretFields", note.encryptedSecretFields)
                    put("createdAt", note.createdAt)
                    put("updatedAt", note.updatedAt)
                })
            }
            put("notes", notesArray)

            val itemsArray = JSONArray()
            items.forEach { item ->
                itemsArray.put(JSONObject().apply {
                    put("id", item.id)
                    put("originalName", item.originalName)
                    put("extension", item.extension)
                    put("internalEncryptedName", item.internalEncryptedName)
                    put("folderCategory", item.folderCategory)
                    put("sizeBytes", item.sizeBytes)
                    put("sha256Checksum", item.sha256Checksum)
                    put("mimeType", item.mimeType)
                    put("createdAt", item.createdAt)
                })
            }
            put("items", itemsArray)
        }

        // 2. Build Unencrypted ZIP stream in a temporary file
        val tempZipFile = File.createTempFile("vcrypt_vault_archive_", ".zip", context.cacheDir)
        var totalPayloadBytes = 0L

        try {
            ZipOutputStream(FileOutputStream(tempZipFile)).use { zos ->
                // Add manifest entry
                val manifestBytes = manifestObj.toString(2).toByteArray(Charsets.UTF_8)
                val manifestEntry = ZipEntry(MANIFEST_ENTRY_NAME)
                zos.putNextEntry(manifestEntry)
                zos.write(manifestBytes)
                zos.closeEntry()
                totalPayloadBytes += manifestBytes.size

                // Add encrypted file blobs
                items.forEach { item ->
                    val fileOnDisk = File(vaultDir, item.internalEncryptedName)
                    if (fileOnDisk.exists()) {
                        val entry = ZipEntry("$FILES_DIR_PREFIX${item.internalEncryptedName}")
                        zos.putNextEntry(entry)
                        FileInputStream(fileOnDisk).use { fis ->
                            val buf = ByteArray(16384)
                            var read: Int
                            while (fis.read(buf).also { read = it } != -1) {
                                zos.write(buf, 0, read)
                                totalPayloadBytes += read
                            }
                        }
                        zos.closeEntry()
                    }
                }
                zos.flush()
            }

            // 3. Encrypt the entire ZIP into the target output stream with AES-256-GCM
            FileInputStream(tempZipFile).use { zipInput ->
                CryptoEngine.encryptStream(
                    inputStream = zipInput,
                    outputStream = targetOutputStream,
                    originalFileName = "vericrypt_vault_master.vcrypt-vault",
                    password = backupPassword.toCharArray()
                )
            }

            // 4. Log Audit Event
            db.auditDao().insertEvent(
                AuditEvent(
                    action = AuditAction.VAULT_EXPORT,
                    itemName = "Vault Master Backup",
                    status = AuditStatus.SUCCESS,
                    details = "Encrypted backup created: ${notes.size} notes, ${items.size} files (${totalPayloadBytes / 1024} KB)."
                )
            )

            VaultBackupSummary(
                notesCount = notes.size,
                filesCount = items.size,
                totalBytes = totalPayloadBytes
            )
        } finally {
            tempZipFile.delete()
        }
    }

    suspend fun restoreVaultBackup(
        context: Context,
        db: AppDatabase,
        backupPassword: String,
        sourceInputStream: InputStream
    ): VaultRestoreSummary = withContext(Dispatchers.IO) {
        val vaultDir = VaultStorage.getVaultDirectory(context)
        val tempDecryptedZip = File.createTempFile("vcrypt_restore_zip_", ".tmp", context.cacheDir)
        val stagingDir = File(context.cacheDir, "staging_${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val newlyMovedFiles = mutableListOf<File>()

        try {
            // 1. Decrypt container
            FileOutputStream(tempDecryptedZip).use { fos ->
                CryptoEngine.decryptStream(
                    inputStream = sourceInputStream,
                    outputStream = fos,
                    password = backupPassword.toCharArray()
                )
            }

            // 2. Read ZIP entries into staging directory to ensure atomicity
            var manifestContent: String? = null
            var restoredBytes = 0L
            val stagedFiles = mutableListOf<String>()

            ZipInputStream(FileInputStream(tempDecryptedZip)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    if (entryName == MANIFEST_ENTRY_NAME) {
                        manifestContent = String(zis.readBytes(), Charsets.UTF_8)
                    } else if (entryName.startsWith(FILES_DIR_PREFIX)) {
                        val fileName = entryName.removePrefix(FILES_DIR_PREFIX)
                        if (fileName.isNotBlank()) {
                            val targetFile = File(stagingDir, fileName)
                            // SEC-01: Zip Slip canonical boundary defense
                            if (!targetFile.canonicalPath.startsWith(stagingDir.canonicalPath + File.separator)) {
                                throw SecurityException("Security Violation: Zip Slip path traversal detected in entry '$entryName'")
                            }
                            FileOutputStream(targetFile).use { fos ->
                                val buf = ByteArray(16384)
                                var read: Int
                                while (zis.read(buf).also { read = it } != -1) {
                                    fos.write(buf, 0, read)
                                    restoredBytes += read
                                }
                                fos.flush()
                                fos.fd.sync()
                            }
                            stagedFiles.add(fileName)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            if (manifestContent == null) {
                throw IllegalStateException("Corrupted archive: manifest.json is missing.")
            }

            // 3. Move files from staging into vaultDir atomically
            for (fileName in stagedFiles) {
                val stagedFile = File(stagingDir, fileName)
                val targetFile = File(vaultDir, fileName)
                stagedFile.copyTo(targetFile, overwrite = true)
                newlyMovedFiles.add(targetFile)
            }

            // 4. Parse manifest and insert records into database
            val manifestJson = JSONObject(manifestContent!!)
            val notesArray = manifestJson.optJSONArray("notes") ?: JSONArray()
            val itemsArray = manifestJson.optJSONArray("items") ?: JSONArray()

            var restoredNotesCount = 0
            for (i in 0 until notesArray.length()) {
                val nObj = notesArray.getJSONObject(i)
                val note = VaultNote(
                    title = nObj.optString("title", "Restored Note"),
                    encryptedContent = nObj.optString("encryptedContent", ""),
                    category = nObj.optString("category", "General"),
                    isPinned = nObj.optBoolean("isPinned", false),
                    colorIndex = nObj.optInt("colorIndex", 0),
                    tags = nObj.optString("tags", ""),
                    algorithm = nObj.optString("algorithm", "AES-256-GCM"),
                    keyAlias = nObj.optString("keyAlias", "MASTER_VAULT"),
                    encryptedSecretFields = nObj.optString("encryptedSecretFields", ""),
                    createdAt = nObj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = nObj.optLong("updatedAt", System.currentTimeMillis())
                )
                db.vaultNoteDao().insertNote(note)
                restoredNotesCount++
            }

            var restoredFilesCount = 0
            for (i in 0 until itemsArray.length()) {
                val iObj = itemsArray.getJSONObject(i)
                val internalName = iObj.optString("internalEncryptedName", "")
                val fileOnDisk = File(vaultDir, internalName)
                if (fileOnDisk.exists()) {
                    val item = VaultItem(
                        originalName = iObj.optString("originalName", "restored_file"),
                        extension = iObj.optString("extension", ""),
                        internalEncryptedName = internalName,
                        folderCategory = iObj.optString("folderCategory", "Default"),
                        sizeBytes = iObj.optLong("sizeBytes", fileOnDisk.length()),
                        sha256Checksum = iObj.optString("sha256Checksum", ""),
                        mimeType = iObj.optString("mimeType", "application/octet-stream"),
                        createdAt = iObj.optLong("createdAt", System.currentTimeMillis())
                    )
                    db.vaultDao().insertVaultItem(item)
                    restoredFilesCount++
                }
            }

            // 5. Log Audit Event
            db.auditDao().insertEvent(
                AuditEvent(
                    action = AuditAction.VAULT_IMPORT,
                    itemName = "Vault Restore Archive",
                    status = AuditStatus.SUCCESS,
                    details = "Restored $restoredNotesCount notes and $restoredFilesCount files (${restoredBytes / 1024} KB)."
                )
            )

            VaultRestoreSummary(
                restoredNotesCount = restoredNotesCount,
                restoredFilesCount = restoredFilesCount,
                totalBytesRestored = restoredBytes
            )
        } catch (e: Exception) {
            DiagnosticsEngine.recordError("VaultArchiveEngine", "Restore failed, performing atomic rollback: ${e.message}", e)
            // Roll back any newly moved files to avoid corrupting the vault
            newlyMovedFiles.forEach { it.delete() }
            throw e
        } finally {
            stagingDir.deleteRecursively()
            shredFile(tempDecryptedZip)
        }
    }

    private fun shredFile(file: File) {
        if (!file.exists()) return
        try {
            val length = file.length()
            if (length > 0) {
                FileOutputStream(file).use { fos ->
                    val zeroBuf = ByteArray(8192)
                    var written = 0L
                    while (written < length) {
                        val toWrite = minOf(zeroBuf.size.toLong(), length - written).toInt()
                        fos.write(zeroBuf, 0, toWrite)
                        written += toWrite
                    }
                    fos.flush()
                    fos.fd.sync()
                }
            }
        } catch (ignored: Exception) {
        } finally {
            file.delete()
        }
    }
}
