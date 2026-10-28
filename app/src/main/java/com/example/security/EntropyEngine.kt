package com.example.security

import java.nio.charset.StandardCharsets
import kotlin.math.ln

enum class EntropyLevel {
    LOW,
    MEDIUM,
    HIGH
}

enum class EntropyAssessment(
    val label: String,
    val description: String,
    val level: EntropyLevel
) {
    VERY_LOW(
        "Negligible Entropy",
        "Extremely predictable or repetitive data with virtually zero randomness.",
        EntropyLevel.LOW
    ),
    LOW(
        "Low Entropy",
        "Typical human-readable plain text, ASCII source code, or uncompressed structured data.",
        EntropyLevel.LOW
    ),
    MEDIUM(
        "Moderate Entropy",
        "Structured binaries, formatted documents, or partially encoded data.",
        EntropyLevel.MEDIUM
    ),
    HIGH(
        "High Entropy (Cryptographic Grade)",
        "Near-ideal randomness typical of AES ciphertext, strong cryptographic keys, or modern compression.",
        EntropyLevel.HIGH
    )
}

data class EntropyResult(
    val entropyBitsPerByte: Double, // 0.0 to 8.0
    val maxEntropy: Double = 8.0,
    val percentage: Float, // 0.0 to 100.0
    val assessment: EntropyAssessment,
    val byteFrequencies: IntArray, // Frequency of each byte 0..255
    val uniqueByteCount: Int,
    val totalBytes: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EntropyResult

        if (entropyBitsPerByte != other.entropyBitsPerByte) return false
        if (maxEntropy != other.maxEntropy) return false
        if (percentage != other.percentage) return false
        if (assessment != other.assessment) return false
        if (!byteFrequencies.contentEquals(other.byteFrequencies)) return false
        if (uniqueByteCount != other.uniqueByteCount) return false
        if (totalBytes != other.totalBytes) return false

        return true
    }

    override fun hashCode(): Int {
        var result = entropyBitsPerByte.hashCode()
        result = 31 * result + maxEntropy.hashCode()
        result = 31 * result + percentage.hashCode()
        result = 31 * result + assessment.hashCode()
        result = 31 * result + byteFrequencies.contentHashCode()
        result = 31 * result + uniqueByteCount
        result = 31 * result + totalBytes
        return result
    }
}

object EntropyEngine {

    private val LOG_2 = ln(2.0)

    /**
     * Calculates Shannon entropy: H = - sum(p_i * log2(p_i)) over byte distribution.
     * Theoretical range is [0.0, 8.0] bits per byte.
     */
    fun calculateEntropy(bytes: ByteArray): EntropyResult {
        val totalBytes = bytes.size
        if (totalBytes == 0) {
            return EntropyResult(
                entropyBitsPerByte = 0.0,
                percentage = 0.0f,
                assessment = EntropyAssessment.VERY_LOW,
                byteFrequencies = IntArray(256),
                uniqueByteCount = 0,
                totalBytes = 0
            )
        }

        val frequencies = IntArray(256)
        for (b in bytes) {
            val unsignedIndex = b.toInt() and 0xFF
            frequencies[unsignedIndex]++
        }

        return calculateFromFrequencies(frequencies, totalBytes)
    }

    /**
     * Calculates Shannon entropy from pre-accumulated byte frequencies.
     * Ideal for memory-bounded streaming of large files.
     */
    fun calculateFromFrequencies(frequencies: IntArray, totalBytes: Int): EntropyResult {
        if (totalBytes <= 0) {
            return EntropyResult(
                entropyBitsPerByte = 0.0,
                percentage = 0.0f,
                assessment = EntropyAssessment.VERY_LOW,
                byteFrequencies = frequencies,
                uniqueByteCount = 0,
                totalBytes = 0
            )
        }

        var uniqueCount = 0
        var entropySum = 0.0
        val totalDouble = totalBytes.toDouble()

        for (count in frequencies) {
            if (count > 0) {
                uniqueCount++
                val p = count.toDouble() / totalDouble
                val log2P = ln(p) / LOG_2
                entropySum -= p * log2P
            }
        }

        val clampedEntropy = entropySum.coerceIn(0.0, 8.0)
        val percentage = ((clampedEntropy / 8.0) * 100.0).toFloat().coerceIn(0f, 100f)

        val assessment = when {
            clampedEntropy < 1.0 -> EntropyAssessment.VERY_LOW
            clampedEntropy < 4.8 -> EntropyAssessment.LOW
            clampedEntropy < 7.2 -> EntropyAssessment.MEDIUM
            else -> EntropyAssessment.HIGH
        }

        return EntropyResult(
            entropyBitsPerByte = clampedEntropy,
            percentage = percentage,
            assessment = assessment,
            byteFrequencies = frequencies,
            uniqueByteCount = uniqueCount,
            totalBytes = totalBytes
        )
    }

    /**
     * Calculates Shannon entropy for a UTF-8 text string.
     */
    fun calculateTextEntropy(text: String): EntropyResult {
        return calculateEntropy(text.toByteArray(StandardCharsets.UTF_8))
    }

    /**
     * Formats 256 byte frequencies into 16 aggregated buckets for UI histogram rendering.
     */
    fun aggregateHistogramBuckets(frequencies: IntArray, bucketCount: Int = 16): FloatArray {
        require(bucketCount in 4..64) { "Bucket count must be between 4 and 64" }
        val result = FloatArray(bucketCount)
        val bucketSize = 256 / bucketCount

        var maxBucketSum = 0
        val rawSums = IntArray(bucketCount)

        for (i in 0 until bucketCount) {
            var sum = 0
            for (j in 0 until bucketSize) {
                sum += frequencies[i * bucketSize + j]
            }
            rawSums[i] = sum
            if (sum > maxBucketSum) {
                maxBucketSum = sum
            }
        }

        if (maxBucketSum > 0) {
            for (i in 0 until bucketCount) {
                result[i] = rawSums[i].toFloat() / maxBucketSum.toFloat()
            }
        }

        return result
    }
}
