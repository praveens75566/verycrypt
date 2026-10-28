package com.example.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import kotlin.system.measureNanoTime

data class AlgorithmBenchmark(
    val name: String,
    val category: String,
    val throughputMBps: Double,
    val latencyMs: Double,
    val iterations: Int,
    val processedBytes: Long
)

data class BenchmarkSuiteResult(
    val results: List<AlgorithmBenchmark>,
    val totalTimeMs: Long,
    val hardwareRating: String
)

object BenchmarkEngine {

    suspend fun runBenchmark(
        iterations: Int = 10,
        payloadSizeKb: Int = 256
    ): BenchmarkSuiteResult = withContext(Dispatchers.Default) {
        val payloadSizeBytes = payloadSizeKb * 1024
        val random = SecureRandom()
        val testData = ByteArray(payloadSizeBytes)
        random.nextBytes(testData)

        val results = mutableListOf<AlgorithmBenchmark>()
        val startSuiteTime = System.currentTimeMillis()

        // 1. SHA-256 Benchmark
        val sha256Nanos = measureNanoTime {
            repeat(iterations) {
                HashingEngine.computeSha256Bytes(testData)
            }
        }
        val sha256TotalBytes = payloadSizeBytes.toLong() * iterations
        val sha256Seconds = sha256Nanos / 1_000_000_000.0
        val sha256MBps = if (sha256Seconds > 0) (sha256TotalBytes / (1024.0 * 1024.0)) / sha256Seconds else 0.0
        results.add(
            AlgorithmBenchmark(
                name = "SHA-256 Digest",
                category = "Hashing",
                throughputMBps = sha256MBps,
                latencyMs = (sha256Nanos / 1_000_000.0) / iterations,
                iterations = iterations,
                processedBytes = sha256TotalBytes
            )
        )

        // 2. SHA-512 Benchmark
        val sha512Nanos = measureNanoTime {
            repeat(iterations) {
                HashingEngine.computeSha512Bytes(testData)
            }
        }
        val sha512TotalBytes = payloadSizeBytes.toLong() * iterations
        val sha512Seconds = sha512Nanos / 1_000_000_000.0
        val sha512MBps = if (sha512Seconds > 0) (sha512TotalBytes / (1024.0 * 1024.0)) / sha512Seconds else 0.0
        results.add(
            AlgorithmBenchmark(
                name = "SHA-512 Digest",
                category = "Hashing",
                throughputMBps = sha512MBps,
                latencyMs = (sha512Nanos / 1_000_000.0) / iterations,
                iterations = iterations,
                processedBytes = sha512TotalBytes
            )
        )

        // 3. AES-256-GCM Hardware Cipher Benchmark
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val aesKey = keyGen.generateKey()
        val iv = ByteArray(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")

        val aesNanos = measureNanoTime {
            repeat(iterations) {
                random.nextBytes(iv)
                cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
                cipher.doFinal(testData)
            }
        }
        val aesTotalBytes = payloadSizeBytes.toLong() * iterations
        val aesSeconds = aesNanos / 1_000_000_000.0
        val aesMBps = if (aesSeconds > 0) (aesTotalBytes / (1024.0 * 1024.0)) / aesSeconds else 0.0
        results.add(
            AlgorithmBenchmark(
                name = "AES-256-GCM Encrypt",
                category = "Symmetric Cipher",
                throughputMBps = aesMBps,
                latencyMs = (aesNanos / 1_000_000.0) / iterations,
                iterations = iterations,
                processedBytes = aesTotalBytes
            )
        )

        // 4. Cascade 512-bit (Dual AES) Benchmark
        val cascadeKeyGen = KeyGenerator.getInstance("AES")
        cascadeKeyGen.init(256)
        val key2 = cascadeKeyGen.generateKey()
        val iv2 = ByteArray(12)
        val cipher2 = Cipher.getInstance("AES/GCM/NoPadding")

        val cascadeNanos = measureNanoTime {
            repeat(iterations) {
                random.nextBytes(iv)
                random.nextBytes(iv2)
                cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
                val pass1 = cipher.doFinal(testData)
                cipher2.init(Cipher.ENCRYPT_MODE, key2, GCMParameterSpec(128, iv2))
                cipher2.doFinal(pass1)
            }
        }
        val cascadeTotalBytes = payloadSizeBytes.toLong() * iterations
        val cascadeSeconds = cascadeNanos / 1_000_000_000.0
        val cascadeMBps = if (cascadeSeconds > 0) (cascadeTotalBytes / (1024.0 * 1024.0)) / cascadeSeconds else 0.0
        results.add(
            AlgorithmBenchmark(
                name = "Cascade 512-bit Cipher",
                category = "Multi-Layer Symmetric",
                throughputMBps = cascadeMBps,
                latencyMs = (cascadeNanos / 1_000_000.0) / iterations,
                iterations = iterations,
                processedBytes = cascadeTotalBytes
            )
        )

        // 5. XChaCha20-Poly1305 AEAD Benchmark (192-bit Nonce + 128-bit MAC)
        val xChaChaKey = XChaCha20Poly1305Engine.generateSecureKey()
        val xChaChaNonce = XChaCha20Poly1305Engine.generateExtendedNonce()
        // Run on 64KB slice if large to keep benchmark swift
        val xTestSlice = if (testData.size > 64 * 1024) testData.copyOfRange(0, 64 * 1024) else testData
        val xChaChaNanos = measureNanoTime {
            repeat(iterations) {
                XChaCha20Poly1305Engine.encrypt(
                    plaintext = xTestSlice,
                    key = xChaChaKey,
                    nonce = xChaChaNonce,
                    enforceNonceCheck = false
                )
            }
        }
        val xChaChaTotalBytes = xTestSlice.size.toLong() * iterations
        val xChaChaSeconds = xChaChaNanos / 1_000_000_000.0
        val xChaChaMBps = if (xChaChaSeconds > 0) (xChaChaTotalBytes / (1024.0 * 1024.0)) / xChaChaSeconds else 0.0
        results.add(
            AlgorithmBenchmark(
                name = "XChaCha20-Poly1305 (24-byte Nonce)",
                category = "AEAD Stream Cipher",
                throughputMBps = xChaChaMBps,
                latencyMs = (xChaChaNanos / 1_000_000.0) / iterations,
                iterations = iterations,
                processedBytes = xChaChaTotalBytes
            )
        )

        val totalTime = System.currentTimeMillis() - startSuiteTime
        val avgMBps = results.map { it.throughputMBps }.average()
        val hardwareRating = when {
            avgMBps > 200.0 -> "Hardware Enclave Ultra-Fast"
            avgMBps > 75.0 -> "ARMv8 Crypto Hardware Accelerated"
            avgMBps > 25.0 -> "Standard High-Efficiency"
            else -> "Software Fallback Mode"
        }

        BenchmarkSuiteResult(
            results = results,
            totalTimeMs = totalTime,
            hardwareRating = hardwareRating
        )
    }
}
