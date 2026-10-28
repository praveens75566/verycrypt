package com.example

import com.example.security.CryptoDiagnostics
import com.example.security.CsprngTokenEngine
import com.example.security.HmacAlgorithm
import com.example.security.HmacEngine
import com.example.security.KdfEngine
import com.example.security.VeriCryptPackage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class HmacAndKdfSuiteTest {

    @Test
    fun testHmacSha256ComputationAndConstantTimeVerification() {
        val key = "secret-vericrypt-key".toByteArray(Charsets.UTF_8)
        val message = "The quick brown fox jumps over the lazy dog".toByteArray(Charsets.UTF_8)

        val result = HmacEngine.computeHmac(HmacAlgorithm.HMAC_SHA256, key, message)
        assertNotNull(result)
        assertEquals(32, result.macBytes.size)
        assertEquals(64, result.macHex.length)
        assertTrue(result.macBase64.isNotEmpty())

        // Verification passes with authentic tag
        val verifyPassHex = HmacEngine.verifyHmac(result.macBytes, result.macHex)
        assertTrue("Authentic hex MAC must verify", verifyPassHex)

        val verifyPassBase64 = HmacEngine.verifyHmac(result.macBytes, result.macBase64)
        assertTrue("Authentic base64 MAC must verify", verifyPassBase64)

        // Verification fails if candidate is tampered
        val tamperedHex = result.macHex.substring(0, result.macHex.length - 2) + if (result.macHex.endsWith("00")) "ff" else "00"
        val verifyFail = HmacEngine.verifyHmac(result.macBytes, tamperedHex)
        assertFalse("Tampered MAC must fail verification", verifyFail)
    }

    @Test
    fun testHmacSha512Computation() {
        val key = HmacEngine.generateRandomKey(HmacAlgorithm.HMAC_SHA512)
        assertEquals(64, key.size)

        val message = "Payload for HMAC-SHA512".toByteArray(Charsets.UTF_8)
        val result = HmacEngine.computeHmac(HmacAlgorithm.HMAC_SHA512, key, message)
        assertEquals(64, result.macBytes.size)
        assertEquals(128, result.macHex.length)

        val webhook = HmacEngine.formatAsWebhookSignature(HmacAlgorithm.HMAC_SHA512, result.macHex)
        assertTrue(webhook.startsWith("sha512="))
    }

    @Test
    fun testKdfPbkdf2DerivationRepeatability() {
        val pass = "VeriCryptMasterPassphrase2026".toCharArray()
        val salt = "0102030405060708090a0b0c0d0e0f10".chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        val result1 = KdfEngine.deriveKey(pass, salt, iterations = 10_000, keyLengthBits = 256)
        val result2 = KdfEngine.deriveKey(pass, salt, iterations = 10_000, keyLengthBits = 256)

        assertEquals("Same passphrase and salt must yield identical derived key", result1.keyHex, result2.keyHex)
        assertEquals(32, result1.derivedKeyBytes.size)

        // Different salt yields completely distinct key
        val salt2 = "ffffffffffffffffffffffffffffffff".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val result3 = KdfEngine.deriveKey(pass, salt2, iterations = 10_000, keyLengthBits = 256)
        assertNotEquals("Different salt must yield different derived key", result1.keyHex, result3.keyHex)
    }

    @Test
    fun testCsprngDicewarePassphrase() {
        val result5 = CsprngTokenEngine.generatePassphrase(wordCount = 5, separator = "-")
        val words = result5.token.split("-")
        assertEquals(5, words.size)
        assertTrue("5 words entropy should be >= 30.0 bits", result5.entropyBits >= 30.0)
        assertTrue(result5.shannonEntropyPerByte > 3.0)
    }

    @Test
    fun testCsprngPasswordAndTokenGenerators() {
        val pwd = CsprngTokenEngine.generatePassword(length = 24, includeUpper = true, includeLower = true, includeDigits = true, includeSymbols = true)
        assertEquals(24, pwd.token.length)
        assertTrue(pwd.entropyBits > 100.0)

        val hexToken = CsprngTokenEngine.generateHexToken(32)
        assertEquals(64, hexToken.token.length)
        assertEquals(256.0, hexToken.entropyBits, 0.01)
    }

    @Test
    fun testCryptoDiagnostics() {
        // Hex analysis
        val validHex = CryptoDiagnostics.analyzeHex("001122aabbcc")
        assertTrue(validHex.isValid)
        assertEquals(6, validHex.byteCount)

        val oddHex = CryptoDiagnostics.analyzeHex("001122a")
        assertFalse(oddHex.isValid)

        // Base64 analysis
        val validB64 = CryptoDiagnostics.analyzeBase64("SGVsbG8gV29ybGQ=")
        assertTrue(validB64.isValid)

        // Package inspection
        val pkg = VeriCryptPackage.createJsonPackage(
            algorithm = "XChaCha20-Poly1305",
            ciphertextBytes = ByteArray(100) { 1 },
            nonceBytes = ByteArray(24) { 2 },
            tagBytes = ByteArray(16) { 3 }
        )
        val inspection = CryptoDiagnostics.inspectPackage(pkg)
        assertTrue(inspection.isRecognizedPackage)
        assertEquals(24, inspection.nonceByteCount)
        assertTrue(inspection.isNonceValid)
        assertEquals(16, inspection.tagByteCount)
        assertTrue(inspection.isTagValid)

        // Decryption triage
        val keyError = CryptoDiagnostics.triageDecryptionFailure(IllegalArgumentException("bad key"), keyBytesLen = 16)
        assertTrue(keyError.contains("Key Size Error"))
    }
}
