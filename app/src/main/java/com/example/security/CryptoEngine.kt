package com.example.security

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class CryptoAuthenticationException(message: String, cause: Throwable? = null) : Exception(message, cause)
class InvalidContainerException(message: String) : Exception(message)

enum class CipherAlgorithm(val displayName: String, val shortName: String, val nonceBytes: Int) {
    AES_256_GCM("AES-256-GCM (12-byte IV)", "AES-GCM", 12),
    XCHACHA20_POLY1305("XChaCha20-Poly1305 (24-byte Nonce)", "XChaCha20", 24)
}

data class EncryptionResult(
    val originalFileName: String,
    val encryptedSizeBytes: Long,
    val saltHex: String,
    val ivHex: String,
    val algorithm: CipherAlgorithm = CipherAlgorithm.AES_256_GCM
)

data class DecryptionResult(
    val restoredFileName: String,
    val decryptedSizeBytes: Long,
    val algorithm: CipherAlgorithm = CipherAlgorithm.AES_256_GCM
)

object CryptoEngine {

    val MAGIC_HEADER = "VCRYPT01".toByteArray(Charsets.US_ASCII)
    val MAGIC_HEADER_XCHACHA = "XCHACHA1".toByteArray(Charsets.US_ASCII)
    private const val KDF_ALGO_PBKDF2_SHA256: Short = 1
    const val DEFAULT_PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private const val IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val BUFFER_SIZE = 64 * 1024

    private val secureRandom = SecureRandom()

    fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int = DEFAULT_PBKDF2_ITERATIONS): SecretKey {
        val spec = PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        val secretKey = SecretKeySpec(keyBytes, "AES")
        // SEC-03: Zero-trace memory hygiene: wipe intermediate key buffer immediately
        java.util.Arrays.fill(keyBytes, 0.toByte())
        return secretKey
    }

    suspend fun encryptStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        originalFileName: String,
        password: CharArray,
        onProgress: ((Float) -> Unit)? = null,
        totalBytes: Long = -1L
    ): EncryptionResult = withContext(Dispatchers.IO) {
        // 1. Generate unique random 16-byte Salt and 12-byte IV
        val salt = ByteArray(SALT_LENGTH_BYTES)
        secureRandom.nextBytes(salt)

        val iv = ByteArray(IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        // 2. Derive AES-256 Key
        val secretKey = deriveKey(password, salt, DEFAULT_PBKDF2_ITERATIONS)

        // 3. Initialize AES-GCM Cipher
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        // 4. Write Header to OutputStream
        val dos = DataOutputStream(outputStream)
        dos.write(MAGIC_HEADER)
        dos.writeShort(KDF_ALGO_PBKDF2_SHA256.toInt())
        dos.writeInt(DEFAULT_PBKDF2_ITERATIONS)
        dos.write(salt)
        dos.write(iv)

        val nameBytes = originalFileName.toByteArray(Charsets.UTF_8)
        dos.writeInt(nameBytes.size)
        dos.write(nameBytes)
        dos.flush()

        // 5. Stream encryption
        var totalEncrypted = (MAGIC_HEADER.size + 2 + 4 + SALT_LENGTH_BYTES + IV_LENGTH_BYTES + 4 + nameBytes.size).toLong()
        var totalRead = 0L
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int

        val cos = CipherOutputStream(outputStream, cipher)
        try {
            inputStream.use { input ->
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    currentCoroutineContext().ensureActive()
                    cos.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalBytes > 0 && onProgress != null) {
                        onProgress((totalRead.toFloat() / totalBytes).coerceIn(0f, 1f))
                    }
                }
            }
            cos.flush()
        } finally {
            // Note: closing cos completes GCM tag computation and flushes to outputStream
            cos.close()
        }

        EncryptionResult(
            originalFileName = originalFileName,
            encryptedSizeBytes = totalEncrypted + totalRead + (GCM_TAG_LENGTH_BITS / 8),
            saltHex = HashingEngine.bytesToHex(salt),
            ivHex = HashingEngine.bytesToHex(iv)
        )
    }

    suspend fun decryptStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        password: CharArray,
        onProgress: ((Float) -> Unit)? = null,
        totalBytes: Long = -1L
    ): DecryptionResult = withContext(Dispatchers.IO) {
        val dis = DataInputStream(inputStream)

        // 1. Validate Magic Header
        val magic = ByteArray(MAGIC_HEADER.size)
        dis.readFully(magic)
        if (!magic.contentEquals(MAGIC_HEADER)) {
            throw InvalidContainerException("Invalid file header: Not an authenticated VeriCrypt (.vcrypt) container.")
        }

        // 2. Read Metadata
        val kdfAlgo = dis.readShort()
        val iterations = dis.readInt()
        if (iterations <= 0 || iterations > 2_000_000) {
            throw InvalidContainerException("Unsupported or invalid KDF parameters.")
        }

        val salt = ByteArray(SALT_LENGTH_BYTES)
        dis.readFully(salt)

        val iv = ByteArray(IV_LENGTH_BYTES)
        dis.readFully(iv)

        val nameLength = dis.readInt()
        if (nameLength <= 0 || nameLength > 4096) {
            throw InvalidContainerException("Invalid metadata: Original file name length is corrupt.")
        }

        val nameBytes = ByteArray(nameLength)
        dis.readFully(nameBytes)
        val originalFileName = String(nameBytes, Charsets.UTF_8)

        // 3. Derive Key
        val secretKey = deriveKey(password, salt, iterations)

        // 4. Initialize AES-GCM Cipher
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

        // 5. Decrypt Stream
        val cis = CipherInputStream(dis, cipher)
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        var totalDecrypted = 0L

        try {
            outputStream.use { out ->
                while (cis.read(buffer).also { bytesRead = it } != -1) {
                    currentCoroutineContext().ensureActive()
                    out.write(buffer, 0, bytesRead)
                    totalDecrypted += bytesRead
                    if (totalBytes > 0 && onProgress != null) {
                        onProgress((totalDecrypted.toFloat() / totalBytes).coerceIn(0f, 1f))
                    }
                }
                out.flush()
            }
        } catch (e: Exception) {
            // Check for AEAD authentication failure (wrong password or altered ciphertext)
            val isAuthFailure = e is AEADBadTagException ||
                    e.cause is AEADBadTagException ||
                    e.message?.contains("tag", ignoreCase = true) == true ||
                    e.message?.contains("mac check in GCM failed", ignoreCase = true) == true

            if (isAuthFailure) {
                throw CryptoAuthenticationException(
                    "Decryption failed: Incorrect password or file ciphertext has been tampered with.",
                    e
                )
            } else {
                throw e
            }
        } finally {
            try {
                cis.close()
            } catch (e: Exception) {
                if (e is AEADBadTagException || e.cause is AEADBadTagException) {
                    throw CryptoAuthenticationException(
                        "Decryption failed: Incorrect password or file ciphertext has been tampered with.",
                        e
                    )
                }
            }
        }

        DecryptionResult(
            restoredFileName = originalFileName,
            decryptedSizeBytes = totalDecrypted
        )
    }

    suspend fun inspectContainerHeader(inputStream: InputStream): Pair<String, Int> = withContext(Dispatchers.IO) {
        val dis = DataInputStream(inputStream)
        val magic = ByteArray(MAGIC_HEADER.size)
        dis.readFully(magic)
        if (!magic.contentEquals(MAGIC_HEADER)) {
            DiagnosticsEngine.recordWarn("CryptoEngine", "Header inspection failed: invalid magic header")
            throw InvalidContainerException("Not a valid VeriCrypt (.vcrypt) container.")
        }
        val kdf = dis.readShort()
        val iterations = dis.readInt()
        if (iterations <= 0 || iterations > 2_000_000) {
            DiagnosticsEngine.recordWarn("CryptoEngine", "Header inspection rejected: abnormal iterations count ($iterations)")
            throw InvalidContainerException("Unsupported or invalid KDF parameters in container header.")
        }
        // Read full salt and IV reliably instead of partial skipBytes
        val saltIvSkip = ByteArray(SALT_LENGTH_BYTES + IV_LENGTH_BYTES)
        dis.readFully(saltIvSkip)
        val nameLength = dis.readInt()
        if (nameLength <= 0 || nameLength > 4096) {
            DiagnosticsEngine.recordWarn("CryptoEngine", "Header inspection rejected: invalid filename length ($nameLength)")
            throw InvalidContainerException("Invalid container metadata: File name length ($nameLength bytes) is invalid.")
        }
        val nameBytes = ByteArray(nameLength)
        dis.readFully(nameBytes)
        Pair(String(nameBytes, Charsets.UTF_8), iterations)
    }

    suspend fun encryptText(
        plainText: String,
        password: String,
        algorithm: CipherAlgorithm = CipherAlgorithm.AES_256_GCM
    ): String = withContext(Dispatchers.IO) {
        if (algorithm == CipherAlgorithm.XCHACHA20_POLY1305) {
            val salt = ByteArray(SALT_LENGTH_BYTES)
            secureRandom.nextBytes(salt)
            val passChars = password.toCharArray()
            val secretKey = deriveKey(passChars, salt, DEFAULT_PBKDF2_ITERATIONS)
            java.util.Arrays.fill(passChars, '\u0000')
            val nonce = XChaCha20Poly1305Engine.generateExtendedNonce()
            val rawKey = secretKey.encoded
            val encResult = try {
                XChaCha20Poly1305Engine.encrypt(
                    plaintext = plainText.toByteArray(Charsets.UTF_8),
                    key = rawKey,
                    nonce = nonce,
                    enforceNonceCheck = false
                )
            } finally {
                java.util.Arrays.fill(rawKey, 0.toByte())
            }

            // Combined payload: Salt (16) + Nonce (24) + Tag (16) + Ciphertext (N)
            val combined = ByteBuffer.allocate(salt.size + nonce.size + encResult.tag.size + encResult.ciphertext.size)
                .put(salt)
                .put(nonce)
                .put(encResult.tag)
                .put(encResult.ciphertext)
                .array()

            val base64 = android.util.Base64.encodeToString(combined, android.util.Base64.DEFAULT)
            buildString {
                append("-----BEGIN XCHACHA20-POLY1305 ENCRYPTED MESSAGE-----\n")
                append("Version: 1.0\n")
                append("Algorithm: XChaCha20-Poly1305 / PBKDF2-HMAC-SHA256\n")
                append("Salt: ${HashingEngine.bytesToHex(salt)}\n")
                append("Nonce: ${HashingEngine.bytesToHex(nonce)}\n")
                append("Tag: ${HashingEngine.bytesToHex(encResult.tag)}\n\n")
                append(base64.trim())
                append("\n-----END XCHACHA20-POLY1305 ENCRYPTED MESSAGE-----\n")
            }
        } else {
            val inputStream = java.io.ByteArrayInputStream(plainText.toByteArray(Charsets.UTF_8))
            val outputStream = ByteArrayOutputStream()

            encryptStream(
                inputStream = inputStream,
                outputStream = outputStream,
                originalFileName = "text_snippet.txt",
                password = password.toCharArray()
            )

            val base64 = android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.DEFAULT)
            buildString {
                append("-----BEGIN VERICRYPT ENCRYPTED MESSAGE-----\n")
                append("Version: 1.0\n")
                append("Algorithm: AES-256-GCM / PBKDF2-HMAC-SHA256\n\n")
                append(base64.trim())
                append("\n-----END VERICRYPT ENCRYPTED MESSAGE-----\n")
            }
        }
    }

    suspend fun decryptText(
        armoredMessage: String,
        password: String
    ): String = withContext(Dispatchers.IO) {
        if (armoredMessage.contains("XCHACHA20-POLY1305")) {
            val beginMarker = "-----BEGIN XCHACHA20-POLY1305 ENCRYPTED MESSAGE-----"
            val endMarker = "-----END XCHACHA20-POLY1305 ENCRYPTED MESSAGE-----"
            var cleanBase64 = armoredMessage.trim()
            if (cleanBase64.contains(beginMarker)) {
                cleanBase64 = cleanBase64.substringAfter(beginMarker).substringBefore(endMarker)
                if (cleanBase64.contains("\n\n")) {
                    cleanBase64 = cleanBase64.substringAfter("\n\n")
                }
            }
            cleanBase64 = cleanBase64.replace("\r", "").replace("\n", "").trim()
            val combined = try {
                android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
            } catch (e: IllegalArgumentException) {
                DiagnosticsEngine.recordError("CryptoEngine", "Base64 decode failed for XChaCha20 payload", e)
                throw InvalidContainerException("Malformed Base64 ciphertext payload: ${e.message}")
            }
            if (combined.size < SALT_LENGTH_BYTES + XChaCha20Poly1305Engine.NONCE_SIZE_BYTES + XChaCha20Poly1305Engine.TAG_SIZE_BYTES) {
                throw InvalidContainerException("Invalid XChaCha20 container: Payload is too small.")
            }

            val salt = combined.copyOfRange(0, SALT_LENGTH_BYTES)
            val nonce = combined.copyOfRange(SALT_LENGTH_BYTES, SALT_LENGTH_BYTES + XChaCha20Poly1305Engine.NONCE_SIZE_BYTES)
            val tag = combined.copyOfRange(
                SALT_LENGTH_BYTES + XChaCha20Poly1305Engine.NONCE_SIZE_BYTES,
                SALT_LENGTH_BYTES + XChaCha20Poly1305Engine.NONCE_SIZE_BYTES + XChaCha20Poly1305Engine.TAG_SIZE_BYTES
            )
            val ciphertext = combined.copyOfRange(
                SALT_LENGTH_BYTES + XChaCha20Poly1305Engine.NONCE_SIZE_BYTES + XChaCha20Poly1305Engine.TAG_SIZE_BYTES,
                combined.size
            )

            val passChars = password.toCharArray()
            val secretKey = deriveKey(passChars, salt, DEFAULT_PBKDF2_ITERATIONS)
            java.util.Arrays.fill(passChars, '\u0000')
            val rawKey = secretKey.encoded
            val decryptedBytes = try {
                XChaCha20Poly1305Engine.decrypt(ciphertext, rawKey, nonce, tag)
            } finally {
                java.util.Arrays.fill(rawKey, 0.toByte())
            }
            String(decryptedBytes, Charsets.UTF_8)
        } else {
            var cleanBase64 = armoredMessage.trim()
            val beginMarker = "-----BEGIN VERICRYPT ENCRYPTED MESSAGE-----"
            val endMarker = "-----END VERICRYPT ENCRYPTED MESSAGE-----"

            if (cleanBase64.contains(beginMarker)) {
                cleanBase64 = cleanBase64.substringAfter(beginMarker).substringBefore(endMarker)
                // Strip any key-value headers before the payload
                if (cleanBase64.contains("\n\n")) {
                    cleanBase64 = cleanBase64.substringAfter("\n\n")
                }
            }

            cleanBase64 = cleanBase64.replace("\r", "").replace("\n", "").trim()
            val containerBytes = try {
                android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
            } catch (e: IllegalArgumentException) {
                DiagnosticsEngine.recordError("CryptoEngine", "Base64 decode failed for AES container payload", e)
                throw InvalidContainerException("Malformed Base64 ciphertext payload: ${e.message}")
            }
            val inputStream = java.io.ByteArrayInputStream(containerBytes)
            val outputStream = ByteArrayOutputStream()

            decryptStream(
                inputStream = inputStream,
                outputStream = outputStream,
                password = password.toCharArray()
            )

            outputStream.toString(Charsets.UTF_8.name())
        }
    }
}
