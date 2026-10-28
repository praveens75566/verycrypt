package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vericrypt_preferences")

enum class ThemeMode {
    SYSTEM, DARK, OLED, LIGHT
}

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isAppLockEnabled: Boolean = false,
    val isBiometricsEnabled: Boolean = true,
    val autoLockTimeoutSeconds: Int = 30,
    val hasPinConfigured: Boolean = false,
    val pinSalt: String = "",
    val pinHash: String = "",
    val failedAttempts: Int = 0,
    val lockoutUntilTimestamp: Long = 0L,
    val isScreenProtectionEnabled: Boolean = false,
    val isSimpleUiMode: Boolean = true,
    val clipboardTimeoutSeconds: Int = 30,
    val defaultCipher: String = "AES_256_GCM",
    val shreddingPasses: Int = 3,
    val hapticFeedbackEnabled: Boolean = true,
    val readerFontSizeSp: Int = 15,
    val highContrastText: Boolean = false,
    val textLineWrapEnabled: Boolean = true
)

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val BIOMETRICS_ENABLED = booleanPreferencesKey("biometrics_enabled")
        val AUTO_LOCK_TIMEOUT = intPreferencesKey("auto_lock_timeout")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val FAILED_ATTEMPTS = intPreferencesKey("failed_attempts")
        val LOCKOUT_UNTIL = longPreferencesKey("lockout_until")
        val SCREEN_PROTECTION_ENABLED = booleanPreferencesKey("screen_protection_enabled")
        val SIMPLE_UI_MODE = booleanPreferencesKey("simple_ui_mode")
        val CLIPBOARD_TIMEOUT = intPreferencesKey("clipboard_timeout")
        val DEFAULT_CIPHER = stringPreferencesKey("default_cipher")
        val SHREDDING_PASSES = intPreferencesKey("shredding_passes")
        val HAPTIC_FEEDBACK_ENABLED = booleanPreferencesKey("haptic_feedback_enabled")
        val READER_FONT_SIZE = intPreferencesKey("reader_font_size")
        val HIGH_CONTRAST_TEXT = booleanPreferencesKey("high_contrast_text")
        val TEXT_LINE_WRAP_ENABLED = booleanPreferencesKey("text_line_wrap_enabled")
    }

    val userSettings: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        val themeString = preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val themeMode = try {
            ThemeMode.valueOf(themeString)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }

        val pinHash = preferences[PreferencesKeys.PIN_HASH] ?: ""
        val pinSalt = preferences[PreferencesKeys.PIN_SALT] ?: ""

        UserSettings(
            themeMode = themeMode,
            isAppLockEnabled = preferences[PreferencesKeys.APP_LOCK_ENABLED] ?: false,
            isBiometricsEnabled = preferences[PreferencesKeys.BIOMETRICS_ENABLED] ?: true,
            autoLockTimeoutSeconds = preferences[PreferencesKeys.AUTO_LOCK_TIMEOUT] ?: 30,
            hasPinConfigured = pinHash.isNotBlank() && pinSalt.isNotBlank(),
            pinSalt = pinSalt,
            pinHash = pinHash,
            failedAttempts = preferences[PreferencesKeys.FAILED_ATTEMPTS] ?: 0,
            lockoutUntilTimestamp = preferences[PreferencesKeys.LOCKOUT_UNTIL] ?: 0L,
            isScreenProtectionEnabled = preferences[PreferencesKeys.SCREEN_PROTECTION_ENABLED] ?: false,
            isSimpleUiMode = preferences[PreferencesKeys.SIMPLE_UI_MODE] ?: true,
            clipboardTimeoutSeconds = preferences[PreferencesKeys.CLIPBOARD_TIMEOUT] ?: 30,
            defaultCipher = preferences[PreferencesKeys.DEFAULT_CIPHER] ?: "AES_256_GCM",
            shreddingPasses = preferences[PreferencesKeys.SHREDDING_PASSES] ?: 3,
            hapticFeedbackEnabled = preferences[PreferencesKeys.HAPTIC_FEEDBACK_ENABLED] ?: true,
            readerFontSizeSp = preferences[PreferencesKeys.READER_FONT_SIZE] ?: 15,
            highContrastText = preferences[PreferencesKeys.HIGH_CONTRAST_TEXT] ?: false,
            textLineWrapEnabled = preferences[PreferencesKeys.TEXT_LINE_WRAP_ENABLED] ?: true
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_LOCK_ENABLED] = enabled
        }
    }

    suspend fun setBiometricsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BIOMETRICS_ENABLED] = enabled
        }
    }

    suspend fun setAutoLockTimeout(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_LOCK_TIMEOUT] = seconds
        }
    }

    suspend fun saveMasterPin(saltHex: String, hashHex: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PIN_SALT] = saltHex
            preferences[PreferencesKeys.PIN_HASH] = hashHex
            preferences[PreferencesKeys.APP_LOCK_ENABLED] = true
            preferences[PreferencesKeys.FAILED_ATTEMPTS] = 0
            preferences[PreferencesKeys.LOCKOUT_UNTIL] = 0L
        }
    }

    suspend fun removeMasterPin() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.PIN_SALT)
            preferences.remove(PreferencesKeys.PIN_HASH)
            preferences[PreferencesKeys.APP_LOCK_ENABLED] = false
            preferences[PreferencesKeys.FAILED_ATTEMPTS] = 0
            preferences[PreferencesKeys.LOCKOUT_UNTIL] = 0L
        }
    }

    suspend fun recordFailedAttempt(currentFailed: Int) {
        val newFailed = currentFailed + 1
        val lockoutMs = if (newFailed >= 5) {
            System.currentTimeMillis() + 30_000L // 30 sec timeout after 5 failures
        } else 0L

        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FAILED_ATTEMPTS] = newFailed
            preferences[PreferencesKeys.LOCKOUT_UNTIL] = lockoutMs
        }
    }

    suspend fun resetFailedAttempts() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FAILED_ATTEMPTS] = 0
            preferences[PreferencesKeys.LOCKOUT_UNTIL] = 0L
        }
    }

    suspend fun setScreenProtectionEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SCREEN_PROTECTION_ENABLED] = enabled
        }
    }

    suspend fun setSimpleUiMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SIMPLE_UI_MODE] = enabled
        }
    }

    suspend fun setClipboardTimeoutSeconds(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CLIPBOARD_TIMEOUT] = seconds
        }
    }

    suspend fun setDefaultCipher(cipherName: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_CIPHER] = cipherName
        }
    }

    suspend fun setShreddingPasses(passes: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHREDDING_PASSES] = passes
        }
    }

    suspend fun setHapticFeedbackEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAPTIC_FEEDBACK_ENABLED] = enabled
        }
    }

    suspend fun setReaderFontSizeSp(sizeSp: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.READER_FONT_SIZE] = sizeSp
        }
    }

    suspend fun setHighContrastText(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HIGH_CONTRAST_TEXT] = enabled
        }
    }

    suspend fun setTextLineWrapEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TEXT_LINE_WRAP_ENABLED] = enabled
        }
    }
}
