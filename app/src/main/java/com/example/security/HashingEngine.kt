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
    val sha3_512Hex: String = "",
    val mimeType: String? = null
)

data class TextHashResult(
    val rawText: String,
    val lengthChars: Int,
    val sizeBytes: Int,
    val sha256Hex: String,
    val sha512Hex: String,
    val sha3_512Hex: String = ""
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

    fun computeSha3_512Bytes(bytes: ByteArray): String {
        return try {
            val digest = MessageDigest.getInstance("SHA3-512")
            bytesToHex(digest.digest(bytes))
        } catch (_: Exception) {
            Sha3_512Engine.digest(bytes)
        }
    }

    fun computeSha256Text(text: String): String {
        return computeSha256Bytes(text.toByteArray(Charsets.UTF_8))
    }

    fun computeSha512Text(text: String): String {
        return computeSha512Bytes(text.toByteArray(Charsets.UTF_8))
    }

    fun computeSha3_512Text(text: String): String {
        return computeSha3_512Bytes(text.toByteArray(Charsets.UTF_8))
    }

    fun hashTextDirectly(text: String): TextHashResult {
        val bytes = text.toByteArray(Charsets.UTF_8)
        return TextHashResult(
            rawText = text,
            lengthChars = text.length,
            sizeBytes = bytes.size,
            sha256Hex = computeSha256Bytes(bytes),
            sha512Hex = computeSha512Bytes(bytes),
            sha3_512Hex = computeSha3_512Bytes(bytes)
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

    suspend fun computeSha3_512(
        inputStream: InputStream,
        totalBytes: Long = -1L,
        onProgress: ((Float) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        val nativeDigest = try {
            MessageDigest.getInstance("SHA3-512")
        } catch (_: Exception) {
            null
        }

        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        var totalRead = 0L

        if (nativeDigest != null) {
            inputStream.use { stream ->
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    nativeDigest.update(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalBytes > 0 && onProgress != null) {
                        val progress = (totalRead.toFloat() / totalBytes).coerceIn(0f, 1f)
                        onProgress(progress)
                    }
                }
            }
            bytesToHex(nativeDigest.digest())
        } else {
            val fallbackEngine = Sha3_512Engine()
            inputStream.use { stream ->
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    fallbackEngine.update(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalBytes > 0 && onProgress != null) {
                        val progress = (totalRead.toFloat() / totalBytes).coerceIn(0f, 1f)
                        onProgress(progress)
                    }
                }
            }
            fallbackEngine.digestHex()
        }
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
        val inStream256 = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open input stream for $uri")

        val hash256Hex = computeSha256(inStream256, sizeBytes) { prog ->
            onProgress?.invoke(prog * 0.33f)
        }

        val inStream512 = context.contentResolver.openInputStream(uri)
        val hash512Hex = if (inStream512 != null) {
            computeSha512(inStream512, sizeBytes) { prog ->
                onProgress?.invoke(0.33f + prog * 0.33f)
            }
        } else ""

        val inStreamSha3 = context.contentResolver.openInputStream(uri)
        val hashSha3Hex = if (inStreamSha3 != null) {
            computeSha3_512(inStreamSha3, sizeBytes) { prog ->
                onProgress?.invoke(0.66f + prog * 0.34f)
            }
        } else ""

        val mimeType = context.contentResolver.getType(uri)

        FileHashResult(
            uri = uri,
            fileName = fileName,
            sizeBytes = sizeBytes,
            sha256Hex = hash256Hex,
            sha512Hex = hash512Hex,
            sha3_512Hex = hashSha3Hex,
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

class Sha3_512Engine {
    private val state = LongArray(25)
    private val buffer = ByteArray(72)
    private var bufferPos = 0

    fun update(data: ByteArray, offset: Int = 0, len: Int = data.size) {
        var pos = offset
        var remaining = len
        while (remaining > 0) {
            val toCopy = minOf(remaining, 72 - bufferPos)
            System.arraycopy(data, pos, buffer, bufferPos, toCopy)
            bufferPos += toCopy
            pos += toCopy
            remaining -= toCopy

            if (bufferPos == 72) {
                absorbBlock(buffer)
                bufferPos = 0
            }
        }
    }

    private fun absorbBlock(block: ByteArray) {
        for (i in 0 until 9) {
            val offset = i * 8
            val w = (block[offset].toLong() and 0xFFL) or
                    ((block[offset + 1].toLong() and 0xFFL) shl 8) or
                    ((block[offset + 2].toLong() and 0xFFL) shl 16) or
                    ((block[offset + 3].toLong() and 0xFFL) shl 24) or
                    ((block[offset + 4].toLong() and 0xFFL) shl 32) or
                    ((block[offset + 5].toLong() and 0xFFL) shl 40) or
                    ((block[offset + 6].toLong() and 0xFFL) shl 48) or
                    ((block[offset + 7].toLong() and 0xFFL) shl 56)
            state[i] = state[i] xor w
        }
        keccakF1600(state)
    }

    fun digestHex(): String {
        buffer[bufferPos] = 0x06
        for (i in (bufferPos + 1) until 72) {
            buffer[i] = 0
        }
        buffer[71] = (buffer[71].toInt() or 0x80).toByte()
        absorbBlock(buffer)

        val out = ByteArray(64)
        for (i in 0 until 8) {
            val w = state[i]
            val offset = i * 8
            out[offset] = (w and 0xFFL).toByte()
            out[offset + 1] = ((w ushr 8) and 0xFFL).toByte()
            out[offset + 2] = ((w ushr 16) and 0xFFL).toByte()
            out[offset + 3] = ((w ushr 24) and 0xFFL).toByte()
            out[offset + 4] = ((w ushr 32) and 0xFFL).toByte()
            out[offset + 5] = ((w ushr 40) and 0xFFL).toByte()
            out[offset + 6] = ((w ushr 48) and 0xFFL).toByte()
            out[offset + 7] = ((w ushr 56) and 0xFFL).toByte()
        }
        return HashingEngine.bytesToHex(out)
    }

    companion object {
        fun digest(data: ByteArray): String {
            val engine = Sha3_512Engine()
            engine.update(data)
            return engine.digestHex()
        }

        private val RC = longArrayOf(
            0x0000000000000001UL.toLong(), 0x0000000000008082UL.toLong(),
            0x800000000000808AUL.toLong(), 0x8000000080008000UL.toLong(),
            0x000000000000808BUL.toLong(), 0x0000000080000001UL.toLong(),
            0x8000000080008081UL.toLong(), 0x8000000000008009UL.toLong(),
            0x000000000000008AUL.toLong(), 0x0000000000000088UL.toLong(),
            0x000000008000801BUL.toLong(), 0x0000000080000009UL.toLong(),
            0x000000008000808BUL.toLong(), 0x800000000000008BUL.toLong(),
            0x8000000000008089UL.toLong(), 0x8000000000008003UL.toLong(),
            0x8000000000008002UL.toLong(), 0x8000000000000080UL.toLong(),
            0x000000000000800AUL.toLong(), 0x800000008000000AUL.toLong(),
            0x8000000080008081UL.toLong(), 0x8000000000008000UL.toLong(),
            0x0000000080000001UL.toLong(), 0x8000000080008008UL.toLong()
        )

        private val RHO = intArrayOf(
            0, 36, 3, 41, 18,
            1, 44, 10, 45, 2,
            62, 6, 43, 15, 61,
            28, 55, 25, 21, 56,
            27, 20, 39, 8, 14
        )

        private fun keccakF1600(state: LongArray) {
            val b = LongArray(25)
            val c = LongArray(5)
            val d = LongArray(5)

            for (round in 0 until 24) {
                for (x in 0 until 5) {
                    c[x] = state[x] xor state[x + 5] xor state[x + 10] xor state[x + 15] xor state[x + 20]
                }
                for (x in 0 until 5) {
                    d[x] = c[(x + 4) % 5] xor c[(x + 1) % 5].rotateLeft(1)
                }
                for (i in 0 until 25) {
                    state[i] = state[i] xor d[i % 5]
                }

                for (x in 0 until 5) {
                    for (y in 0 until 5) {
                        val origIdx = x + 5 * y
                        val newX = y
                        val newY = (2 * x + 3 * y) % 5
                        val newIdx = newX + 5 * newY
                        b[newIdx] = state[origIdx].rotateLeft(RHO[origIdx])
                    }
                }

                for (y in 0 until 5) {
                    for (x in 0 until 5) {
                        val idx = x + 5 * y
                        val idx1 = ((x + 1) % 5) + 5 * y
                        val idx2 = ((x + 2) % 5) + 5 * y
                        state[idx] = b[idx] xor (b[idx1].inv() and b[idx2])
                    }
                }

                state[0] = state[0] xor RC[round]
            }
        }
    }
}

