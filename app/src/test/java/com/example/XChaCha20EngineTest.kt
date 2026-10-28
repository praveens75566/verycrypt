package com.example

import com.example.security.CryptoAuthenticationException
import com.example.security.XChaCha20Poly1305Engine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class XChaCha20EngineTest {

    @Test
    fun testKeyAndNonceGeneration() {
        val key1 = XChaCha20Poly1305Engine.generateSecureKey()
        val key2 = XChaCha20Poly1305Engine.generateSecureKey()
        assertEquals(32, key1.size)
        assertEquals(32, key2.size)
        assertFalse(key1.contentEquals(key2))

        val nonce1 = XChaCha20Poly1305Engine.generateExtendedNonce()
        val nonce2 = XChaCha20Poly1305Engine.generateExtendedNonce()
        assertEquals(24, nonce1.size)
        assertEquals(24, nonce2.size)
        assertFalse(nonce1.contentEquals(nonce2))
    }

    @Test
    fun testHChaCha20SubkeyDerivation() {
        // RFC draft-irtf-cfrg-xchacha Section 2.2.1 Test Vector
        // Key = 00:01:02:03:04:05:06:07:08:09:0a:0b:0c:0d:0e:0f:10:11:12:13:14:15:16:17:18:19:1a:1b:1c:1d:1e:1f
        val key = ByteArray(32) { it.toByte() }
        // Nonce16 = 00:00:00:09:00:00:00:4a:00:00:00:00:31:41:59:27
        val nonce16 = byteArrayOf(
            0x00, 0x00, 0x00, 0x09, 0x00, 0x00, 0x00, 0x4a.toByte(),
            0x00, 0x00, 0x00, 0x00, 0x31, 0x41, 0x59, 0x27
        )

        val subkey = XChaCha20Poly1305Engine.hChaCha20(key, nonce16)
        assertEquals(32, subkey.size)

        // Expected output from RFC draft:
        // 82:41:3b:42:27:b2:7b:fe:d3:0e:42:50:8a:87:7d:73:a0:f9:e4:d5:8a:74:a8:53:c1:2e:c4:13:26:d3:ec:dc
        val expectedHex = "82413b4227b27bfed30e42508a877d73a0f9e4d58a74a853c12ec41326d3ecdc"
        val subkeyHex = subkey.joinToString("") { "%02x".format(it) }
        assertEquals(expectedHex, subkeyHex)
    }

    @Test
    fun testXChaCha20Poly1305RoundTrip() {
        val key = XChaCha20Poly1305Engine.generateSecureKey()
        val plaintext = "VeriCrypt: Confirmed Authenticated Confidentiality with 192-bit Nonce.".toByteArray(Charsets.UTF_8)
        val aad = "User: Alice | Domain: VeriCrypt".toByteArray(Charsets.UTF_8)

        val encResult = XChaCha20Poly1305Engine.encrypt(
            plaintext = plaintext,
            key = key,
            aad = aad,
            enforceNonceCheck = false
        )

        assertEquals(24, encResult.nonce.size)
        assertEquals(16, encResult.tag.size)
        assertEquals(plaintext.size, encResult.ciphertext.size)

        val decrypted = XChaCha20Poly1305Engine.decrypt(
            ciphertext = encResult.ciphertext,
            key = key,
            nonce = encResult.nonce,
            tag = encResult.tag,
            aad = aad
        )

        assertArrayEquals(plaintext, decrypted)
        assertEquals(String(plaintext, Charsets.UTF_8), String(decrypted, Charsets.UTF_8))
    }

    @Test
    fun testAuthenticationFailureOnCiphertextTamper() {
        val key = XChaCha20Poly1305Engine.generateSecureKey()
        val plaintext = "Confidential classified intelligence.".toByteArray(Charsets.UTF_8)

        val encResult = XChaCha20Poly1305Engine.encrypt(
            plaintext = plaintext,
            key = key,
            enforceNonceCheck = false
        )

        // Corrupt 1 bit in ciphertext
        val tamperedCiphertext = encResult.ciphertext.clone()
        tamperedCiphertext[0] = (tamperedCiphertext[0].toInt() xor 0x01).toByte()

        assertThrows(CryptoAuthenticationException::class.java) {
            XChaCha20Poly1305Engine.decrypt(
                ciphertext = tamperedCiphertext,
                key = key,
                nonce = encResult.nonce,
                tag = encResult.tag
            )
        }
    }

    @Test
    fun testAuthenticationFailureOnTagTamper() {
        val key = XChaCha20Poly1305Engine.generateSecureKey()
        val plaintext = "Financial payment record $5,000,000 USD.".toByteArray(Charsets.UTF_8)

        val encResult = XChaCha20Poly1305Engine.encrypt(
            plaintext = plaintext,
            key = key,
            enforceNonceCheck = false
        )

        // Corrupt 1 bit in Poly1305 tag
        val tamperedTag = encResult.tag.clone()
        tamperedTag[15] = (tamperedTag[15].toInt() xor 0x80).toByte()

        assertThrows(CryptoAuthenticationException::class.java) {
            XChaCha20Poly1305Engine.decrypt(
                ciphertext = encResult.ciphertext,
                key = key,
                nonce = encResult.nonce,
                tag = tamperedTag
            )
        }
    }

    @Test
    fun testAuthenticationFailureOnAadMismatch() {
        val key = XChaCha20Poly1305Engine.generateSecureKey()
        val plaintext = "Valid transaction".toByteArray(Charsets.UTF_8)
        val originalAad = "Sender: Account#1001".toByteArray(Charsets.UTF_8)
        val tamperedAad = "Sender: Account#9999".toByteArray(Charsets.UTF_8)

        val encResult = XChaCha20Poly1305Engine.encrypt(
            plaintext = plaintext,
            key = key,
            aad = originalAad,
            enforceNonceCheck = false
        )

        assertThrows(CryptoAuthenticationException::class.java) {
            XChaCha20Poly1305Engine.decrypt(
                ciphertext = encResult.ciphertext,
                key = key,
                nonce = encResult.nonce,
                tag = encResult.tag,
                aad = tamperedAad
            )
        }
    }

    @Test
    fun testArmoredMessageRoundTrip() {
        val key = XChaCha20Poly1305Engine.generateSecureKey()
        val plaintext = "Armored XChaCha20 Test Message with ASCII Headers".toByteArray(Charsets.UTF_8)

        val encResult = XChaCha20Poly1305Engine.encrypt(
            plaintext = plaintext,
            key = key,
            enforceNonceCheck = false
        )

        val armored = encResult.toArmoredString()
        assertTrue(armored.contains("-----BEGIN XCHACHA20-POLY1305 ENCRYPTED MESSAGE-----"))
        assertTrue(armored.contains("NonceSize: 192-bit"))

        val decrypted = XChaCha20Poly1305Engine.decryptArmoredOrCombined(armored, key)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun testPipelineTracing() {
        val key = XChaCha20Poly1305Engine.generateSecureKey()
        val nonce = XChaCha20Poly1305Engine.generateExtendedNonce()
        val plaintext = "Hello World".toByteArray(Charsets.UTF_8)

        val pipeline = XChaCha20Poly1305Engine.tracePipeline(plaintext, key, nonce, null)
        assertEquals(7, pipeline.size)
        assertEquals("1. Input 256-bit Secret Key", pipeline[0].title)
        assertEquals("3. HChaCha20 Subkey Derivation", pipeline[2].title)
        assertEquals("7. Poly1305 128-bit Authentication Tag", pipeline[6].title)
    }

    @Test
    fun testTamperSimulationHelper() {
        val key = XChaCha20Poly1305Engine.generateSecureKey()
        val nonce = XChaCha20Poly1305Engine.generateExtendedNonce()
        val plaintext = "Test Data".toByteArray(Charsets.UTF_8)

        val ctResult = XChaCha20Poly1305Engine.testTamperResistance(
            plaintext, key, nonce, XChaCha20Poly1305Engine.TamperTarget.CIPHERTEXT
        )
        assertTrue(ctResult.rejected)

        val tagResult = XChaCha20Poly1305Engine.testTamperResistance(
            plaintext, key, nonce, XChaCha20Poly1305Engine.TamperTarget.TAG
        )
        assertTrue(tagResult.rejected)
    }
}
