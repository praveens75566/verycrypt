package com.example.security

import android.util.Base64
import org.json.JSONObject

/**
 * Standardized, self-contained encrypted package structure for 1-click export, sharing, and import.
 * Contains all parameters required to authenticate and decrypt ciphertext (Nonce, Tag, Ciphertext, AAD)
 * while strictly omitting the private secret key to prevent credential exposure.
 */
data class VeriCryptPackage(
    val schema: String = "vericrypt.package.v1",
    val algorithm: String = "XChaCha20-Poly1305",
    val createdAt: Long = System.currentTimeMillis(),
    val keyFingerprint: String = "",
    val nonceHex: String,
    val nonceBits: Int = 192,
    val tagHex: String,
    val tagBits: Int = 128,
    val aadText: String = "",
    val ciphertextHex: String,
    val ciphertextBase64: String
) {

    fun toJsonString(pretty: Boolean = true): String {
        val json = JSONObject().apply {
            put("schema", schema)
            put("algorithm", algorithm)
            put("created_at", createdAt)
            put("key_fingerprint", keyFingerprint)
            put("nonce_hex", nonceHex)
            put("nonce_bits", nonceBits)
            put("tag_hex", tagHex)
            put("tag_bits", tagBits)
            put("aad_text", aadText)
            put("ciphertext_hex", ciphertextHex)
            put("ciphertext_base64", ciphertextBase64)
        }
        return if (pretty) json.toString(2) else json.toString()
    }

    fun toArmoredString(): String {
        return buildString {
            append("-----BEGIN VERICRYPT ENCRYPTED PACKAGE-----\n")
            append("Version: 1.0\n")
            append("Algorithm: $algorithm\n")
            if (keyFingerprint.isNotBlank()) append("KeyHint: $keyFingerprint\n")
            append("NonceHex: $nonceHex\n")
            append("TagHex: $tagHex\n")
            if (aadText.isNotBlank()) {
                val aadB64 = Base64.encodeToString(aadText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                append("AAD-Base64: $aadB64\n")
            }
            append("\n")
            append(ciphertextBase64)
            append("\n-----END VERICRYPT ENCRYPTED PACKAGE-----\n")
        }
    }

    companion object {
        const val ARMOR_HEADER = "-----BEGIN VERICRYPT ENCRYPTED PACKAGE-----"
        const val ARMOR_FOOTER = "-----END VERICRYPT ENCRYPTED PACKAGE-----"

        fun createJsonPackage(
            algorithm: String,
            ciphertextBytes: ByteArray,
            nonceBytes: ByteArray,
            tagBytes: ByteArray,
            aadText: String = "",
            keyFingerprint: String = ""
        ): String {
            val pkg = VeriCryptPackage(
                algorithm = algorithm,
                keyFingerprint = keyFingerprint,
                nonceHex = HashingEngine.bytesToHex(nonceBytes),
                nonceBits = nonceBytes.size * 8,
                tagHex = HashingEngine.bytesToHex(tagBytes),
                tagBits = tagBytes.size * 8,
                aadText = aadText,
                ciphertextHex = HashingEngine.bytesToHex(ciphertextBytes),
                ciphertextBase64 = Base64.encodeToString(ciphertextBytes, Base64.NO_WRAP)
            )
            return pkg.toJsonString(pretty = true)
        }

        fun fromJsonString(jsonStr: String): VeriCryptPackage {
            val json = JSONObject(jsonStr.trim())
            return VeriCryptPackage(
                schema = json.optString("schema", "vericrypt.package.v1"),
                algorithm = json.optString("algorithm", "XChaCha20-Poly1305"),
                createdAt = json.optLong("created_at", System.currentTimeMillis()),
                keyFingerprint = json.optString("key_fingerprint", ""),
                nonceHex = json.getString("nonce_hex"),
                nonceBits = json.optInt("nonce_bits", 192),
                tagHex = json.getString("tag_hex"),
                tagBits = json.optInt("tag_bits", 128),
                aadText = json.optString("aad_text", ""),
                ciphertextHex = json.optString("ciphertext_hex", ""),
                ciphertextBase64 = json.getString("ciphertext_base64")
            )
        }

        fun fromArmoredString(armored: String): VeriCryptPackage {
            val lines = armored.lines().map { it.trim() }
            var algo = "XChaCha20-Poly1305"
            var keyHint = ""
            var nonceHex = ""
            var tagHex = ""
            var aad = ""
            val b64Builder = StringBuilder()
            var inBody = false

            for (line in lines) {
                if (line.startsWith("-----BEGIN")) {
                    inBody = false
                    continue
                }
                if (line.startsWith("-----END")) {
                    break
                }
                if (!inBody) {
                    if (line.isEmpty()) {
                        inBody = true
                        continue
                    }
                    val colonIdx = line.indexOf(':')
                    if (colonIdx > 0) {
                        val key = line.substring(0, colonIdx).trim()
                        val value = line.substring(colonIdx + 1).trim()
                        when (key.lowercase()) {
                            "algorithm" -> algo = value
                            "keyhint" -> keyHint = value
                            "nonce", "noncehex" -> nonceHex = value
                            "tag", "taghex" -> tagHex = value
                            "aad-base64" -> {
                                try {
                                    aad = String(Base64.decode(value, Base64.NO_WRAP), Charsets.UTF_8)
                                } catch (_: Exception) {}
                            }
                        }
                    }
                } else {
                    b64Builder.append(line)
                }
            }

            val b64 = b64Builder.toString().trim()
            val hex = try {
                val bytes = Base64.decode(b64, Base64.NO_WRAP)
                bytes.joinToString("") { "%02x".format(it) }
            } catch (_: Exception) {
                ""
            }

            return VeriCryptPackage(
                algorithm = algo,
                keyFingerprint = keyHint,
                nonceHex = nonceHex,
                nonceBits = if (nonceHex.length == 48) 192 else 96,
                tagHex = tagHex,
                tagBits = 128,
                aadText = aad,
                ciphertextHex = hex,
                ciphertextBase64 = b64
            )
        }

        /**
         * Smart parser that accepts JSON, armored string, or raw combined Base64.
         */
        fun parseAuto(input: String): VeriCryptPackage {
            val trimmed = input.trim()
            return when {
                trimmed.startsWith("{") && trimmed.endsWith("}") -> fromJsonString(trimmed)
                trimmed.contains("-----BEGIN") -> fromArmoredString(trimmed)
                else -> {
                    // Try parsing as JSON first
                    try {
                        fromJsonString(trimmed)
                    } catch (_: Exception) {
                        fromArmoredString(trimmed)
                    }
                }
            }
        }
    }
}
