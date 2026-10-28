package com.example.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class HmacAlgorithm(val standardName: String, val displayName: String, val defaultKeyLengthBytes: Int) {
    HMAC_SHA256("HmacSHA256", "HMAC-SHA256", 32),
    HMAC_SHA512("HmacSHA512", "HMAC-SHA512", 64),
    HMAC_SHA384("HmacSHA384", "HMAC-SHA384", 48)
}

data class HmacResult(
    val algorithm: HmacAlgorithm,
    val macBytes: ByteArray,
    val macHex: String,
    val macBase64: String,
    val keyLengthBytes: Int,
    val dataLengthBytes: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HmacResult

        if (algorithm != other.algorithm) return false
        if (!macBytes.contentEquals(other.macBytes)) return false
        if (macHex != other.macHex) return false
        if (macBase64 != other.macBase64) return false
        if (keyLengthBytes != other.keyLengthBytes) return false
        if (dataLengthBytes != other.dataLengthBytes) return false

        return true
    }

    override fun hashCode(): Int {
        var result = algorithm.hashCode()
        result = 31 * result + macBytes.contentHashCode()
        result = 31 * result + macHex.hashCode()
        result = 31 * result + macBase64.hashCode()
        result = 31 * result + keyLengthBytes
        result = 31 * result + dataLengthBytes
        return result
    }
}

object HmacEngine {

    private val secureRandom = SecureRandom()

    /**
     * Computes an HMAC digest over [dataBytes] using [keyBytes] and [algorithm].
     */
    fun computeHmac(
        algorithm: HmacAlgorithm,
        keyBytes: ByteArray,
        dataBytes: ByteArray
    ): HmacResult {
        require(keyBytes.isNotEmpty()) { "HMAC key cannot be empty" }
        val secretKey = SecretKeySpec(keyBytes, algorithm.standardName)
        val mac = Mac.getInstance(algorithm.standardName)
        mac.init(secretKey)
        val macBytes = mac.doFinal(dataBytes)

        val hex = macBytes.joinToString("") { "%02x".format(it) }
        val base64 = android.util.Base64.encodeToString(macBytes, android.util.Base64.NO_WRAP)

        return HmacResult(
            algorithm = algorithm,
            macBytes = macBytes,
            macHex = hex,
            macBase64 = base64,
            keyLengthBytes = keyBytes.size,
            dataLengthBytes = dataBytes.size
        )
    }

    /**
     * Performs a constant-time comparison of two MAC byte arrays to prevent side-channel timing attacks.
     */
    fun verifyHmac(expectedMac: ByteArray, candidateMac: ByteArray): Boolean {
        return MessageDigest.isEqual(expectedMac, candidateMac)
    }

    /**
     * Performs constant-time verification comparing a hex or base64 candidate string against expected bytes.
     */
    fun verifyHmac(expectedMac: ByteArray, candidateMacHexOrBase64: String): Boolean {
        val cleaned = candidateMacHexOrBase64.trim()
        val expectedHexLength = expectedMac.size * 2
        val candidateBytes = try {
            if (cleaned.length == expectedHexLength && cleaned.matches(Regex("^[0-9a-fA-F]+$"))) {
                cleaned.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            } else {
                android.util.Base64.decode(cleaned, android.util.Base64.DEFAULT)
            }
        } catch (e: Exception) {
            DiagnosticsEngine.recordWarn("HmacEngine", "Failed to parse candidate MAC string: ${e.message}")
            return false
        }
        return verifyHmac(expectedMac, candidateBytes)
    }

    /**
     * Generates a cryptographically secure random key for the given algorithm.
     */
    fun generateRandomKey(algorithm: HmacAlgorithm): ByteArray {
        val key = ByteArray(algorithm.defaultKeyLengthBytes)
        secureRandom.nextBytes(key)
        return key
    }

    /**
     * Formats an HMAC output into standard webhook / API authorization format.
     */
    fun formatAsWebhookSignature(algorithm: HmacAlgorithm, macHex: String): String {
        val prefix = when (algorithm) {
            HmacAlgorithm.HMAC_SHA256 -> "sha256="
            HmacAlgorithm.HMAC_SHA512 -> "sha512="
            HmacAlgorithm.HMAC_SHA384 -> "sha384="
        }
        return "$prefix$macHex"
    }
}
