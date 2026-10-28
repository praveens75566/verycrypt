package com.example.security

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

enum class DiagnosticLevel {
    INFO,
    WARN,
    ERROR
}

data class DiagnosticEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: DiagnosticLevel,
    val subsystem: String,
    val message: String,
    val stackTracePreview: String? = null
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
}

data class SelfTestResult(
    val primitiveName: String,
    val isSuccess: Boolean,
    val executionTimeMs: Long,
    val details: String
)

data class DiagnosticsReport(
    val isAllPassed: Boolean,
    val totalTimeMs: Long,
    val results: List<SelfTestResult>,
    val freeMemoryMb: Long,
    val allocatedMemoryMb: Long,
    val maxMemoryMb: Long
)

object DiagnosticsEngine {

    private const val MAX_LOG_CAPACITY = 60
    private val logBuffer = ConcurrentLinkedDeque<DiagnosticEntry>()

    fun recordInfo(subsystem: String, message: String) {
        appendEntry(DiagnosticEntry(level = DiagnosticLevel.INFO, subsystem = subsystem, message = message))
    }

    fun recordWarn(subsystem: String, message: String) {
        appendEntry(DiagnosticEntry(level = DiagnosticLevel.WARN, subsystem = subsystem, message = message))
    }

    fun recordError(subsystem: String, message: String, throwable: Throwable? = null) {
        val stackTrace = throwable?.let {
            val sw = StringWriter()
            it.printStackTrace(PrintWriter(sw))
            sw.toString().take(600)
        }
        appendEntry(
            DiagnosticEntry(
                level = DiagnosticLevel.ERROR,
                subsystem = subsystem,
                message = message,
                stackTracePreview = stackTrace
            )
        )
    }

    private fun appendEntry(entry: DiagnosticEntry) {
        logBuffer.addLast(entry)
        while (logBuffer.size > MAX_LOG_CAPACITY) {
            logBuffer.pollFirst()
        }
    }

    fun getLogs(): List<DiagnosticEntry> {
        return logBuffer.toList().reversed()
    }

    fun clearLogs() {
        logBuffer.clear()
        recordInfo("DiagnosticsEngine", "Diagnostic event logs cleared by user.")
    }

    fun getMemoryHealth(): Triple<Long, Long, Long> {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        val totalMemory = runtime.totalMemory() / (1024 * 1024)
        val freeMemory = runtime.freeMemory() / (1024 * 1024)
        return Triple(freeMemory, totalMemory, maxMemory)
    }

    suspend fun runSelfTest(): DiagnosticsReport = withContext(Dispatchers.Default) {
        val results = mutableListOf<SelfTestResult>()
        val startTime = System.currentTimeMillis()

        // 1. AES-256-GCM Self-Test
        val aesStart = System.currentTimeMillis()
        try {
            val testPlaintext = "VeriCrypt_Self_Test_Payload_${System.currentTimeMillis()}"
            val testPassword = "SelfTestStrongPassword!#99"
            val armored = CryptoEngine.encryptText(testPlaintext, testPassword, CipherAlgorithm.AES_256_GCM)
            val decrypted = CryptoEngine.decryptText(armored, testPassword)
            val success = decrypted == testPlaintext
            results.add(
                SelfTestResult(
                    primitiveName = "AES-256-GCM Authenticated Encryption",
                    isSuccess = success,
                    executionTimeMs = System.currentTimeMillis() - aesStart,
                    details = if (success) "Round-trip encryption/decryption verified." else "Plaintext mismatch after decryption."
                )
            )
        } catch (e: Exception) {
            results.add(
                SelfTestResult(
                    primitiveName = "AES-256-GCM Authenticated Encryption",
                    isSuccess = false,
                    executionTimeMs = System.currentTimeMillis() - aesStart,
                    details = "Failure: ${e.message}"
                )
            )
        }

        // 2. XChaCha20-Poly1305 Self-Test
        val xStart = System.currentTimeMillis()
        try {
            val testPlaintext = "XChaCha20_Poly1305_Test_Payload"
            val testPassword = "XChaChaPassword!#42"
            val armored = CryptoEngine.encryptText(testPlaintext, testPassword, CipherAlgorithm.XCHACHA20_POLY1305)
            val decrypted = CryptoEngine.decryptText(armored, testPassword)
            val success = decrypted == testPlaintext
            results.add(
                SelfTestResult(
                    primitiveName = "XChaCha20-Poly1305 Extended Nonce AEAD",
                    isSuccess = success,
                    executionTimeMs = System.currentTimeMillis() - xStart,
                    details = if (success) "RFC 8439 extended nonce AEAD verified." else "Decryption mismatch."
                )
            )
        } catch (e: Exception) {
            results.add(
                SelfTestResult(
                    primitiveName = "XChaCha20-Poly1305 Extended Nonce AEAD",
                    isSuccess = false,
                    executionTimeMs = System.currentTimeMillis() - xStart,
                    details = "Failure: ${e.message}"
                )
            )
        }

        // 3. PBKDF2-HMAC-SHA256 KDF Self-Test
        val kdfStart = System.currentTimeMillis()
        try {
            val salt = ByteArray(16) { 0x05 }
            val key = CryptoEngine.deriveKey("KdfVerificationPassword".toCharArray(), salt, 20_000)
            val success = key.encoded.size == 32
            results.add(
                SelfTestResult(
                    primitiveName = "PBKDF2-HMAC-SHA256 (20,000 rounds)",
                    isSuccess = success,
                    executionTimeMs = System.currentTimeMillis() - kdfStart,
                    details = if (success) "256-bit symmetric key derived cleanly." else "Unexpected key length."
                )
            )
        } catch (e: Exception) {
            results.add(
                SelfTestResult(
                    primitiveName = "PBKDF2-HMAC-SHA256",
                    isSuccess = false,
                    executionTimeMs = System.currentTimeMillis() - kdfStart,
                    details = "Failure: ${e.message}"
                )
            )
        }

        // 4. ECDSA P-256 Digital Signature Self-Test
        val signStart = System.currentTimeMillis()
        try {
            val keyPair = SignatureEngine.generateSoftwareKeyPair()
            val sampleData = "Cryptographic integrity payload verification".toByteArray(Charsets.UTF_8)
            val sigResult = SignatureEngine.signStream(
                ByteArrayInputStream(sampleData),
                keyPair.private,
                "selftest_ec"
            )
            val verResult = SignatureEngine.verifyStream(
                ByteArrayInputStream(sampleData),
                sigResult.signatureBytes,
                keyPair.public
            )
            results.add(
                SelfTestResult(
                    primitiveName = "ECDSA P-256 Digital Signatures",
                    isSuccess = verResult.isValid,
                    executionTimeMs = System.currentTimeMillis() - signStart,
                    details = if (verResult.isValid) "secp256r1 ECDSA signature sign & verify passed." else "Signature failed validation."
                )
            )
        } catch (e: Exception) {
            results.add(
                SelfTestResult(
                    primitiveName = "ECDSA P-256 Digital Signatures",
                    isSuccess = false,
                    executionTimeMs = System.currentTimeMillis() - signStart,
                    details = "Failure: ${e.message}"
                )
            )
        }

        // 5. SHA-256 Hashing Self-Test
        val hashStart = System.currentTimeMillis()
        try {
            val hash = HashingEngine.computeSha256Bytes("VeriCrypt".toByteArray(Charsets.UTF_8))
            val success = hash.isNotBlank() && hash.length == 64
            results.add(
                SelfTestResult(
                    primitiveName = "SHA-256 FIPS 180-4 Cryptographic Hash",
                    isSuccess = success,
                    executionTimeMs = System.currentTimeMillis() - hashStart,
                    details = if (success) "64-character hex digest validated." else "Unexpected digest length."
                )
            )
        } catch (e: Exception) {
            results.add(
                SelfTestResult(
                    primitiveName = "SHA-256 Hash",
                    isSuccess = false,
                    executionTimeMs = System.currentTimeMillis() - hashStart,
                    details = "Failure: ${e.message}"
                )
            )
        }

        val totalTime = System.currentTimeMillis() - startTime
        val allPassed = results.all { it.isSuccess }
        val (freeMb, totalMb, maxMb) = getMemoryHealth()

        recordInfo("SelfTest", "Cryptographic suite self-test completed in ${totalTime}ms (All Passed: $allPassed).")

        DiagnosticsReport(
            isAllPassed = allPassed,
            totalTimeMs = totalTime,
            results = results,
            freeMemoryMb = freeMb,
            allocatedMemoryMb = totalMb,
            maxMemoryMb = maxMb
        )
    }

    fun exportDiagnosticsSummary(): String {
        val (freeMb, totalMb, maxMb) = getMemoryHealth()
        val logs = getLogs()
        return buildString {
            append("=== VERICRYPT SYSTEM DIAGNOSTICS REPORT ===\n")
            append("Generated At: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
            append("Memory: Free ${freeMb}MB / Total ${totalMb}MB / Max ${maxMb}MB\n")
            append("Active Log Entries (${logs.size}):\n\n")
            logs.forEach { entry ->
                append("[${entry.formattedTime}] [${entry.level}] [${entry.subsystem}] ${entry.message}\n")
                if (entry.stackTracePreview != null) {
                    append("  StackTrace: ${entry.stackTracePreview.replace("\n", "\n  ")}\n")
                }
            }
            append("=== END REPORT ===\n")
        }
    }
}
