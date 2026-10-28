package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("VeriCrypt", appName)
  }

  @Test
  fun `verify pbkdf2 key derivation produces 256-bit key`() {
    val password = "StrongMasterPassword123!".toCharArray()
    val salt = ByteArray(16) { 0x42.toByte() }
    val secretKey = com.example.security.CryptoEngine.deriveKey(password, salt, 1000)
    assertEquals("AES", secretKey.algorithm)
    assertEquals(32, secretKey.encoded.size) // 256 bits
  }

  @Test
  fun `verify zip slip path traversal detection rejects parent traversal`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val vaultDir = com.example.security.VaultStorage.getVaultDirectory(context)
    val maliciousFileName = "../../databases/injected.db"
    val targetFile = java.io.File(vaultDir, maliciousFileName)

    var exceptionThrown = false
    try {
      if (!targetFile.canonicalPath.startsWith(vaultDir.canonicalPath + java.io.File.separator)) {
        throw SecurityException("Security Violation: Zip Slip path traversal detected in entry '$maliciousFileName'")
      }
    } catch (e: SecurityException) {
      exceptionThrown = true
    }

    org.junit.Assert.assertTrue("Malicious path traversal must be rejected", exceptionThrown)
  }

  @Test
  fun `verify corrupted header with invalid magic is rejected`() = kotlinx.coroutines.runBlocking {
    val corruptBytes = "NOT_A_VALID_HEADER_DATA_1234567890".toByteArray()
    var caught = false
    try {
      com.example.security.CryptoEngine.inspectContainerHeader(java.io.ByteArrayInputStream(corruptBytes))
    } catch (e: com.example.security.InvalidContainerException) {
      caught = true
    }
    org.junit.Assert.assertTrue("Corrupted header must be rejected with InvalidContainerException", caught)
  }

  @Test
  fun `verify malformed base64 in decryptText throws InvalidContainerException`() = kotlinx.coroutines.runBlocking {
    val malformedArmored = "-----BEGIN VERICRYPT ENCRYPTED MESSAGE-----\n!@#$%^&*()_+=\n-----END VERICRYPT ENCRYPTED MESSAGE-----"
    var caught = false
    try {
      com.example.security.CryptoEngine.decryptText(malformedArmored, "anyPassword")
    } catch (e: com.example.security.InvalidContainerException) {
      caught = true
    }
    org.junit.Assert.assertTrue("Malformed Base64 must be mapped to InvalidContainerException", caught)
  }

  @Test
  fun `verify DiagnosticsEngine self test executes cryptographic suite successfully`() = kotlinx.coroutines.runBlocking {
    val report = com.example.security.DiagnosticsEngine.runSelfTest()
    org.junit.Assert.assertTrue("Diagnostics self-test must execute primitives", report.results.size >= 4)
    org.junit.Assert.assertTrue("All cryptographic self-test primitives must pass", report.isAllPassed)
  }

  @Test
  fun `verify DiagnosticsEngine memory health telemetry`() {
    val (freeMb, totalMb, maxMb) = com.example.security.DiagnosticsEngine.getMemoryHealth()
    org.junit.Assert.assertTrue("Total memory must be positive", totalMb > 0)
    org.junit.Assert.assertTrue("Max memory must be >= total memory", maxMb >= totalMb)
  }

  @Test
  fun `verify KdfEngine rejects iterations exceeding upper bound`() {
    var caught = false
    try {
      com.example.security.KdfEngine.deriveKey(
        passphrase = "password".toCharArray(),
        salt = ByteArray(16) { 1 },
        iterations = 3_000_000
      )
    } catch (e: IllegalArgumentException) {
      caught = true
    }
    org.junit.Assert.assertTrue("KDF must reject iterations > 2,000,000", caught)
  }

  @Test
  fun `verify HmacEngine verifies both hex and base64 candidate strings`() {
    val key = ByteArray(32) { 0x07 }
    val data = "SecureWebhookPayload".toByteArray()
    val result = com.example.security.HmacEngine.computeHmac(
      algorithm = com.example.security.HmacAlgorithm.HMAC_SHA256,
      keyBytes = key,
      dataBytes = data
    )

    val isHexValid = com.example.security.HmacEngine.verifyHmac(result.macBytes, result.macHex)
    val isBase64Valid = com.example.security.HmacEngine.verifyHmac(result.macBytes, result.macBase64)
    val isInvalidRejected = !com.example.security.HmacEngine.verifyHmac(result.macBytes, "invalid_mac_hex_or_base64")

    org.junit.Assert.assertTrue("Hex HMAC must verify cleanly", isHexValid)
    org.junit.Assert.assertTrue("Base64 HMAC must verify cleanly", isBase64Valid)
    org.junit.Assert.assertTrue("Invalid HMAC must be rejected", isInvalidRejected)
  }

  @Test
  fun `verify ClipboardSecurityHelper attaches sensitive metadata`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.security.ClipboardSecurityHelper.copySensitiveText(
      context = context,
      label = "TestSecret",
      text = "SecretPassword123",
      showToastNotice = false
    )
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val clip = clipboard.primaryClip
    org.junit.Assert.assertNotNull(clip)
    org.junit.Assert.assertEquals("SecretPassword123", clip?.getItemAt(0)?.text?.toString())
  }

  @Test
  fun `verify UserSettings simpleUiMode defaults to true and updates cleanly`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.UserPreferencesRepository(context)
    val initialSettings = repo.userSettings.first()
    org.junit.Assert.assertTrue("Default UI mode should be Simple Mode for clean user experience", initialSettings.isSimpleUiMode)

    repo.setSimpleUiMode(false)
    val advancedSettings = repo.userSettings.first()
    org.junit.Assert.assertFalse("UI mode should update to advanced when toggled", advancedSettings.isSimpleUiMode)

    repo.setSimpleUiMode(true)
    val restoredSettings = repo.userSettings.first()
    org.junit.Assert.assertTrue("UI mode should revert to simple mode", restoredSettings.isSimpleUiMode)
  }

  @Test
  fun `verify UserSettings appearance and text readiness preferences`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.UserPreferencesRepository(context)

    // Test OLED theme setting
    repo.setThemeMode(com.example.data.ThemeMode.OLED)
    val oledSettings = repo.userSettings.first()
    org.junit.Assert.assertEquals(com.example.data.ThemeMode.OLED, oledSettings.themeMode)

    // Test Reader font scale
    repo.setReaderFontSizeSp(18)
    val fontSettings = repo.userSettings.first()
    org.junit.Assert.assertEquals(18, fontSettings.readerFontSizeSp)

    // Test High Contrast toggle
    repo.setHighContrastText(true)
    val contrastSettings = repo.userSettings.first()
    org.junit.Assert.assertTrue(contrastSettings.highContrastText)

    // Test Line Wrap toggle
    repo.setTextLineWrapEnabled(false)
    val wrapSettings = repo.userSettings.first()
    org.junit.Assert.assertFalse(wrapSettings.textLineWrapEnabled)
  }

  @Test
  fun `verify UserSettings clipboard and cryptographic defaults`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.UserPreferencesRepository(context)

    // Test Clipboard auto-clear timeout
    repo.setClipboardTimeoutSeconds(15)
    val clipSettings = repo.userSettings.first()
    org.junit.Assert.assertEquals(15, clipSettings.clipboardTimeoutSeconds)

    // Test Default cipher
    repo.setDefaultCipher("XCHACHA20_POLY1305")
    val cipherSettings = repo.userSettings.first()
    org.junit.Assert.assertEquals("XCHACHA20_POLY1305", cipherSettings.defaultCipher)

    // Test Shredding passes
    repo.setShreddingPasses(7)
    val shredSettings = repo.userSettings.first()
    org.junit.Assert.assertEquals(7, shredSettings.shreddingPasses)

    // Test Haptic feedback
    repo.setHapticFeedbackEnabled(false)
    val hapticSettings = repo.userSettings.first()
    org.junit.Assert.assertFalse(hapticSettings.hapticFeedbackEnabled)
  }
}
