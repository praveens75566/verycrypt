package com.example.security

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

data class SignatureResult(
    val alias: String,
    val signatureBytes: ByteArray,
    val signatureBase64: String,
    val signatureHex: String
)

data class VerificationResult(
    val isValid: Boolean,
    val details: String,
    val checkedBytes: Long
)

object SignatureEngine {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    private const val EC_CURVE_NAME = "secp256r1" // NIST P-256
    private const val BUFFER_SIZE = 64 * 1024

    fun generateKeystoreKeyPair(alias: String): PublicKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }

        val kpg = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE
        )

        val parameterSpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        ).apply {
            setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE_NAME))
            setDigests(KeyProperties.DIGEST_SHA256)
        }.build()

        kpg.initialize(parameterSpec)
        val keyPair = kpg.generateKeyPair()
        return keyPair.public
    }

    fun generateSoftwareKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec(EC_CURVE_NAME))
        return kpg.generateKeyPair()
    }

    fun getPublicKeyFromKeystore(alias: String): PublicKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val cert = keyStore.getCertificate(alias) ?: return null
        return cert.publicKey
    }

    fun getPrivateKeyFromKeystore(alias: String): PrivateKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
        return entry?.privateKey
    }

    suspend fun signStream(
        inputStream: InputStream,
        privateKey: PrivateKey,
        alias: String
    ): SignatureResult = withContext(Dispatchers.IO) {
        val signer = Signature.getInstance(SIGNATURE_ALGORITHM)
        signer.initSign(privateKey)

        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        inputStream.use { stream ->
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                signer.update(buffer, 0, bytesRead)
            }
        }

        val sigBytes = signer.sign()
        val sigBase64 = Base64.encodeToString(sigBytes, Base64.NO_WRAP)
        val sigHex = HashingEngine.bytesToHex(sigBytes)

        SignatureResult(
            alias = alias,
            signatureBytes = sigBytes,
            signatureBase64 = sigBase64,
            signatureHex = sigHex
        )
    }

    suspend fun verifyStream(
        inputStream: InputStream,
        signatureBytes: ByteArray,
        publicKey: PublicKey
    ): VerificationResult = withContext(Dispatchers.IO) {
        val verifier = Signature.getInstance(SIGNATURE_ALGORITHM)
        verifier.initVerify(publicKey)

        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        var totalBytes = 0L

        inputStream.use { stream ->
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                verifier.update(buffer, 0, bytesRead)
                totalBytes += bytesRead
            }
        }

        val isValid = try {
            verifier.verify(signatureBytes)
        } catch (e: Exception) {
            false
        }

        VerificationResult(
            isValid = isValid,
            details = if (isValid) {
                "Signature valid. Cryptographic proof matches the exact file contents."
            } else {
                "Signature verification failed. The file has been modified, corrupted, or signed by a different key."
            },
            checkedBytes = totalBytes
        )
    }

    fun exportPublicKeyToPem(publicKey: PublicKey): String {
        val encoded = Base64.encodeToString(publicKey.encoded, Base64.DEFAULT)
        return buildString {
            append("-----BEGIN PUBLIC KEY-----\n")
            append(encoded.trim())
            append("\n-----END PUBLIC KEY-----\n")
        }
    }

    fun importPublicKeyFromPem(pem: String): PublicKey {
        try {
            val cleanPem = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\r", "")
                .replace("\n", "")
                .trim()
            if (cleanPem.isEmpty()) {
                throw IllegalArgumentException("Public key input is empty.")
            }
            val keyBytes = try {
                Base64.decode(cleanPem, Base64.DEFAULT)
            } catch (e: IllegalArgumentException) {
                DiagnosticsEngine.recordError("SignatureEngine", "Base64 decoding failed for public key PEM", e)
                throw IllegalArgumentException("Malformed Base64 encoding in public key PEM: ${e.message}")
            }
            val spec = X509EncodedKeySpec(keyBytes)
            val kf = KeyFactory.getInstance("EC")
            return kf.generatePublic(spec)
        } catch (e: Exception) {
            DiagnosticsEngine.recordError("SignatureEngine", "Failed to parse EC public key from PEM", e)
            throw IllegalArgumentException("Invalid EC public key: ${e.localizedMessage ?: "Unknown parsing error"}", e)
        }
    }

    fun computePublicKeyFingerprint(publicKey: PublicKey): String {
        val rawHash = HashingEngine.computeSha256Bytes(publicKey.encoded)
        // Format as colon-separated hex pairs (e.g., e3:b0:c4:...)
        return rawHash.chunked(2).joinToString(":")
    }

    fun deleteKeystoreKey(alias: String) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }
}
