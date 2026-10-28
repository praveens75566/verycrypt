package com.example.security

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Enterprise-grade implementation of XChaCha20-Poly1305 authenticated encryption (AEAD).
 *
 * Conforms to:
 * - RFC 8439 (ChaCha20 and Poly1305 for IETF Protocols)
 * - RFC Draft: draft-irtf-cfrg-xchacha (XChaCha: Extended-nonce ChaCha and AEAD_XChaCha20_Poly1305)
 *
 * Parameters:
 * - Key Size: 256 bits (32 bytes)
 * - Nonce Size: 192 bits (24 bytes)
 * - Authentication Tag Size: 128 bits (16 bytes)
 *
 * Features:
 * - Constant-time tag verification to resist timing attacks
 * - Nonce-reuse prevention guard
 * - Detailed cryptographic step instrumentation for interactive security audits
 * - Tamper simulation utility for integrity demonstrations
 */
object XChaCha20Poly1305Engine {

    const val KEY_SIZE_BYTES = 32
    const val NONCE_SIZE_BYTES = 24
    const val TAG_SIZE_BYTES = 16
    const val STANDARD_CHACHA_NONCE_SIZE = 12

    private val secureRandom = SecureRandom()

    // Nonce reuse detection: keeps the last 10,000 nonce hashes in memory
    private val recentlyUsedNonces = Collections.newSetFromMap(
        object : LinkedHashMap<String, Boolean>(1000, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                return size > 10_000
            }
        }
    )

    data class XChaChaResult(
        val ciphertext: ByteArray,
        val nonce: ByteArray, // 24 bytes (192 bits)
        val tag: ByteArray,   // 16 bytes (128 bits)
        val aad: ByteArray?
    ) {
        val combinedPayload: ByteArray by lazy {
            // Container: Nonce (24) + Tag (16) + Ciphertext (N)
            val buf = ByteBuffer.allocate(nonce.size + tag.size + ciphertext.size)
            buf.put(nonce)
            buf.put(tag)
            buf.put(ciphertext)
            buf.array()
        }

        fun toArmoredString(): String {
            val base64 = android.util.Base64.encodeToString(combinedPayload, android.util.Base64.NO_WRAP)
            return buildString {
                append("-----BEGIN XCHACHA20-POLY1305 ENCRYPTED MESSAGE-----\n")
                append("Version: 1.0\n")
                append("KeySize: 256-bit\n")
                append("NonceSize: 192-bit\n")
                append("TagSize: 128-bit\n")
                append("Nonce: ${nonce.joinToString("") { "%02x".format(it) }}\n")
                append("Tag: ${tag.joinToString("") { "%02x".format(it) }}\n\n")
                append(base64)
                append("\n-----END XCHACHA20-POLY1305 ENCRYPTED MESSAGE-----\n")
            }
        }
    }

    data class CryptoPipelineStep(
        val title: String,
        val description: String,
        val hexValue: String
    )

    fun generateSecureKey(): ByteArray {
        val key = ByteArray(KEY_SIZE_BYTES)
        secureRandom.nextBytes(key)
        return key
    }

    fun generateExtendedNonce(): ByteArray {
        val nonce = ByteArray(NONCE_SIZE_BYTES)
        secureRandom.nextBytes(nonce)
        return nonce
    }

    /**
     * HChaCha20 subkey derivation as specified in RFC draft-irtf-cfrg-xchacha.
     * Takes a 256-bit key and the first 16 bytes of the nonce to produce a derived 256-bit subkey.
     */
    fun hChaCha20(key: ByteArray, nonce16: ByteArray): ByteArray {
        require(key.size == KEY_SIZE_BYTES) { "Key must be exactly 32 bytes (256 bits)" }
        require(nonce16.size == 16) { "HChaCha20 input nonce must be exactly 16 bytes" }

        val state = IntArray(16)
        // ChaCha20 constant "expand 32-byte k"
        state[0] = 0x61707865
        state[1] = 0x3320646e
        state[2] = 0x79622d32
        state[3] = 0x6b206574

        val keyWords = bytesToIntsLE(key)
        for (i in 0 until 8) {
            state[4 + i] = keyWords[i]
        }

        val nonceWords = bytesToIntsLE(nonce16)
        for (i in 0 until 4) {
            state[12 + i] = nonceWords[i]
        }

        // 20 rounds of ChaCha
        for (i in 0 until 10) {
            // Column rounds
            quarterRound(state, 0, 4, 8, 12)
            quarterRound(state, 1, 5, 9, 13)
            quarterRound(state, 2, 6, 10, 14)
            quarterRound(state, 3, 7, 11, 15)
            // Diagonal rounds
            quarterRound(state, 0, 5, 10, 15)
            quarterRound(state, 1, 6, 11, 12)
            quarterRound(state, 2, 7, 8, 13)
            quarterRound(state, 3, 4, 9, 14)
        }

        // Output words 0..3 and 12..15
        val outWords = IntArray(8)
        outWords[0] = state[0]
        outWords[1] = state[1]
        outWords[2] = state[2]
        outWords[3] = state[3]
        outWords[4] = state[12]
        outWords[5] = state[13]
        outWords[6] = state[14]
        outWords[7] = state[15]

        return intsToBytesLE(outWords)
    }

    /**
     * Encrypts plaintext using XChaCha20-Poly1305.
     */
    fun encrypt(
        plaintext: ByteArray,
        key: ByteArray,
        nonce: ByteArray? = null,
        aad: ByteArray? = null,
        enforceNonceCheck: Boolean = true
    ): XChaChaResult {
        require(key.size == KEY_SIZE_BYTES) { "Key must be exactly 32 bytes (256 bits)" }
        val finalNonce = nonce ?: generateExtendedNonce()
        require(finalNonce.size == NONCE_SIZE_BYTES) { "Nonce must be exactly 24 bytes (192 bits)" }

        if (enforceNonceCheck) {
            val nonceHash = MessageDigest.getInstance("SHA-256")
                .digest(key + finalNonce)
                .joinToString("") { "%02x".format(it) }
            synchronized(recentlyUsedNonces) {
                if (recentlyUsedNonces.contains(nonceHash)) {
                    throw CryptoAuthenticationException("FATAL NONCE REUSE DETECTED: This nonce has already been used with this key. Reusing nonces in Poly1305 destroys authenticity.")
                }
                recentlyUsedNonces.add(nonceHash)
            }
        }

        // 1. Derive subkey using HChaCha20 with first 16 bytes of nonce
        val noncePrefix16 = finalNonce.copyOfRange(0, 16)
        val subkey = hChaCha20(key, noncePrefix16)

        // 2. Build 12-byte standard ChaCha20 nonce: 4 bytes zero + last 8 bytes of extended nonce
        val chachaNonce12 = ByteArray(STANDARD_CHACHA_NONCE_SIZE)
        System.arraycopy(finalNonce, 16, chachaNonce12, 4, 8)

        // 3. Generate Poly1305 key using ChaCha20 block 0 with counter = 0
        val polyKeyBlock = chaCha20Block(subkey, 0, chachaNonce12)
        val poly1305Key = polyKeyBlock.copyOfRange(0, 32)

        // 4. Encrypt plaintext using ChaCha20 starting with counter = 1
        val ciphertext = chaCha20Process(plaintext, subkey, 1, chachaNonce12)

        // 5. Compute Poly1305 Tag over AAD and Ciphertext (RFC 8439 AEAD construction)
        val tag = computePoly1305Tag(poly1305Key, aad ?: ByteArray(0), ciphertext)

        return XChaChaResult(
            ciphertext = ciphertext,
            nonce = finalNonce,
            tag = tag,
            aad = aad
        )
    }

    /**
     * Decrypts ciphertext and verifies Poly1305 authentication tag.
     * Throws CryptoAuthenticationException if the tag does not match.
     */
    fun decrypt(
        ciphertext: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        tag: ByteArray,
        aad: ByteArray? = null
    ): ByteArray {
        require(key.size == KEY_SIZE_BYTES) { "Key must be exactly 32 bytes (256 bits)" }
        require(nonce.size == NONCE_SIZE_BYTES) { "Nonce must be exactly 24 bytes (192 bits)" }
        require(tag.size == TAG_SIZE_BYTES) { "Poly1305 tag must be exactly 16 bytes (128 bits)" }

        // 1. Derive subkey using HChaCha20
        val noncePrefix16 = nonce.copyOfRange(0, 16)
        val subkey = hChaCha20(key, noncePrefix16)

        // 2. Build 12-byte standard ChaCha20 nonce
        val chachaNonce12 = ByteArray(STANDARD_CHACHA_NONCE_SIZE)
        System.arraycopy(nonce, 16, chachaNonce12, 4, 8)

        // 3. Generate Poly1305 key from counter 0
        val polyKeyBlock = chaCha20Block(subkey, 0, chachaNonce12)
        val poly1305Key = polyKeyBlock.copyOfRange(0, 32)

        // 4. Compute expected tag over AAD and Ciphertext
        val expectedTag = computePoly1305Tag(poly1305Key, aad ?: ByteArray(0), ciphertext)

        // 5. Constant-time verification
        if (!MessageDigest.isEqual(expectedTag, tag)) {
            throw CryptoAuthenticationException(
                "XChaCha20-Poly1305 Authentication Failed: Data has been modified, tampered with, or key is incorrect. Decryption rejected."
            )
        }

        // 6. Decrypt ciphertext using ChaCha20 starting with counter = 1
        return chaCha20Process(ciphertext, subkey, 1, chachaNonce12)
    }

    /**
     * Decrypts an armored message or raw combined container (24-byte Nonce + 16-byte Tag + Ciphertext)
     */
    fun decryptArmoredOrCombined(armoredText: String, key: ByteArray, aad: ByteArray? = null): ByteArray {
        var clean = armoredText.trim()
        val begin = "-----BEGIN XCHACHA20-POLY1305 ENCRYPTED MESSAGE-----"
        val end = "-----END XCHACHA20-POLY1305 ENCRYPTED MESSAGE-----"

        if (clean.contains(begin)) {
            clean = clean.substringAfter(begin).substringBefore(end)
            if (clean.contains("\n\n")) {
                clean = clean.substringAfter("\n\n")
            }
        }

        clean = clean.replace("\r", "").replace("\n", "").trim()
        val combined = android.util.Base64.decode(clean, android.util.Base64.NO_WRAP)

        if (combined.size < NONCE_SIZE_BYTES + TAG_SIZE_BYTES) {
            throw InvalidContainerException("Payload too short for XChaCha20-Poly1305 container")
        }

        val nonce = combined.copyOfRange(0, NONCE_SIZE_BYTES)
        val tag = combined.copyOfRange(NONCE_SIZE_BYTES, NONCE_SIZE_BYTES + TAG_SIZE_BYTES)
        val ciphertext = combined.copyOfRange(NONCE_SIZE_BYTES + TAG_SIZE_BYTES, combined.size)

        return decrypt(ciphertext, key, nonce, tag, aad)
    }

    /**
     * Inspects intermediate values of XChaCha20-Poly1305 for educational and auditing purposes.
     */
    fun tracePipeline(
        plaintext: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray?
    ): List<CryptoPipelineStep> {
        val steps = mutableListOf<CryptoPipelineStep>()

        steps.add(
            CryptoPipelineStep(
                title = "1. Input 256-bit Secret Key",
                description = "Master secret key (32 bytes / 256 bits)",
                hexValue = key.joinToString("") { "%02x".format(it) }
            )
        )

        steps.add(
            CryptoPipelineStep(
                title = "2. 192-bit Extended Nonce (24 bytes)",
                description = "Extended nonce providing $2^{96}$ collision-safe space",
                hexValue = nonce.joinToString("") { "%02x".format(it) }
            )
        )

        val nonce16 = nonce.copyOfRange(0, 16)
        val subkey = hChaCha20(key, nonce16)

        steps.add(
            CryptoPipelineStep(
                title = "3. HChaCha20 Subkey Derivation",
                description = "HChaCha20(Key, Nonce[0..15]) produces an intermediate 256-bit subkey",
                hexValue = subkey.joinToString("") { "%02x".format(it) }
            )
        )

        val chachaNonce12 = ByteArray(STANDARD_CHACHA_NONCE_SIZE)
        System.arraycopy(nonce, 16, chachaNonce12, 4, 8)

        steps.add(
            CryptoPipelineStep(
                title = "4. ChaCha20 IETF Nonce Construction",
                description = "4 zero bytes + Nonce[16..23] forming 96-bit ChaCha20 nonce",
                hexValue = chachaNonce12.joinToString("") { "%02x".format(it) }
            )
        )

        val polyKeyBlock = chaCha20Block(subkey, 0, chachaNonce12)
        val poly1305Key = polyKeyBlock.copyOfRange(0, 32)

        steps.add(
            CryptoPipelineStep(
                title = "5. Poly1305 One-Time Key (OTK)",
                description = "Generated from ChaCha20 keystream block 0 (counter 0)",
                hexValue = poly1305Key.joinToString("") { "%02x".format(it) }
            )
        )

        val ciphertext = chaCha20Process(plaintext, subkey, 1, chachaNonce12)

        steps.add(
            CryptoPipelineStep(
                title = "6. ChaCha20 Stream Ciphertext",
                description = "Plaintext XOR with ChaCha20 keystream starting at counter 1",
                hexValue = ciphertext.joinToString("") { "%02x".format(it) }
            )
        )

        val tag = computePoly1305Tag(poly1305Key, aad ?: ByteArray(0), ciphertext)

        steps.add(
            CryptoPipelineStep(
                title = "7. Poly1305 128-bit Authentication Tag",
                description = "MAC computed over AAD || pad || Ciphertext || pad || lengths",
                hexValue = tag.joinToString("") { "%02x".format(it) }
            )
        )

        return steps
    }

    /**
     * Tamper demonstration helper: corrupts 1 bit in ciphertext, tag, or AAD and verifies rejection.
     */
    fun testTamperResistance(
        plaintext: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        corruptTarget: TamperTarget
    ): TamperTestResult {
        val originalResult = encrypt(plaintext, key, nonce, enforceNonceCheck = false)
        val modifiedCiphertext = originalResult.ciphertext.clone()
        val modifiedTag = originalResult.tag.clone()
        var modifiedAad = originalResult.aad?.clone()

        when (corruptTarget) {
            TamperTarget.CIPHERTEXT -> {
                if (modifiedCiphertext.isNotEmpty()) {
                    modifiedCiphertext[0] = (modifiedCiphertext[0].toInt() xor 0x01).toByte()
                }
            }
            TamperTarget.TAG -> {
                modifiedTag[0] = (modifiedTag[0].toInt() xor 0x01).toByte()
            }
            TamperTarget.AAD -> {
                modifiedAad = (modifiedAad ?: ByteArray(0)) + byteArrayOf(0x42)
            }
        }

        return try {
            decrypt(modifiedCiphertext, key, nonce, modifiedTag, modifiedAad)
            TamperTestResult(
                rejected = false,
                message = "CRITICAL FAILURE: Tampered data was accepted without detection!"
            )
        } catch (e: CryptoAuthenticationException) {
            TamperTestResult(
                rejected = true,
                message = "SUCCESS: Poly1305 rejected tampered data instantly. Plaintext remained zeroized."
            )
        }
    }

    enum class TamperTarget { CIPHERTEXT, TAG, AAD }
    data class TamperTestResult(val rejected: Boolean, val message: String)

    // =========================================================================
    // Internal ChaCha20 & Poly1305 primitives
    // =========================================================================

    private fun quarterRound(x: IntArray, a: Int, b: Int, c: Int, d: Int) {
        x[a] += x[b]
        x[d] = (x[d] xor x[a]).rotateLeft(16)
        x[c] += x[d]
        x[b] = (x[b] xor x[c]).rotateLeft(12)
        x[a] += x[b]
        x[d] = (x[d] xor x[a]).rotateLeft(8)
        x[c] += x[d]
        x[b] = (x[b] xor x[c]).rotateLeft(7)
    }

    private fun chaCha20Block(key: ByteArray, counter: Int, nonce: ByteArray): ByteArray {
        val state = IntArray(16)
        state[0] = 0x61707865
        state[1] = 0x3320646e
        state[2] = 0x79622d32
        state[3] = 0x6b206574

        val keyWords = bytesToIntsLE(key)
        for (i in 0 until 8) {
            state[4 + i] = keyWords[i]
        }

        state[12] = counter

        val nonceWords = bytesToIntsLE(nonce)
        state[13] = nonceWords[0]
        state[14] = nonceWords[1]
        state[15] = nonceWords[2]

        val working = state.clone()
        for (i in 0 until 10) {
            quarterRound(working, 0, 4, 8, 12)
            quarterRound(working, 1, 5, 9, 13)
            quarterRound(working, 2, 6, 10, 14)
            quarterRound(working, 3, 7, 11, 15)

            quarterRound(working, 0, 5, 10, 15)
            quarterRound(working, 1, 6, 11, 12)
            quarterRound(working, 2, 7, 8, 13)
            quarterRound(working, 3, 4, 9, 14)
        }

        for (i in 0 until 16) {
            working[i] += state[i]
        }

        return intsToBytesLE(working)
    }

    private fun chaCha20Process(
        input: ByteArray,
        key: ByteArray,
        initialCounter: Int,
        nonce: ByteArray
    ): ByteArray {
        val output = ByteArray(input.size)
        var counter = initialCounter
        var offset = 0

        while (offset < input.size) {
            val keyStream = chaCha20Block(key, counter, nonce)
            val blockSize = minOf(64, input.size - offset)
            for (i in 0 until blockSize) {
                output[offset + i] = (input[offset + i].toInt() xor keyStream[i].toInt()).toByte()
            }
            offset += blockSize
            counter++
        }

        return output
    }

    /**
     * RFC 8439 Poly1305 MAC computation.
     * Evaluates polynomial modulo 2^130 - 5.
     */
    private fun computePoly1305Tag(key: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray {
        require(key.size == 32) { "Poly1305 key must be 32 bytes" }

        // Construct AEAD construction: AAD || pad16 || Ciphertext || pad16 || len(AAD) || len(Ciphertext)
        val aadPadding = if (aad.size % 16 == 0) 0 else 16 - (aad.size % 16)
        val ctPadding = if (ciphertext.size % 16 == 0) 0 else 16 - (ciphertext.size % 16)

        val totalLen = aad.size + aadPadding + ciphertext.size + ctPadding + 16
        val macData = ByteBuffer.allocate(totalLen).order(ByteOrder.LITTLE_ENDIAN)

        macData.put(aad)
        if (aadPadding > 0) macData.put(ByteArray(aadPadding))
        macData.put(ciphertext)
        if (ctPadding > 0) macData.put(ByteArray(ctPadding))
        macData.putLong(aad.size.toLong())
        macData.putLong(ciphertext.size.toLong())

        return poly1305Mac(macData.array(), key)
    }

    /**
     * Core Poly1305 algorithm
     */
    private fun poly1305Mac(msg: ByteArray, key: ByteArray): ByteArray {
        // Clamp r
        val r0 = (key[0].toInt() and 0xff) or ((key[1].toInt() and 0xff) shl 8) or
                ((key[2].toInt() and 0xff) shl 16) or ((key[3].toInt() and 0x0f) shl 24)
        val r1 = (key[4].toInt() and 0xfc) or ((key[5].toInt() and 0xff) shl 8) or
                ((key[6].toInt() and 0xff) shl 16) or ((key[7].toInt() and 0x0f) shl 24)
        val r2 = (key[8].toInt() and 0xfc) or ((key[9].toInt() and 0xff) shl 8) or
                ((key[10].toInt() and 0xff) shl 16) or ((key[11].toInt() and 0x0f) shl 24)
        val r3 = (key[12].toInt() and 0xfc) or ((key[13].toInt() and 0xff) shl 8) or
                ((key[14].toInt() and 0xff) shl 16) or ((key[15].toInt() and 0x0f) shl 24)

        // Using BigInteger arithmetic for verified precision and clarity in Poly1305 modulo 2^130 - 5
        val p = java.math.BigInteger.valueOf(2).pow(130).subtract(java.math.BigInteger.valueOf(5))

        // Reconstruct r as BigInteger
        val rBytes = ByteArray(16)
        rBytes[0] = (r0 and 0xff).toByte()
        rBytes[1] = ((r0 shr 8) and 0xff).toByte()
        rBytes[2] = ((r0 shr 16) and 0xff).toByte()
        rBytes[3] = ((r0 shr 24) and 0xff).toByte()

        rBytes[4] = (r1 and 0xff).toByte()
        rBytes[5] = ((r1 shr 8) and 0xff).toByte()
        rBytes[6] = ((r1 shr 16) and 0xff).toByte()
        rBytes[7] = ((r1 shr 24) and 0xff).toByte()

        rBytes[8] = (r2 and 0xff).toByte()
        rBytes[9] = ((r2 shr 8) and 0xff).toByte()
        rBytes[10] = ((r2 shr 16) and 0xff).toByte()
        rBytes[11] = ((r2 shr 24) and 0xff).toByte()

        rBytes[12] = (r3 and 0xff).toByte()
        rBytes[13] = ((r3 shr 8) and 0xff).toByte()
        rBytes[14] = ((r3 shr 16) and 0xff).toByte()
        rBytes[15] = ((r3 shr 24) and 0xff).toByte()

        val rBigInt = toPositiveBigIntLE(rBytes)

        // Read s
        val sBytes = key.copyOfRange(16, 32)
        val sBigInt = toPositiveBigIntLE(sBytes)

        var accumulator = java.math.BigInteger.ZERO

        var offset = 0
        while (offset < msg.size) {
            val blockSize = minOf(16, msg.size - offset)
            val block = ByteArray(blockSize + 1)
            System.arraycopy(msg, offset, block, 0, blockSize)
            // Append 0x01 byte at position blockSize (Poly1305 delimiter)
            block[blockSize] = 0x01.toByte()

            val nBigInt = toPositiveBigIntLE(block)
            accumulator = accumulator.add(nBigInt).multiply(rBigInt).mod(p)
            offset += blockSize
        }

        // Add s modulo 2^128
        val tagBigInt = accumulator.add(sBigInt).mod(java.math.BigInteger.valueOf(2).pow(128))
        val tagBytes = toBytesLE(tagBigInt, 16)
        return tagBytes
    }

    private fun toPositiveBigIntLE(bytes: ByteArray): java.math.BigInteger {
        // Reverse to Big-Endian for BigInteger
        val reversed = ByteArray(bytes.size + 1)
        for (i in bytes.indices) {
            reversed[bytes.size - i] = bytes[i]
        }
        reversed[0] = 0 // Ensure positive
        return java.math.BigInteger(reversed)
    }

    private fun toBytesLE(bigInt: java.math.BigInteger, targetSize: Int): ByteArray {
        val beBytes = bigInt.toByteArray()
        val leBytes = ByteArray(targetSize)
        val len = minOf(targetSize, beBytes.size)

        for (i in 0 until len) {
            leBytes[i] = beBytes[beBytes.size - 1 - i]
        }
        return leBytes
    }

    private fun bytesToIntsLE(bytes: ByteArray): IntArray {
        val ints = IntArray(bytes.size / 4)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in ints.indices) {
            ints[i] = buffer.int
        }
        return ints
    }

    private fun intsToBytesLE(ints: IntArray): ByteArray {
        val buffer = ByteBuffer.allocate(ints.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in ints) {
            buffer.putInt(i)
        }
        return buffer.array()
    }
}
