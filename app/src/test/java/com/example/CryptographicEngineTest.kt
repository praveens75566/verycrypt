package com.example

import com.example.security.CryptoAuthenticationException
import com.example.security.CryptoEngine
import com.example.security.HashingEngine
import com.example.security.SignatureEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
class CryptographicEngineTest {

    @Test
    fun testSha256StandardVectors() {
        runBlocking {
            // NIST test vector for empty string
            val emptyHash = HashingEngine.computeSha256("".byteInputStream())
            assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", emptyHash)

            // Standard vector for "hello world"
            val helloHash = HashingEngine.computeSha256("hello world".byteInputStream())
            assertEquals("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9", helloHash)
        }
    }

    @Test
    fun testSha3_512StandardVectors() {
        runBlocking {
            // NIST FIPS 202 test vector for empty string
            val emptySha3 = HashingEngine.computeSha3_512("".byteInputStream())
            assertEquals(
                "a69f73cca23a9ac5c8b567dc185a756e97c982164fe25859e0d1dcc1475c80a615b2123af1f5f94c11e3e9402c3ac558f500199d95b6d3e301758586281dcd26",
                emptySha3
            )

            // Direct text hashing should also yield the exact same SHA3-512 hash
            val directTextResult = HashingEngine.hashTextDirectly("")
            assertEquals(emptySha3, directTextResult.sha3_512Hex)

            // Non-empty test
            val text = "VeriCrypt Cryptographic Security"
            val hashStream = HashingEngine.computeSha3_512(text.byteInputStream())
            val hashBytes = HashingEngine.computeSha3_512Bytes(text.toByteArray(Charsets.UTF_8))
            assertEquals(hashStream, hashBytes)
            assertTrue(hashBytes.length == 128)
            assertTrue(HashingEngine.compareHashes(hashStream, hashBytes))
        }
    }

    @Test
    fun testHashComparison() {
        val hash = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9"
        val upperHash = hash.uppercase()
        val formattedHash = "b9:4d:27:b9:93:4d:3e:08:a5:2e:52:d7:da:7d:ab:fa:c4:84:ef:e3:7a:53:80:ee:90:88:f7:ac:e2:ef:cd:e9"

        assertTrue(HashingEngine.compareHashes(hash, upperHash))
        assertTrue(HashingEngine.compareHashes(hash, formattedHash))
        assertFalse(HashingEngine.compareHashes(hash, "0000000000000000000000000000000000000000000000000000000000000000"))
    }

    @Test
    fun testAesGcmEncryptionDecryptionRoundtrip() {
        runBlocking {
            val originalText = "VeriCrypt: Military-grade offline confidentiality."
            val password = "SecureMasterPassword123!"
            val fileName = "confidential_document.txt"

            val inputStream = originalText.toByteArray(Charsets.UTF_8).inputStream()
            val encryptedOutput = ByteArrayOutputStream()

            // 1. Encrypt
            val encryptResult = CryptoEngine.encryptStream(
                inputStream = inputStream,
                outputStream = encryptedOutput,
                originalFileName = fileName,
                password = password.toCharArray()
            )

            val encryptedBytes = encryptedOutput.toByteArray()
            assertTrue(encryptedBytes.isNotEmpty())
            assertEquals(fileName, encryptResult.originalFileName)

            // 2. Decrypt with correct password
            val decryptedOutput = ByteArrayOutputStream()
            val decryptResult = CryptoEngine.decryptStream(
                inputStream = encryptedBytes.inputStream(),
                outputStream = decryptedOutput,
                password = password.toCharArray()
            )

            assertEquals(fileName, decryptResult.restoredFileName)
            assertEquals(originalText, decryptedOutput.toString(Charsets.UTF_8.name()))
        }
    }

    @Test
    fun testTamperedCiphertextRejection() {
        runBlocking {
            val originalText = "Top secret ledger data"
            val password = "StrongPassword987!"

            val encryptedOutput = ByteArrayOutputStream()
            CryptoEngine.encryptStream(
                inputStream = originalText.toByteArray(Charsets.UTF_8).inputStream(),
                outputStream = encryptedOutput,
                originalFileName = "ledger.dat",
                password = password.toCharArray()
            )

            val cipherBytes = encryptedOutput.toByteArray()

            // Flip a bit in the ciphertext / tag region (near the end of container)
            cipherBytes[cipherBytes.size - 4] = (cipherBytes[cipherBytes.size - 4].toInt() xor 0xFF).toByte()

            val corruptedOutput = ByteArrayOutputStream()

            // Attempting to decrypt tampered bytes must throw CryptoAuthenticationException
            assertThrows(CryptoAuthenticationException::class.java) {
                runBlocking {
                    CryptoEngine.decryptStream(
                        inputStream = cipherBytes.inputStream(),
                        outputStream = corruptedOutput,
                        password = password.toCharArray()
                    )
                }
            }
        }
    }

    @Test
    fun testWrongPasswordRejection() {
        runBlocking {
            val originalText = "Sensitive payload"
            val correctPassword = "CorrectPassword123"
            val wrongPassword = "WrongPassword456"

            val encryptedOutput = ByteArrayOutputStream()
            CryptoEngine.encryptStream(
                inputStream = originalText.toByteArray(Charsets.UTF_8).inputStream(),
                outputStream = encryptedOutput,
                originalFileName = "payload.bin",
                password = correctPassword.toCharArray()
            )

            val cipherBytes = encryptedOutput.toByteArray()
            val output = ByteArrayOutputStream()

            assertThrows(CryptoAuthenticationException::class.java) {
                runBlocking {
                    CryptoEngine.decryptStream(
                        inputStream = cipherBytes.inputStream(),
                        outputStream = output,
                        password = wrongPassword.toCharArray()
                    )
                }
            }
        }
    }

    @Test
    fun testEcdsaSignAndVerifyRoundtrip() {
        runBlocking {
            val payload = "Important signed contract content."
            val keyPair = SignatureEngine.generateSoftwareKeyPair()

            // Sign stream
            val sigResult = SignatureEngine.signStream(
                inputStream = payload.toByteArray(Charsets.UTF_8).inputStream(),
                privateKey = keyPair.private,
                alias = "test_contract_key"
            )

            assertTrue(sigResult.signatureBytes.isNotEmpty())
            assertTrue(sigResult.signatureBase64.isNotEmpty())

            // Verify valid stream
            val validVerification = SignatureEngine.verifyStream(
                inputStream = payload.toByteArray(Charsets.UTF_8).inputStream(),
                signatureBytes = sigResult.signatureBytes,
                publicKey = keyPair.public
            )
            assertTrue(validVerification.isValid)

            // Verify altered stream -> must be invalid
            val alteredVerification = SignatureEngine.verifyStream(
                inputStream = "Tampered contract content.".toByteArray(Charsets.UTF_8).inputStream(),
                signatureBytes = sigResult.signatureBytes,
                publicKey = keyPair.public
            )
            assertFalse(alteredVerification.isValid)
        }
    }

    @Test
    fun testPublicKeyPemExportImport() {
        val keyPair = SignatureEngine.generateSoftwareKeyPair()
        val pem = SignatureEngine.exportPublicKeyToPem(keyPair.public)

        assertTrue(pem.startsWith("-----BEGIN PUBLIC KEY-----"))
        assertTrue(pem.endsWith("-----END PUBLIC KEY-----\n"))

        val importedKey = SignatureEngine.importPublicKeyFromPem(pem)
        assertEquals(keyPair.public.encoded.toList(), importedKey.encoded.toList())
    }

    @Test
    fun testSha512StandardVectors() {
        runBlocking {
            // NIST test vector for empty string SHA-512
            val emptyHash = HashingEngine.computeSha512("".byteInputStream())
            assertEquals(
                "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e",
                emptyHash
            )

            // Standard vector for "hello world" SHA-512
            val helloHash = HashingEngine.computeSha512("hello world".byteInputStream())
            assertEquals(
                "309ecc489c12d6eb4cc40f50c902f2b4d0ed77ee511a7c7a9bcd3ca86d4cd86f989dd35bc5ff499670da34255b45b0cfd830e81f605dcf7dc5542e93ae9cd76f",
                helloHash
            )
        }
    }

    @Test
    fun testDirectTextHashing() {
        val sample = "Confidential Message To Verify"
        val result = HashingEngine.hashTextDirectly(sample)

        assertEquals(sample, result.rawText)
        assertEquals(sample.length, result.lengthChars)
        assertEquals(sample.toByteArray(Charsets.UTF_8).size, result.sizeBytes)
        assertEquals(64, result.sha256Hex.length)
        assertEquals(128, result.sha512Hex.length)
    }

    @Test
    fun testDirectTextEncryptionDecryptionRoundtrip() {
        runBlocking {
            val secretText = "Top-secret operational directive 2026. Keep fully offline."
            val passphrase = "UltraSecurePassword789!"

            val armoredCiphertext = CryptoEngine.encryptText(secretText, passphrase)
            assertTrue(armoredCiphertext.contains("BEGIN VERICRYPT ENCRYPTED MESSAGE"))
            assertTrue(armoredCiphertext.contains("END VERICRYPT ENCRYPTED MESSAGE"))

            // Valid roundtrip
            val recovered = CryptoEngine.decryptText(armoredCiphertext, passphrase)
            assertEquals(secretText, recovered)

            // Wrong password fails
            assertThrows(CryptoAuthenticationException::class.java) {
                runBlocking {
                    CryptoEngine.decryptText(armoredCiphertext, "WrongPassword321!")
                }
            }
        }
    }

    @Test
    fun testEntropyCalculations() {
        // Test 1: All identical bytes (zero entropy)
        val zeros = ByteArray(1024) { 0 }
        val zeroEntropy = com.example.security.EntropyEngine.calculateEntropy(zeros)
        assertEquals(0.0, zeroEntropy.entropyBitsPerByte, 0.001)
        assertEquals(com.example.security.EntropyAssessment.VERY_LOW, zeroEntropy.assessment)

        // Test 2: Uniform distribution across all 256 bytes (maximum 8.0 entropy)
        val uniform = ByteArray(256 * 10) { (it % 256).toByte() }
        val uniformEntropy = com.example.security.EntropyEngine.calculateEntropy(uniform)
        assertEquals(8.0, uniformEntropy.entropyBitsPerByte, 0.001)
        assertEquals(com.example.security.EntropyAssessment.HIGH, uniformEntropy.assessment)

        // Test 3: Natural English text (~3.5 - 5.0 bits/byte)
        val sampleText = "The quick brown fox jumps over the lazy dog. Repetitive structure and common ASCII chars."
        val textEntropy = com.example.security.EntropyEngine.calculateTextEntropy(sampleText)
        assertTrue(textEntropy.entropyBitsPerByte in 3.0..5.5)

        // Test 4: High entropy crypto ciphertext
        val randomBytes = java.security.SecureRandom().let { rng ->
            ByteArray(1024).also { rng.nextBytes(it) }
        }
        val cryptoEntropy = com.example.security.EntropyEngine.calculateEntropy(randomBytes)
        assertTrue(cryptoEntropy.entropyBitsPerByte > 7.5)
        assertEquals(com.example.security.EntropyAssessment.HIGH, cryptoEntropy.assessment)
    }

    @Test
    fun testAuditEngineReportGeneration() {
        val context = org.robolectric.RuntimeEnvironment.getApplication().applicationContext
        val report = com.example.security.AuditEngine.generateSecurityAudit(
            context = context,
            isAppLockConfigured = true
        )

        assertTrue(report.overallScore in 0..100)
        assertTrue(report.checks.isNotEmpty())
        assertTrue(report.toFormattedText().contains("VERICRYPT CRYPTOGRAPHIC AUDIT REPORT"))
        assertTrue(report.toFormattedJson().contains("VeriCrypt Cryptographic Security Audit"))
    }

    @Test
    fun testBenchmarkSuiteExecution() {
        runBlocking {
            val suiteResult = com.example.security.BenchmarkEngine.runBenchmark(
                iterations = 2,
                payloadSizeKb = 64
            )

            assertEquals(5, suiteResult.results.size)
            assertTrue(suiteResult.results.any { it.name.contains("SHA-256") })
            assertTrue(suiteResult.results.any { it.name.contains("SHA-512") })
            assertTrue(suiteResult.results.any { it.name.contains("AES-256-GCM") })
            assertTrue(suiteResult.results.any { it.name.contains("Cascade") })
            assertTrue(suiteResult.results.any { it.name.contains("XChaCha20") })
            assertTrue(suiteResult.results.all { it.throughputMBps >= 0.0 })
        }
    }
}

