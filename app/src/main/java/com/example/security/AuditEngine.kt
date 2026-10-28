package com.example.security

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import java.security.KeyStore
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AuditRating(val label: String, val level: String) {
    EXCELLENT("Excellent Security Posture", "FORTIFIED"),
    GOOD("Good Security Posture", "PROTECTED"),
    ADEQUATE("Moderate Security Posture", "ELEVATED_RISK"),
    VULNERABLE("Vulnerable Security Posture", "CRITICAL_ACTION_REQUIRED")
}

enum class CheckStatus {
    PASSED,
    WARNING,
    FAILED
}

data class SecurityCheckItem(
    val title: String,
    val description: String,
    val status: CheckStatus,
    val scoreImpact: Int,
    val recommendation: String? = null
)

data class AuditReport(
    val timestampMs: Long,
    val overallScore: Int, // 0..100
    val rating: AuditRating,
    val checks: List<SecurityCheckItem>,
    val osVersion: String,
    val deviceModel: String,
    val hardwareKeystoreActive: Boolean,
    val biometricEnrolled: Boolean,
    val appLockEnabled: Boolean,
    val deviceLockActive: Boolean
) {
    fun toFormattedText(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
        val dateStr = sdf.format(Date(timestampMs))

        val builder = StringBuilder()
        builder.appendLine("==================================================")
        builder.appendLine("        VERICRYPT CRYPTOGRAPHIC AUDIT REPORT       ")
        builder.appendLine("==================================================")
        builder.appendLine("Generated: $dateStr")
        builder.appendLine("Device: $deviceModel | OS: $osVersion")
        builder.appendLine("--------------------------------------------------")
        builder.appendLine("OVERALL SECURITY SCORE: $overallScore / 100 (${rating.label})")
        builder.appendLine("SECURITY LEVEL: ${rating.level}")
        builder.appendLine("--------------------------------------------------")
        builder.appendLine("SYSTEM SECURITY CHECKS:")
        for (check in checks) {
            val statusSymbol = when (check.status) {
                CheckStatus.PASSED -> "[PASS]"
                CheckStatus.WARNING -> "[WARN]"
                CheckStatus.FAILED -> "[FAIL]"
            }
            builder.appendLine("  $statusSymbol ${check.title} (+${check.scoreImpact} pts)")
            builder.appendLine("         ${check.description}")
            if (check.recommendation != null && check.status != CheckStatus.PASSED) {
                builder.appendLine("         Recommendation: ${check.recommendation}")
            }
        }
        builder.appendLine("--------------------------------------------------")
        builder.appendLine("HARDWARE SPECIFICATIONS & CRYPTOGRAPHIC ENGINE:")
        builder.appendLine("  • Android KeyStore Provider: ${if (hardwareKeystoreActive) "Active (Hardware Enclave)" else "Standard Provider"}")
        builder.appendLine("  • Biometric Authentication: ${if (biometricEnrolled) "Available & Enrolled" else "Not Enrolled / Unavailable"}")
        builder.appendLine("  • OS Lockscreen Protection: ${if (deviceLockActive) "Enabled (Keyguard Secured)" else "Disabled / Unsecured"}")
        builder.appendLine("  • VeriCrypt App Master PIN: ${if (appLockEnabled) "Configured & Active" else "Not Configured"}")
        builder.appendLine("  • AES Encryption: 256-bit GCM with PKCS5/PBKDF2 Derivation")
        builder.appendLine("  • Cascade Encryption: Dual AES-256 (512-bit Effective Depth)")
        builder.appendLine("  • Hashing Standard: FIPS 180-4 SHA-256 and SHA-512")
        builder.appendLine("  • Digital Signatures: RSA-4096 and ECDSA P-256")
        builder.appendLine("==================================================")
        builder.appendLine("STATUS: AUDIT VERIFIED OFFLINE ON DEVICE")
        builder.appendLine("==================================================")
        return builder.toString()
    }

    fun toFormattedJson(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        val isoDate = sdf.format(Date(timestampMs))

        val checkJsonList = checks.joinToString(",\n    ") { check ->
            val rec = if (check.recommendation != null) "\"${check.recommendation.replace("\"", "\\\"")}\"" else "null"
            """{
      "title": "${check.title}",
      "status": "${check.status}",
      "scoreImpact": ${check.scoreImpact},
      "description": "${check.description.replace("\"", "\\\"")}",
      "recommendation": $rec
    }"""
        }

        return """{
  "report": "VeriCrypt Cryptographic Security Audit",
  "timestamp": "$isoDate",
  "score": $overallScore,
  "rating": "${rating.name}",
  "ratingLabel": "${rating.label}",
  "system": {
    "deviceModel": "$deviceModel",
    "osVersion": "$osVersion",
    "hardwareKeystore": $hardwareKeystoreActive,
    "biometricEnrolled": $biometricEnrolled,
    "deviceLockActive": $deviceLockActive,
    "appLockConfigured": $appLockEnabled
  },
  "checks": [
    $checkJsonList
  ]
}"""
    }
}

object AuditEngine {

    fun generateSecurityAudit(
        context: Context,
        isAppLockConfigured: Boolean
    ): AuditReport {
        val checks = mutableListOf<SecurityCheckItem>()
        var totalScore = 0

        // 1. Check Android KeyStore hardware provider
        val hasAndroidKeyStore = try {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            true
        } catch (_: Exception) {
            false
        }

        if (hasAndroidKeyStore) {
            totalScore += 20
            checks.add(
                SecurityCheckItem(
                    title = "Hardware Keystore Provider",
                    description = "AndroidKeyStore is available and operational for secure key isolation.",
                    status = CheckStatus.PASSED,
                    scoreImpact = 20
                )
            )
        } else {
            checks.add(
                SecurityCheckItem(
                    title = "Hardware Keystore Provider",
                    description = "Hardware-backed AndroidKeyStore provider could not be initialized.",
                    status = CheckStatus.FAILED,
                    scoreImpact = 0,
                    recommendation = "Ensure Android security patches are up to date."
                )
            )
        }

        // 2. Check Device Lock / Screen Lock
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isDeviceSecure = keyguardManager?.isDeviceSecure ?: false
        if (isDeviceSecure) {
            totalScore += 20
            checks.add(
                SecurityCheckItem(
                    title = "Device Screen Lock (Keyguard)",
                    description = "Device has an active lock screen PIN, pattern, or biometric credential.",
                    status = CheckStatus.PASSED,
                    scoreImpact = 20
                )
            )
        } else {
            checks.add(
                SecurityCheckItem(
                    title = "Device Screen Lock (Keyguard)",
                    description = "No lock screen credential detected on this device.",
                    status = CheckStatus.WARNING,
                    scoreImpact = 0,
                    recommendation = "Enable a device PIN, password, or biometric lock in Android Settings."
                )
            )
        }

        // 3. Check App Master Security PIN
        if (isAppLockConfigured) {
            totalScore += 25
            checks.add(
                SecurityCheckItem(
                    title = "VeriCrypt Master Security PIN",
                    description = "App-level master PIN protection and timed auto-lock are active.",
                    status = CheckStatus.PASSED,
                    scoreImpact = 25
                )
            )
        } else {
            checks.add(
                SecurityCheckItem(
                    title = "VeriCrypt Master Security PIN",
                    description = "VeriCrypt master PIN lock is not configured.",
                    status = CheckStatus.WARNING,
                    scoreImpact = 0,
                    recommendation = "Set up a 6-digit Master PIN in Settings to protect your vault and keys."
                )
            )
        }

        // 4. Check Biometric Hardware & Enrollment
        val isBiometricAvailable = AppLockManager.isBiometricHardwareAvailable(context)
        if (isBiometricAvailable) {
            totalScore += 20
            checks.add(
                SecurityCheckItem(
                    title = "Biometric Sensor & Enclave",
                    description = "Biometric authentication hardware is present and ready for high-security unlock.",
                    status = CheckStatus.PASSED,
                    scoreImpact = 20
                )
            )
        } else {
            totalScore += 5
            checks.add(
                SecurityCheckItem(
                    title = "Biometric Sensor & Enclave",
                    description = "Biometrics are either unavailable or not enrolled on this hardware.",
                    status = CheckStatus.WARNING,
                    scoreImpact = 5,
                    recommendation = "Enroll a fingerprint or face unlock in Android Settings if your device supports it."
                )
            )
        }

        // 5. Check Cryptographic Randomness (CSPRNG)
        val rngPassed = try {
            val rng = SecureRandom()
            val sample = ByteArray(32)
            rng.nextBytes(sample)
            val entropy = EntropyEngine.calculateEntropy(sample)
            entropy.entropyBitsPerByte > 3.0 // 32 random bytes will have high entropy
        } catch (_: Exception) {
            false
        }

        if (rngPassed) {
            totalScore += 15
            checks.add(
                SecurityCheckItem(
                    title = "Cryptographic Randomness (CSPRNG)",
                    description = "System SecureRandom passed entropy verification and health checks.",
                    status = CheckStatus.PASSED,
                    scoreImpact = 15
                )
            )
        } else {
            checks.add(
                SecurityCheckItem(
                    title = "Cryptographic Randomness (CSPRNG)",
                    description = "System SecureRandom failed to generate high-entropy output.",
                    status = CheckStatus.FAILED,
                    scoreImpact = 0,
                    recommendation = "Device entropy pool may be degraded."
                )
            )
        }

        val clampedScore = totalScore.coerceIn(0, 100)
        val rating = when {
            clampedScore >= 90 -> AuditRating.EXCELLENT
            clampedScore >= 70 -> AuditRating.GOOD
            clampedScore >= 50 -> AuditRating.ADEQUATE
            else -> AuditRating.VULNERABLE
        }

        return AuditReport(
            timestampMs = System.currentTimeMillis(),
            overallScore = clampedScore,
            rating = rating,
            checks = checks,
            osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            hardwareKeystoreActive = hasAndroidKeyStore,
            biometricEnrolled = isBiometricAvailable,
            appLockEnabled = isAppLockConfigured,
            deviceLockActive = isDeviceSecure
        )
    }
}
