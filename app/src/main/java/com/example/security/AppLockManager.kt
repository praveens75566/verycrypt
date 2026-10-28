package com.example.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.data.UserPreferencesRepository
import com.example.data.UserSettings
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.security.SecureRandom

object AppLockManager {

    private val secureRandom = SecureRandom()

    fun checkBiometricAvailability(context: Context): Int {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
    }

    fun isBiometricHardwareAvailable(context: Context): Boolean {
        return checkBiometricAvailability(context) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun hashPin(pin: String, saltHex: String): String {
        val salt = HashingEngine.hexToBytes(saltHex)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(pin.toByteArray(Charsets.UTF_8))
        return HashingEngine.bytesToHex(digest.digest())
    }

    fun generateSalt(): String {
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)
        return HashingEngine.bytesToHex(salt)
    }

    suspend fun verifyPin(
        enteredPin: String,
        prefs: UserPreferencesRepository
    ): Boolean {
        val settings = prefs.userSettings.first()
        if (!settings.hasPinConfigured) return true

        // Check if currently locked out
        if (settings.lockoutUntilTimestamp > System.currentTimeMillis()) {
            return false
        }

        val computedHash = hashPin(enteredPin, settings.pinSalt)
        val isValid = computedHash.equals(settings.pinHash, ignoreCase = true)

        if (isValid) {
            prefs.resetFailedAttempts()
        } else {
            prefs.recordFailedAttempt(settings.failedAttempts)
        }
        return isValid
    }

    fun showBiometricPrompt(
        activity: FragmentActivity,
        title: String = "VeriCrypt Authentication",
        subtitle: String = "Unlock with biometric verification",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .build()

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Biometric authentication unrecognized. Please try again.")
                }
            }
        )

        biometricPrompt.authenticate(promptInfo)
    }
}
