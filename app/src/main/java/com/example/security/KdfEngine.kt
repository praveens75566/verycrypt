package com.example.security

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class KdfResult(
    val derivedKeyBytes: ByteArray,
    val keyHex: String,
    val keyBase64: String,
    val saltHex: String,
    val iterations: Int,
    val keyLengthBits: Int,
    val executionTimeMs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KdfResult

        if (!derivedKeyBytes.contentEquals(other.derivedKeyBytes)) return false
        if (keyHex != other.keyHex) return false
        if (keyBase64 != other.keyBase64) return false
        if (saltHex != other.saltHex) return false
        if (iterations != other.iterations) return false
        if (keyLengthBits != other.keyLengthBits) return false
        if (executionTimeMs != other.executionTimeMs) return false

        return true
    }

    override fun hashCode(): Int {
        var result = derivedKeyBytes.contentHashCode()
        result = 31 * result + keyHex.hashCode()
        result = 31 * result + keyBase64.hashCode()
        result = 31 * result + saltHex.hashCode()
        result = 31 * result + iterations
        result = 31 * result + keyLengthBits
        result = 31 * result + executionTimeMs.hashCode()
        return result
    }
}

object KdfEngine {

    private val secureRandom = SecureRandom()
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    /**
     * Generates a random cryptographic salt.
     */
    fun generateSalt(lengthBytes: Int = 16): ByteArray {
        val salt = ByteArray(lengthBytes)
        secureRandom.nextBytes(salt)
        return salt
    }

    /**
     * Derives a cryptographic key from a passphrase and salt using PBKDF2WithHmacSHA256.
     * Benchmarks execution time on the local hardware.
     */
    fun deriveKey(
        passphrase: CharArray,
        salt: ByteArray,
        iterations: Int = 100_000,
        keyLengthBits: Int = 256
    ): KdfResult {
        require(passphrase.isNotEmpty()) { "Passphrase cannot be empty" }
        require(salt.isNotEmpty()) { "Salt cannot be empty" }
        require(iterations in 1_000..2_000_000) { "Iterations must be between 1,000 and 2,000,000 to prevent ANR thread starvation" }
        require(keyLengthBits in listOf(128, 192, 256, 384, 512)) { "Unsupported key length: $keyLengthBits" }

        if (iterations > 500_000) {
            DiagnosticsEngine.recordWarn("KdfEngine", "Heavy KDF workload requested: $iterations iterations.")
        }

        val startTime = System.nanoTime()
        val spec = PBEKeySpec(passphrase, salt, iterations, keyLengthBits)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        val derivedBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000

        val hex = derivedBytes.joinToString("") { "%02x".format(it) }
        val base64 = android.util.Base64.encodeToString(derivedBytes, android.util.Base64.NO_WRAP)
        val saltHex = salt.joinToString("") { "%02x".format(it) }

        return KdfResult(
            derivedKeyBytes = derivedBytes,
            keyHex = hex,
            keyBase64 = base64,
            saltHex = saltHex,
            iterations = iterations,
            keyLengthBits = keyLengthBits,
            executionTimeMs = elapsedMs
        )
    }
}
