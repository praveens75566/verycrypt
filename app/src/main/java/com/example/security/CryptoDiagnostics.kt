package com.example.security

import org.json.JSONObject

data class FormatDiagnostic(
    val isValid: Boolean,
    val byteCount: Int,
    val formatName: String,
    val message: String
)

data class PackageEnvelopeInspection(
    val isRecognizedPackage: Boolean,
    val formatType: String, // "JSON" or "Armored"
    val version: String,
    val algorithm: String,
    val nonceByteCount: Int,
    val isNonceValid: Boolean,
    val tagByteCount: Int,
    val isTagValid: Boolean,
    val ciphertextByteCount: Int,
    val hasAad: Boolean,
    val timestampFormatted: String,
    val structuralIntegrityStatus: String
)

object CryptoDiagnostics {

    /**
     * Inspects a string to determine if it is valid Hex and reports byte count.
     */
    fun analyzeHex(raw: String): FormatDiagnostic {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return FormatDiagnostic(false, 0, "Hex", "Empty input")
        }
        if (trimmed.length % 2 != 0) {
            return FormatDiagnostic(false, 0, "Hex", "Invalid odd character length (${trimmed.length} chars)")
        }
        val isHex = trimmed.matches(Regex("^[0-9a-fA-F]+$"))
        if (!isHex) {
            return FormatDiagnostic(false, 0, "Hex", "Contains non-hexadecimal characters")
        }
        val bytes = trimmed.length / 2
        return FormatDiagnostic(true, bytes, "Hex", "Valid $bytes-byte hexadecimal sequence")
    }

    /**
     * Inspects a string to determine if it is valid Base64 and reports byte count.
     */
    fun analyzeBase64(raw: String): FormatDiagnostic {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return FormatDiagnostic(false, 0, "Base64", "Empty input")
        }
        return try {
            val decoded = android.util.Base64.decode(trimmed, android.util.Base64.DEFAULT)
            FormatDiagnostic(true, decoded.size, "Base64", "Valid standard Base64 (${decoded.size} decoded bytes)")
        } catch (e: Exception) {
            FormatDiagnostic(false, 0, "Base64", "Malformed Base64 encoding: ${e.message}")
        }
    }

    /**
     * Inspects a raw string to see if it represents a VeriCrypt container envelope.
     */
    fun inspectPackage(rawInput: String): PackageEnvelopeInspection {
        val trimmed = rawInput.trim()

        // 1. Check if Armored
        if (trimmed.startsWith(VeriCryptPackage.ARMOR_HEADER) && trimmed.contains(VeriCryptPackage.ARMOR_FOOTER)) {
            val base64Body = trimmed
                .substringAfter(VeriCryptPackage.ARMOR_HEADER)
                .substringBefore(VeriCryptPackage.ARMOR_FOOTER)
                .trim()
            return try {
                val jsonString = String(android.util.Base64.decode(base64Body, android.util.Base64.DEFAULT), Charsets.UTF_8)
                parseJsonEnvelope(jsonString, "Armored ASCII")
            } catch (e: Exception) {
                unrecognizedPackage("Failed to unwrap armored ASCII container: ${e.message}")
            }
        }

        // 2. Check if JSON
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return try {
                parseJsonEnvelope(trimmed, "Raw JSON")
            } catch (e: Exception) {
                unrecognizedPackage("Malformed JSON structure: ${e.message}")
            }
        }

        return unrecognizedPackage("Input is not a VeriCrypt JSON or Armored package")
    }

    private fun parseJsonEnvelope(jsonString: String, formatType: String): PackageEnvelopeInspection {
        val json = JSONObject(jsonString)
        val schema = json.optString("schema", "")
        val magic = json.optString("magic", "")
        if (!schema.startsWith("vericrypt") && magic != "VERICRYPT_AEAD_PACKAGE" && !json.has("nonce_hex") && !json.has("nonce_base64")) {
            return unrecognizedPackage("Missing or invalid VeriCrypt schema header")
        }

        val version = if (schema.isNotEmpty()) schema.substringAfterLast("v", "1.0") else json.optString("version", "1.0")
        val algorithm = json.optString("algorithm", "XChaCha20-Poly1305")

        val nonceBytes = when {
            json.has("nonce_hex") -> try { HashingEngine.hexToBytes(json.getString("nonce_hex")) } catch (_: Exception) { ByteArray(0) }
            json.has("nonce_base64") -> try { android.util.Base64.decode(json.getString("nonce_base64"), android.util.Base64.DEFAULT) } catch (_: Exception) { ByteArray(0) }
            else -> ByteArray(0)
        }

        val tagBytes = when {
            json.has("tag_hex") -> try { HashingEngine.hexToBytes(json.getString("tag_hex")) } catch (_: Exception) { ByteArray(0) }
            json.has("tag_base64") -> try { android.util.Base64.decode(json.getString("tag_base64"), android.util.Base64.DEFAULT) } catch (_: Exception) { ByteArray(0) }
            else -> ByteArray(0)
        }

        val cipherBytes = when {
            json.has("ciphertext_base64") -> try { android.util.Base64.decode(json.getString("ciphertext_base64"), android.util.Base64.DEFAULT) } catch (_: Exception) { ByteArray(0) }
            json.has("ciphertext_hex") -> try { HashingEngine.hexToBytes(json.getString("ciphertext_hex")) } catch (_: Exception) { ByteArray(0) }
            else -> ByteArray(0)
        }

        val aad = json.optString("aad_text", json.optString("associated_data", ""))
        val timestamp = json.optLong("created_at", json.optLong("timestamp_epoch_ms", 0L))

        val expectedNonceLen = if (algorithm.contains("XChaCha20", ignoreCase = true)) 24 else 12
        val isNonceValid = nonceBytes.size == expectedNonceLen
        val isTagValid = tagBytes.size == 16

        val dateStr = if (timestamp > 0) {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
        } else "Unknown Date"

        val status = if (isNonceValid && isTagValid && cipherBytes.isNotEmpty()) {
            "Verified Authentic Container Structure"
        } else {
            "Structural Anomaly Detected (Nonce/Tag mismatch)"
        }

        return PackageEnvelopeInspection(
            isRecognizedPackage = true,
            formatType = formatType,
            version = version,
            algorithm = algorithm,
            nonceByteCount = nonceBytes.size,
            isNonceValid = isNonceValid,
            tagByteCount = tagBytes.size,
            isTagValid = isTagValid,
            ciphertextByteCount = cipherBytes.size,
            hasAad = aad.isNotEmpty(),
            timestampFormatted = dateStr,
            structuralIntegrityStatus = status
        )
    }

    private fun unrecognizedPackage(reason: String) = PackageEnvelopeInspection(
        isRecognizedPackage = false,
        formatType = "Unrecognized",
        version = "",
        algorithm = "",
        nonceByteCount = 0,
        isNonceValid = false,
        tagByteCount = 0,
        isTagValid = false,
        ciphertextByteCount = 0,
        hasAad = false,
        timestampFormatted = "",
        structuralIntegrityStatus = reason
    )

    /**
     * Triages decryption failures and produces user-actionable diagnostics.
     */
    fun triageDecryptionFailure(error: Throwable, keyBytesLen: Int?): String {
        val msg = error.message?.lowercase() ?: ""
        return when {
            keyBytesLen != null && keyBytesLen != 32 -> {
                "Key Size Error: Provided key is $keyBytesLen bytes (${keyBytesLen * 8}-bit). AEAD ciphers require exactly 32 bytes (256-bit)."
            }
            msg.contains("mac check in gcm failed") || msg.contains("poly1305") || msg.contains("tag mismatch") || msg.contains("bad padding") -> {
                "Integrity Authentication Failure: The poly1305 authentication tag failed verification. Either the key is incorrect, or the ciphertext / associated data was altered."
            }
            msg.contains("bad base64") || msg.contains("illegal base64") -> {
                "Encoding Error: Ciphertext or nonce contains invalid Base64 characters."
            }
            msg.contains("nonce") -> {
                "Nonce Error: Initialization vector / nonce does not match the cipher specification (expected 192-bit for XChaCha20, 96-bit for AES-GCM)."
            }
            else -> "Decryption failed: ${error.message ?: "Authentication tag or key error"}"
        }
    }
}
