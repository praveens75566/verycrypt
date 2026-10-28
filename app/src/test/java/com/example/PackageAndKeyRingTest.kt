package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.model.SymmetricKeyMeta
import com.example.security.HashingEngine
import com.example.security.VeriCryptPackage
import com.example.security.XChaCha20Poly1305Engine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PackageAndKeyRingTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testVeriCryptPackageJsonSerializationAndRoundTrip() {
        val key = XChaCha20Poly1305Engine.generateSecureKey()
        val nonce = XChaCha20Poly1305Engine.generateExtendedNonce()
        val plaintext = "Test 1-click shareable payload with XChaCha20".toByteArray(Charsets.UTF_8)
        val aad = "Context: Testing".toByteArray(Charsets.UTF_8)

        val enc = XChaCha20Poly1305Engine.encrypt(
            plaintext = plaintext,
            key = key,
            nonce = nonce,
            aad = aad,
            enforceNonceCheck = false
        )

        val cipherHex = HashingEngine.bytesToHex(enc.ciphertext)
        val cipherB64 = android.util.Base64.encodeToString(enc.ciphertext, android.util.Base64.NO_WRAP)
        val nonceHex = HashingEngine.bytesToHex(enc.nonce)
        val tagHex = HashingEngine.bytesToHex(enc.tag)

        val pkg = VeriCryptPackage(
            algorithm = "XChaCha20-Poly1305",
            keyFingerprint = HashingEngine.computeSha256Bytes(key).take(16),
            nonceHex = nonceHex,
            nonceBits = 192,
            tagHex = tagHex,
            tagBits = 128,
            aadText = "Context: Testing",
            ciphertextHex = cipherHex,
            ciphertextBase64 = cipherB64
        )

        val jsonStr = pkg.toJsonString(pretty = true)
        assertTrue(jsonStr.contains("\"algorithm\": \"XChaCha20-Poly1305\""))
        assertTrue(jsonStr.contains("\"nonce_hex\": \"$nonceHex\""))

        // Deserialize back
        val parsedPkg = VeriCryptPackage.fromJsonString(jsonStr)
        assertEquals(pkg.algorithm, parsedPkg.algorithm)
        assertEquals(pkg.nonceHex, parsedPkg.nonceHex)
        assertEquals(pkg.tagHex, parsedPkg.tagHex)
        assertEquals(pkg.ciphertextBase64, parsedPkg.ciphertextBase64)
        assertEquals(pkg.aadText, parsedPkg.aadText)

        // Decrypt using parsed package fields
        val decrypted = XChaCha20Poly1305Engine.decrypt(
            ciphertext = HashingEngine.hexToBytes(parsedPkg.ciphertextHex),
            key = key,
            nonce = HashingEngine.hexToBytes(parsedPkg.nonceHex),
            tag = HashingEngine.hexToBytes(parsedPkg.tagHex),
            aad = parsedPkg.aadText.toByteArray(Charsets.UTF_8)
        )

        assertArrayEquals(plaintext, decrypted)
        assertEquals("Test 1-click shareable payload with XChaCha20", String(decrypted, Charsets.UTF_8))
    }

    @Test
    fun testVeriCryptPackageAutoParserWithArmoredString() {
        val key = XChaCha20Poly1305Engine.generateSecureKey()
        val nonce = XChaCha20Poly1305Engine.generateExtendedNonce()
        val plaintext = "Armored payload auto parsing".toByteArray(Charsets.UTF_8)

        val enc = XChaCha20Poly1305Engine.encrypt(
            plaintext = plaintext,
            key = key,
            nonce = nonce,
            enforceNonceCheck = false
        )

        val pkg = VeriCryptPackage(
            algorithm = "XChaCha20-Poly1305",
            keyFingerprint = "test-hint",
            nonceHex = HashingEngine.bytesToHex(enc.nonce),
            tagHex = HashingEngine.bytesToHex(enc.tag),
            ciphertextHex = HashingEngine.bytesToHex(enc.ciphertext),
            ciphertextBase64 = android.util.Base64.encodeToString(enc.ciphertext, android.util.Base64.NO_WRAP)
        )

        val armored = pkg.toArmoredString()
        assertTrue(armored.contains("-----BEGIN VERICRYPT ENCRYPTED PACKAGE-----"))

        val parsed = VeriCryptPackage.parseAuto(armored)
        assertEquals(pkg.algorithm, parsed.algorithm)
        assertEquals(pkg.nonceHex, parsed.nonceHex)
        assertEquals(pkg.tagHex, parsed.tagHex)
        assertEquals(pkg.ciphertextBase64, parsed.ciphertextBase64)
    }

    @Test
    fun testSymmetricKeyRingPersistence() = runBlocking {
        val dao = db.symmetricKeyDao()
        assertEquals(0, dao.getAllSymmetricKeys().first().size)

        val key1 = SymmetricKeyMeta(
            alias = "Server Backup Key",
            algorithm = "XChaCha20-Poly1305",
            keyHex = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            fingerprintSha256 = "0123456789abcdef",
            note = "Production sync"
        )

        val key2 = SymmetricKeyMeta(
            alias = "Personal Note Key",
            algorithm = "XChaCha20-Poly1305",
            keyHex = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210",
            fingerprintSha256 = "fedcba9876543210",
            note = "Vault notes"
        )

        val id1 = dao.insertSymmetricKey(key1)
        val id2 = dao.insertSymmetricKey(key2)
        assertTrue(id1 > 0)
        assertTrue(id2 > 0)

        val allKeys = dao.getAllSymmetricKeys().first()
        assertEquals(2, allKeys.size)

        val fetched = dao.getSymmetricKeyByAlias("Server Backup Key")
        assertNotNull(fetched)
        assertEquals("Server Backup Key", fetched?.alias)
        assertEquals(key1.keyHex, fetched?.keyHex)

        // Delete key
        dao.deleteSymmetricKeyById(id1)
        val keysAfterDelete = dao.getAllSymmetricKeys().first()
        assertEquals(1, keysAfterDelete.size)
        assertEquals("Personal Note Key", keysAfterDelete[0].alias)
    }
}
