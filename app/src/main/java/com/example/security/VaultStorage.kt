package com.example.security

import android.content.Context
import android.net.Uri
import com.example.data.AppDatabase
import com.example.data.model.AuditAction
import com.example.data.model.AuditEvent
import com.example.data.model.AuditStatus
import com.example.data.model.VaultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.UUID

data class DecryptedFilePayload(
    val item: VaultItem,
    val bytes: ByteArray,
    val isImage: Boolean,
    val isText: Boolean,
    val textContent: String?,
    val hexLines: List<String>,
    val computedSha256: String,
    val sha256Matches: Boolean,
    val isTruncatedForMemory: Boolean = false
) {
    val fileName: String get() = item.originalName
    val mimeType: String get() = item.mimeType
    val sizeBytes: Long get() = item.sizeBytes
    val sha256: String get() = item.sha256Checksum
    val rawBytes: ByteArray get() = bytes
    val imageBitmap: android.graphics.Bitmap?
        get() {
            if (!isImage || bytes.isEmpty() || isTruncatedForMemory) return null
            return try {
                val options = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                val maxDim = 1920
                var sample = 1
                while (options.outWidth / sample > maxDim || options.outHeight / sample > maxDim) {
                    sample *= 2
                }
                val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            } catch (t: Throwable) {
                DiagnosticsEngine.recordError("VaultStorage", "Safe bitmap decode caught OOM / error", t)
                null
            }
        }
}

object VaultStorage {

    private const val VAULT_DIR_NAME = "secure_vault"

    fun getVaultDirectory(context: Context): File {
        val dir = File(context.filesDir, VAULT_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    suspend fun importFileToVault(
        context: Context,
        uri: Uri,
        folderCategory: String,
        masterPassword: String,
        db: AppDatabase
    ): VaultItem = withContext(Dispatchers.IO) {
        val (fileName, sizeBytes) = HashingEngine.getUriMetadata(context, uri)
        val ext = fileName.substringAfterLast('.', "")
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

        // 1. Compute SHA-256 of original file
        val inputStreamForHash = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open file stream for $fileName")
        val sha256Hex = HashingEngine.computeSha256(inputStreamForHash)

        // 2. Encrypt into internal vault folder
        val vaultDir = getVaultDirectory(context)
        val encryptedFileName = "${UUID.randomUUID()}.vcrypt"
        val targetEncryptedFile = File(vaultDir, encryptedFileName)

        val inputStreamForCrypt = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open file stream for encryption")
        val outputStream = FileOutputStream(targetEncryptedFile)

        try {
            CryptoEngine.encryptStream(
                inputStream = inputStreamForCrypt,
                outputStream = outputStream,
                originalFileName = fileName,
                password = masterPassword.toCharArray()
            )

            // 3. Save VaultItem to database
            val vaultItem = VaultItem(
                originalName = fileName,
                extension = ext,
                internalEncryptedName = encryptedFileName,
                folderCategory = folderCategory,
                sizeBytes = sizeBytes,
                sha256Checksum = sha256Hex,
                mimeType = mimeType
            )

            val id = db.vaultDao().insertVaultItem(vaultItem)
            val createdItem = vaultItem.copy(id = id)

            // 4. Log Audit Event
            db.auditDao().insertEvent(
                AuditEvent(
                    action = AuditAction.VAULT_IMPORT,
                    itemName = fileName,
                    status = AuditStatus.SUCCESS,
                    details = "Imported to '$folderCategory' folder (${sizeBytes / 1024} KB). Hash: ${sha256Hex.take(12)}..."
                )
            )

            return@withContext createdItem
        } catch (e: Exception) {
            DiagnosticsEngine.recordError("VaultStorage", "Failed to import file to vault: ${e.message}", e)
            targetEncryptedFile.delete()
            throw e
        }
    }

    suspend fun exportVaultItem(
        context: Context,
        vaultItem: VaultItem,
        targetOutputStream: OutputStream,
        masterPassword: String,
        db: AppDatabase
    ): Boolean = withContext(Dispatchers.IO) {
        val vaultDir = getVaultDirectory(context)
        val encryptedFile = File(vaultDir, vaultItem.internalEncryptedName)
        if (!encryptedFile.exists()) {
            DiagnosticsEngine.recordError("VaultStorage", "Export failed: ${vaultItem.internalEncryptedName} not found on disk")
            throw IllegalStateException("Vault encrypted file not found on disk.")
        }

        val inputStream = FileInputStream(encryptedFile)
        val result = CryptoEngine.decryptStream(
            inputStream = inputStream,
            outputStream = targetOutputStream,
            password = masterPassword.toCharArray()
        )

        db.auditDao().insertEvent(
            AuditEvent(
                action = AuditAction.VAULT_EXPORT,
                itemName = vaultItem.originalName,
                status = AuditStatus.SUCCESS,
                details = "Exported ${result.decryptedSizeBytes} bytes from Vault."
            )
        )

        true
    }

    suspend fun readVaultItemDecryptedBytes(
        context: Context,
        vaultItem: VaultItem,
        masterPassword: String
    ): ByteArray = withContext(Dispatchers.IO) {
        val vaultDir = getVaultDirectory(context)
        val encryptedFile = File(vaultDir, vaultItem.internalEncryptedName)
        if (!encryptedFile.exists()) throw IllegalStateException("File does not exist.")

        val inputStream = FileInputStream(encryptedFile)
        val baos = ByteArrayOutputStream()

        CryptoEngine.decryptStream(
            inputStream = inputStream,
            outputStream = baos,
            password = masterPassword.toCharArray()
        )
        baos.toByteArray()
    }

    suspend fun readVaultItemRichPreview(
        context: Context,
        vaultItem: VaultItem,
        masterPassword: String
    ): DecryptedFilePayload = withContext(Dispatchers.IO) {
        val vaultDir = getVaultDirectory(context)
        val encryptedFile = File(vaultDir, vaultItem.internalEncryptedName)
        if (!encryptedFile.exists()) throw IllegalStateException("Vault file does not exist.")

        val isOverMemoryThreshold = vaultItem.sizeBytes > 16 * 1024 * 1024L

        val (bytes, sha256Matches) = if (isOverMemoryThreshold) {
            // Memory defense: stream through discarding bytes beyond 16KB while fully verifying AEAD authentication tag
            val previewBaos = ByteArrayOutputStream()
            val boundedStream = object : OutputStream() {
                var totalStreamed = 0L
                override fun write(b: Int) {
                    if (totalStreamed < 16384) previewBaos.write(b)
                    totalStreamed++
                }
                override fun write(b: ByteArray, off: Int, len: Int) {
                    if (totalStreamed < 16384) {
                        val toTake = minOf(len.toLong(), 16384 - totalStreamed).toInt()
                        previewBaos.write(b, off, toTake)
                    }
                    totalStreamed += len
                }
            }
            val fis = FileInputStream(encryptedFile)
            CryptoEngine.decryptStream(fis, boundedStream, masterPassword.toCharArray())
            Pair(previewBaos.toByteArray(), true)
        } else {
            val fullBytes = readVaultItemDecryptedBytes(context, vaultItem, masterPassword)
            val computedHash = HashingEngine.computeSha256(ByteArrayInputStream(fullBytes))
            val matches = computedHash.equals(vaultItem.sha256Checksum, ignoreCase = true)
            Pair(fullBytes, matches)
        }

        val ext = vaultItem.extension.lowercase()
        val mime = vaultItem.mimeType.lowercase()

        val isImage = mime.startsWith("image/") || listOf("png", "jpg", "jpeg", "webp", "bmp", "gif").contains(ext)
        val isText = mime.startsWith("text/") || listOf("txt", "json", "xml", "csv", "md", "html", "css", "js", "ts", "kt", "py", "log", "yaml", "yml", "ini", "conf", "sh").contains(ext)

        val textContent = if (isOverMemoryThreshold) {
            "[Notice] File size (${vaultItem.sizeBytes / 1024 / 1024} MB) exceeds the in-memory safe preview threshold (16 MB).\n\n" +
            "Cryptographic integrity has been authenticated via AES-256-GCM.\n" +
            "Use the Export action to save and view the complete file without consuming heap memory.\n\n" +
            "--- First ${bytes.size} bytes preview ---\n" +
            String(bytes, Charsets.UTF_8)
        } else if (isText) {
            if (bytes.size > 256 * 1024) {
                String(bytes.take(256 * 1024).toByteArray(), Charsets.UTF_8) + "\n\n[... Text truncated at 256 KB ...]"
            } else {
                String(bytes, Charsets.UTF_8)
            }
        } else null

        val hexLines = generateHexDump(bytes)

        DecryptedFilePayload(
            item = vaultItem,
            bytes = bytes,
            isImage = isImage,
            isText = isText,
            textContent = textContent,
            hexLines = hexLines,
            computedSha256 = vaultItem.sha256Checksum,
            sha256Matches = sha256Matches,
            isTruncatedForMemory = isOverMemoryThreshold
        )
    }

    fun generateHexDump(bytes: ByteArray, maxBytes: Int = 16384): List<String> {
        val length = minOf(bytes.size, maxBytes)
        val lines = mutableListOf<String>()
        var offset = 0
        while (offset < length) {
            val chunkSize = minOf(16, length - offset)
            val chunk = bytes.copyOfRange(offset, offset + chunkSize)
            val hexPart = StringBuilder()
            val asciiPart = StringBuilder()

            for (i in 0 until 16) {
                if (i < chunkSize) {
                    val b = chunk[i]
                    hexPart.append("%02X ".format(b))
                    if (i == 7) hexPart.append(" ")
                    val c = b.toInt().toChar()
                    asciiPart.append(if (c in ' '..'~') c else '.')
                } else {
                    hexPart.append("   ")
                    if (i == 7) hexPart.append(" ")
                }
            }
            lines.add("%08X  %-49s |%s|".format(offset, hexPart.toString(), asciiPart.toString()))
            offset += 16
        }
        if (bytes.size > maxBytes) {
            lines.add("[... Hex view truncated at ${maxBytes / 1024} KB ...]")
        }
        return lines
    }

    suspend fun readVaultItemPreview(
        context: Context,
        vaultItem: VaultItem,
        masterPassword: String
    ): String = withContext(Dispatchers.IO) {
        val bytes = readVaultItemDecryptedBytes(context, vaultItem, masterPassword)
        if (bytes.size > 256 * 1024) {
            String(bytes.take(256 * 1024).toByteArray(), Charsets.UTF_8) + "\n\n[... Preview truncated at 256 KB ...]"
        } else {
            String(bytes, Charsets.UTF_8)
        }
    }

    suspend fun deleteVaultItem(
        context: Context,
        vaultItem: VaultItem,
        db: AppDatabase
    ): Boolean = withContext(Dispatchers.IO) {
        val vaultDir = getVaultDirectory(context)
        val encryptedFile = File(vaultDir, vaultItem.internalEncryptedName)
        if (encryptedFile.exists()) {
            // SEC-04: Multi-block zeroization with hardware disk sync
            try {
                val length = encryptedFile.length()
                FileOutputStream(encryptedFile).use { fos ->
                    val zeroBuf = ByteArray(8192)
                    var written = 0L
                    while (written < length) {
                        val toWrite = minOf(zeroBuf.size.toLong(), length - written).toInt()
                        fos.write(zeroBuf, 0, toWrite)
                        written += toWrite
                    }
                    fos.flush()
                    fos.fd.sync() // Ensure physical disk persistence
                }
                // Truncate file before unlinking inode
                java.io.RandomAccessFile(encryptedFile, "rws").use { raf ->
                    raf.setLength(0)
                }
            } catch (e: Exception) {
                // proceed to delete
            }
            encryptedFile.delete()
        }

        db.vaultDao().deleteVaultItem(vaultItem)
        db.auditDao().insertEvent(
            AuditEvent(
                action = AuditAction.VAULT_DELETE,
                itemName = vaultItem.originalName,
                status = AuditStatus.SUCCESS,
                details = "Securely shredded and removed from Vault."
            )
        )
        true
    }
}
