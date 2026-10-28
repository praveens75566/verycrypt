package com.example.security

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest

data class FileHashResult(
    val uri: Uri?,
    val fileName: String,
    val sizeBytes: Long,
    val sha256Hex: String,
    val sha512Hex: String = "",
    val mimeType: String? = null
)

data class TextHashResult(
    val rawText: String,
    val lengthChars: Int,
    val sizeBytes: Int,
    val sha256Hex: String,
    val sha512Hex: String
)

data class FolderManifestEntry(
    val relativePath: String,
    val sizeBytes: Long,
    val sha256Hex: String
)

data class FolderManifestResult(
    val folderName: String,
    val totalFiles: Int,
    val totalSizeBytes: Long,
    val compositeRootHash: String,
    val manifestText: String,
    val entries: List<FolderManifestEntry>
)

object HashingEngine {

    private const val BUFFER_SIZE = 64 * 1024 // 64 KB streaming buffer

    fun computeSha256Bytes(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return bytesToHex(digest.digest(bytes))
    }

    fun computeSha512Bytes(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-512")
        return bytesToHex(digest.digest(bytes))
    }

    fun computeSha256Text(text: String): String {
        return computeSha256Bytes(text.toByteArray(Charsets.UTF_8))
    }

    fun computeSha512Text(text: String): String {
        return computeSha512Bytes(text.toByteArray(Charsets.UTF_8))
    }

    fun hashTextDirectly(text: String): TextHashResult {
        val bytes = text.toByteArray(Charsets.UTF_8)
        return TextHashResult(
            rawText = text,
            lengthChars = text.length,
            sizeBytes = bytes.size,
            sha256Hex = computeSha256Bytes(bytes),
            sha512Hex = computeSha512Bytes(bytes)
        )
    }

    suspend fun computeSha512(
        inputStream: InputStream,
        totalBytes: Long = -1L,
        onProgress: ((Float) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-512")
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        var totalRead = 0L

        inputStream.use { stream ->
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
                totalRead += bytesRead
                if (totalBytes > 0 && onProgress != null) {
                    val progress = (totalRead.toFloat() / totalBytes).coerceIn(0f, 1f)
                    onProgress(progress)
                }
            }
        }
        bytesToHex(digest.digest())
    }

    suspend fun computeSha256(
        inputStream: InputStream,
        totalBytes: Long = -1L,
        onProgress: ((Float) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        var totalRead = 0L

        inputStream.use { stream ->
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
                totalRead += bytesRead
                if (totalBytes > 0 && onProgress != null) {
                    val progress = (totalRead.toFloat() / totalBytes).coerceIn(0f, 1f)
                    onProgress(progress)
                }
            }
        }
        bytesToHex(digest.digest())
    }

    suspend fun hashUri(
        context: Context,
        uri: Uri,
        onProgress: ((Float) -> Unit)? = null
    ): FileHashResult = withContext(Dispatchers.IO) {
        val (fileName, sizeBytes) = getUriMetadata(context, uri)
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open input stream for $uri")

        val hashHex = computeSha256(inputStream, sizeBytes, onProgress)
        val mimeType = context.contentResolver.getType(uri)

        FileHashResult(
            uri = uri,
            fileName = fileName,
            sizeBytes = sizeBytes,
            sha256Hex = hashHex,
            mimeType = mimeType
        )
    }

    suspend fun hashMultipleUris(
        context: Context,
        uris: List<Uri>
    ): List<FileHashResult> = withContext(Dispatchers.IO) {
        uris.map { uri ->
            hashUri(context, uri)
        }
    }

    fun compareHashes(hashA: String, hashB: String): Boolean {
        val cleanA = hashA.trim().replace(":", "").replace(" ", "").lowercase()
        val cleanB = hashB.trim().replace(":", "").replace(" ", "").lowercase()
        return cleanA.isNotEmpty() && cleanA == cleanB
    }

    fun findDuplicates(results: List<FileHashResult>): Map<String, List<FileHashResult>> {
        return results.groupBy { it.sha256Hex.lowercase() }
            .filter { it.value.size > 1 }
    }

    fun generateChecksumReport(results: List<FileHashResult>): String {
        val sb = StringBuilder()
        sb.append("# VeriCrypt SHA-256 Checksum Report\n")
        sb.append("# Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}\n")
        sb.append("# Total Files: ${results.size}\n\n")
        for (item in results) {
            sb.append("${item.sha256Hex}  ${item.fileName}\n")
        }
        return sb.toString()
    }

    suspend fun computeFolderManifest(
        entries: List<FolderManifestEntry>,
        folderName: String
    ): FolderManifestResult = withContext(Dispatchers.Default) {
        val sortedEntries = entries.sortedBy { it.relativePath }
        val sb = StringBuilder()
        val rootDigest = MessageDigest.getInstance("SHA-256")
        var totalBytes = 0L

        sb.append("# VeriCrypt Deterministic Folder Manifest: $folderName\n")
        sb.append("# Algorithm: SHA-256\n\n")

        for (entry in sortedEntries) {
            val line = "${entry.sha256Hex}  ${entry.relativePath}\n"
            sb.append(line)
            rootDigest.update(line.toByteArray(Charsets.UTF_8))
            totalBytes += entry.sizeBytes
        }

        val compositeHash = bytesToHex(rootDigest.digest())

        FolderManifestResult(
            folderName = folderName,
            totalFiles = sortedEntries.size,
            totalSizeBytes = totalBytes,
            compositeRootHash = compositeHash,
            manifestText = sb.toString(),
            entries = sortedEntries
        )
    }

    fun getUriMetadata(context: Context, uri: Uri): Pair<String, Long> {
        var name = "unknown_file"
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) name = cursor.getString(nameIndex) ?: name
                    if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                }
            }
        } catch (e: Exception) {
            name = uri.lastPathSegment ?: "unknown_file"
        }
        return Pair(name, size)
    }

    fun bytesToHex(bytes: ByteArray): String {
        val hexChars = "0123456789abcdef"
        val result = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val i = b.toInt() and 0xFF
            result.append(hexChars[i ushr 4])
            result.append(hexChars[i and 0x0F])
        }
        return result.toString()
    }

    fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) +
                    Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
