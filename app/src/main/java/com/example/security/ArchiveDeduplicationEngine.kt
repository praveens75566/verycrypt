package com.example.security

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ArchiveEntryItem(
    val name: String,
    val sizeBytes: Long,
    val sha256Hex: String,
    val isDuplicate: Boolean = false,
    val originalCopyName: String? = null
)

data class DuplicateCluster(
    val sha256Hex: String,
    val originalName: String,
    val sizeBytes: Long,
    val duplicateNames: List<String>
)

data class ArchiveScanResult(
    val archiveName: String,
    val totalEntries: Int,
    val uniqueCount: Int,
    val duplicateCount: Int,
    val totalSizeBytes: Long,
    val wastedSizeBytes: Long,
    val entries: List<ArchiveEntryItem>,
    val duplicateClusters: List<DuplicateCluster>
)

object ArchiveDeduplicationEngine {

    private const val BUFFER_SIZE = 64 * 1024

    suspend fun scanZipArchive(
        context: Context,
        zipUri: Uri,
        onProgress: ((Float) -> Unit)? = null
    ): ArchiveScanResult = withContext(Dispatchers.IO) {
        val (archiveName, totalArchiveBytes) = HashingEngine.getUriMetadata(context, zipUri)
        val entries = mutableListOf<ArchiveEntryItem>()
        val seenHashMap = mutableMapOf<String, String>() // sha256 -> original entry name
        val duplicateClustersMap = mutableMapOf<String, MutableList<String>>() // sha256 -> list of duplicate entry names
        val sizeMap = mutableMapOf<String, Long>()

        val inputStream = context.contentResolver.openInputStream(zipUri)
            ?: throw IllegalStateException("Cannot open ZIP archive stream")

        ZipInputStream(inputStream).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val digest = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    var entryBytes = 0L

                    while (zis.read(buffer).also { bytesRead = it } != -1) {
                        digest.update(buffer, 0, bytesRead)
                        entryBytes += bytesRead
                    }

                    val hashHex = HashingEngine.bytesToHex(digest.digest())
                    val resolvedSize = if (entry.size >= 0) entry.size else entryBytes
                    sizeMap[hashHex] = resolvedSize

                    val originalName = seenHashMap[hashHex]
                    if (originalName != null) {
                        // Duplicate detected
                        entries.add(
                            ArchiveEntryItem(
                                name = entry.name,
                                sizeBytes = resolvedSize,
                                sha256Hex = hashHex,
                                isDuplicate = true,
                                originalCopyName = originalName
                            )
                        )
                        duplicateClustersMap.getOrPut(hashHex) { mutableListOf() }.add(entry.name)
                    } else {
                        // First occurrence
                        seenHashMap[hashHex] = entry.name
                        entries.add(
                            ArchiveEntryItem(
                                name = entry.name,
                                sizeBytes = resolvedSize,
                                sha256Hex = hashHex,
                                isDuplicate = false
                            )
                        )
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val totalSize = entries.sumOf { it.sizeBytes }
        val wastedBytes = entries.filter { it.isDuplicate }.sumOf { it.sizeBytes }

        val clusters = duplicateClustersMap.map { (hash, dupNames) ->
            DuplicateCluster(
                sha256Hex = hash,
                originalName = seenHashMap[hash] ?: "Unknown",
                sizeBytes = sizeMap[hash] ?: 0L,
                duplicateNames = dupNames
            )
        }

        ArchiveScanResult(
            archiveName = archiveName,
            totalEntries = entries.size,
            uniqueCount = seenHashMap.size,
            duplicateCount = entries.count { it.isDuplicate },
            totalSizeBytes = totalSize,
            wastedSizeBytes = wastedBytes,
            entries = entries,
            duplicateClusters = clusters
        )
    }

    suspend fun createDeduplicatedZip(
        context: Context,
        sourceZipUri: Uri,
        destinationOutputStream: OutputStream
    ): Int = withContext(Dispatchers.IO) {
        val seenHashes = mutableSetOf<String>()
        var preservedCount = 0

        val inputStream = context.contentResolver.openInputStream(sourceZipUri)
            ?: throw IllegalStateException("Cannot open input ZIP")

        ZipInputStream(inputStream).use { zis ->
            ZipOutputStream(destinationOutputStream).use { zos ->
                var entry: ZipEntry? = zis.nextEntry
                val buffer = ByteArray(BUFFER_SIZE)

                while (entry != null) {
                    if (entry.isDirectory) {
                        // Write directory entry
                        zos.putNextEntry(ZipEntry(entry.name))
                        zos.closeEntry()
                    } else {
                        // Read entry bytes and compute hash
                        val baos = ByteArrayOutputStream()
                        val digest = MessageDigest.getInstance("SHA-256")
                        var bytesRead: Int

                        while (zis.read(buffer).also { bytesRead = it } != -1) {
                            baos.write(buffer, 0, bytesRead)
                            digest.update(buffer, 0, bytesRead)
                        }

                        val hashHex = HashingEngine.bytesToHex(digest.digest())
                        if (!seenHashes.contains(hashHex)) {
                            // Keep first copy
                            seenHashes.add(hashHex)
                            val newEntry = ZipEntry(entry.name)
                            zos.putNextEntry(newEntry)
                            zos.write(baos.toByteArray())
                            zos.closeEntry()
                            preservedCount++
                        }
                        // Duplicates are simply omitted from output!
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
                zos.finish()
            }
        }
        preservedCount
    }
}
