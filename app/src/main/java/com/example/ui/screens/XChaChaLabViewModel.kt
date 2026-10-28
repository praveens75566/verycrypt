package com.example.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.model.SymmetricKeyMeta
import com.example.security.CryptoAuthenticationException
import com.example.security.HashingEngine
import com.example.security.VeriCryptPackage
import com.example.security.XChaCha20Poly1305Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NonceCollisionStats(
    val standard96BitBirthdayBound: String = "2^48 messages (~2.81 × 10^14) - Danger of collision if generated randomly at scale",
    val xChaCha192BitBirthdayBound: String = "2^96 messages (~7.92 × 10^28) - Safe for trillions of random nonces without reuse risk",
    val securityAdvantage: String = "XChaCha20 extends the nonce by 96 bits, eliminating stateful nonce counters and safe for multi-threaded distributed encryption."
)

data class XChaChaLabUiState(
    // Encryption State
    val secretKeyHex: String = "",
    val activeKeyAlias: String? = null,
    val nonceHex: String = "",
    val aadInput: String = "Sender: VeriCrypt Client v1.0",
    val plainTextInput: String = "Confidential telemetry packet authenticated with Poly1305.",
    val cipherTextHex: String = "",
    val cipherTextBase64: String = "",
    val tagHex: String = "",
    val armoredOutput: String = "",
    val jsonPackageOutput: String = "",
    val pipelineSteps: List<XChaCha20Poly1305Engine.CryptoPipelineStep> = emptyList(),
    val isEncrypting: Boolean = false,
    val tamperStatus: String? = null,
    val isTamperRejected: Boolean? = null,
    val collisionStats: NonceCollisionStats = NonceCollisionStats(),
    val error: String? = null,

    // Decryption State
    val decryptInputText: String = "",
    val decryptKeyHex: String = "",
    val decryptKeyAlias: String? = null,
    val decryptNonceHex: String = "",
    val decryptTagHex: String = "",
    val decryptCiphertextHex: String = "",
    val decryptAadInput: String = "",
    val decryptedPlaintext: String = "",
    val isDecrypting: Boolean = false,
    val decryptSuccess: Boolean? = null,
    val decryptError: String? = null,
    val decryptKeyHint: String? = null
)

class XChaChaLabViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val keyDao = db.symmetricKeyDao()

    val savedKeys: StateFlow<List<SymmetricKeyMeta>> = keyDao.getAllSymmetricKeys()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(XChaChaLabUiState())
    val uiState: StateFlow<XChaChaLabUiState> = _uiState.asStateFlow()

    init {
        initDefaultKeysAndEncrypt()
    }

    fun initDefaultKeysAndEncrypt() {
        val key = XChaCha20Poly1305Engine.generateSecureKey()
        val nonce = XChaCha20Poly1305Engine.generateExtendedNonce()
        val keyHex = HashingEngine.bytesToHex(key)
        _uiState.update {
            it.copy(
                secretKeyHex = keyHex,
                decryptKeyHex = keyHex,
                nonceHex = HashingEngine.bytesToHex(nonce)
            )
        }
        encrypt()
    }

    fun generateNewKey() {
        val key = XChaCha20Poly1305Engine.generateSecureKey()
        val hex = HashingEngine.bytesToHex(key)
        _uiState.update {
            it.copy(
                secretKeyHex = hex,
                activeKeyAlias = null,
                decryptKeyHex = hex,
                tamperStatus = null
            )
        }
        encrypt()
    }

    fun generateNewNonce() {
        val nonce = XChaCha20Poly1305Engine.generateExtendedNonce()
        _uiState.update { it.copy(nonceHex = HashingEngine.bytesToHex(nonce), tamperStatus = null) }
        encrypt()
    }

    fun updatePlainText(text: String) {
        _uiState.update { it.copy(plainTextInput = text, tamperStatus = null) }
        encrypt()
    }

    fun updateAad(aad: String) {
        _uiState.update { it.copy(aadInput = aad, tamperStatus = null) }
        encrypt()
    }

    fun selectKeyForEncryption(keyMeta: SymmetricKeyMeta) {
        _uiState.update {
            it.copy(
                secretKeyHex = keyMeta.keyHex,
                activeKeyAlias = keyMeta.alias,
                decryptKeyHex = keyMeta.keyHex,
                decryptKeyAlias = keyMeta.alias
            )
        }
        encrypt()
    }

    fun selectKeyForDecryption(keyMeta: SymmetricKeyMeta) {
        _uiState.update {
            it.copy(
                decryptKeyHex = keyMeta.keyHex,
                decryptKeyAlias = keyMeta.alias
            )
        }
    }

    fun setCustomSecretKeyHex(hex: String) {
        _uiState.update { it.copy(secretKeyHex = hex.trim(), activeKeyAlias = null) }
        if (hex.trim().length == 64) {
            encrypt()
        }
    }

    fun setCustomDecryptKeyHex(hex: String) {
        _uiState.update { it.copy(decryptKeyHex = hex.trim(), decryptKeyAlias = null) }
    }

    fun saveKeyToRing(alias: String, keyHex: String, note: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            if (alias.isBlank() || keyHex.length != 64) return@launch
            val fingerprint = try {
                val sha = HashingEngine.computeSha256Bytes(HashingEngine.hexToBytes(keyHex))
                sha.take(16)
            } catch (_: Exception) {
                keyHex.take(16)
            }
            val meta = SymmetricKeyMeta(
                alias = alias.trim(),
                algorithm = "XChaCha20-Poly1305",
                keyHex = keyHex.trim(),
                fingerprintSha256 = fingerprint,
                note = note.trim()
            )
            keyDao.insertSymmetricKey(meta)
            _uiState.update {
                if (it.secretKeyHex.equals(keyHex, ignoreCase = true)) {
                    it.copy(activeKeyAlias = alias.trim())
                } else it
            }
        }
    }

    fun deleteKeyFromRing(keyMeta: SymmetricKeyMeta) {
        viewModelScope.launch(Dispatchers.IO) {
            keyDao.deleteSymmetricKey(keyMeta)
            _uiState.update {
                if (it.activeKeyAlias == keyMeta.alias) it.copy(activeKeyAlias = null)
                else it
            }
        }
    }

    fun encrypt() {
        viewModelScope.launch {
            val currentState = _uiState.value
            try {
                val key = HashingEngine.hexToBytes(currentState.secretKeyHex)
                val nonce = HashingEngine.hexToBytes(currentState.nonceHex)
                if (key.size != 32 || nonce.size != 24) return@launch

                val plaintext = currentState.plainTextInput.toByteArray(Charsets.UTF_8)
                val aad = if (currentState.aadInput.isNotBlank()) currentState.aadInput.toByteArray(Charsets.UTF_8) else null

                val encResult = withContext(Dispatchers.Default) {
                    XChaCha20Poly1305Engine.encrypt(
                        plaintext = plaintext,
                        key = key,
                        nonce = nonce,
                        aad = aad,
                        enforceNonceCheck = false
                    )
                }

                val pipeline = withContext(Dispatchers.Default) {
                    XChaCha20Poly1305Engine.tracePipeline(
                        plaintext = plaintext,
                        key = key,
                        nonce = nonce,
                        aad = aad
                    )
                }

                val cipherHex = HashingEngine.bytesToHex(encResult.ciphertext)
                val cipherB64 = android.util.Base64.encodeToString(encResult.ciphertext, android.util.Base64.NO_WRAP)
                val tagHex = HashingEngine.bytesToHex(encResult.tag)
                val nonceHex = currentState.nonceHex

                val fingerprint = try {
                    HashingEngine.computeSha256Bytes(key).take(16)
                } catch (_: Exception) {
                    ""
                }

                val pkg = VeriCryptPackage(
                    algorithm = "XChaCha20-Poly1305",
                    keyFingerprint = fingerprint,
                    nonceHex = nonceHex,
                    nonceBits = 192,
                    tagHex = tagHex,
                    tagBits = 128,
                    aadText = currentState.aadInput,
                    ciphertextHex = cipherHex,
                    ciphertextBase64 = cipherB64
                )

                _uiState.update {
                    it.copy(
                        cipherTextHex = cipherHex,
                        cipherTextBase64 = cipherB64,
                        tagHex = tagHex,
                        armoredOutput = encResult.toArmoredString(),
                        jsonPackageOutput = pkg.toJsonString(pretty = true),
                        pipelineSteps = pipeline,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.localizedMessage ?: "Encryption failed") }
            }
        }
    }

    fun testTamper(target: XChaCha20Poly1305Engine.TamperTarget) {
        viewModelScope.launch {
            val currentState = _uiState.value
            try {
                val key = HashingEngine.hexToBytes(currentState.secretKeyHex)
                val nonce = HashingEngine.hexToBytes(currentState.nonceHex)
                val plaintext = currentState.plainTextInput.toByteArray(Charsets.UTF_8)

                val result = withContext(Dispatchers.Default) {
                    XChaCha20Poly1305Engine.testTamperResistance(
                        plaintext = plaintext,
                        key = key,
                        nonce = nonce,
                        corruptTarget = target
                    )
                }

                _uiState.update {
                    it.copy(
                        tamperStatus = result.message,
                        isTamperRejected = result.rejected
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        tamperStatus = "Tamper test aborted: ${e.message}",
                        isTamperRejected = false
                    )
                }
            }
        }
    }

    // --- DECRYPTION METHODS ---

    fun updateDecryptInputText(text: String) {
        _uiState.update { it.copy(decryptInputText = text, decryptError = null) }
        tryAutoParsePackage(text)
    }

    fun updateDecryptNonceHex(nonce: String) {
        _uiState.update { it.copy(decryptNonceHex = nonce.trim(), decryptError = null) }
    }

    fun updateDecryptTagHex(tag: String) {
        _uiState.update { it.copy(decryptTagHex = tag.trim(), decryptError = null) }
    }

    fun updateDecryptCiphertextHex(cipher: String) {
        _uiState.update { it.copy(decryptCiphertextHex = cipher.trim(), decryptError = null) }
    }

    fun updateDecryptAad(aad: String) {
        _uiState.update { it.copy(decryptAadInput = aad, decryptError = null) }
    }

    fun loadCurrentEncryptionIntoDecrypt() {
        val current = _uiState.value
        _uiState.update {
            it.copy(
                decryptInputText = current.jsonPackageOutput,
                decryptKeyHex = current.secretKeyHex,
                decryptKeyAlias = current.activeKeyAlias,
                decryptNonceHex = current.nonceHex,
                decryptTagHex = current.tagHex,
                decryptCiphertextHex = current.cipherTextHex,
                decryptAadInput = current.aadInput,
                decryptKeyHint = if (current.secretKeyHex.length == 64) HashingEngine.computeSha256Bytes(HashingEngine.hexToBytes(current.secretKeyHex)).take(16) else null,
                decryptedPlaintext = "",
                decryptSuccess = null,
                decryptError = null
            )
        }
    }

    fun importPackageString(raw: String) {
        try {
            val pkg = VeriCryptPackage.parseAuto(raw)
            _uiState.update {
                it.copy(
                    decryptInputText = raw,
                    decryptNonceHex = pkg.nonceHex,
                    decryptTagHex = pkg.tagHex,
                    decryptCiphertextHex = pkg.ciphertextHex.ifEmpty {
                        try {
                            val b = android.util.Base64.decode(pkg.ciphertextBase64, android.util.Base64.NO_WRAP)
                            HashingEngine.bytesToHex(b)
                        } catch (_: Exception) { "" }
                    },
                    decryptAadInput = pkg.aadText,
                    decryptKeyHint = pkg.keyFingerprint.ifBlank { null },
                    decryptError = null
                )
            }

            // Check if we have a saved key matching the fingerprint
            if (pkg.keyFingerprint.isNotBlank()) {
                val matching = savedKeys.value.firstOrNull { it.fingerprintSha256.equals(pkg.keyFingerprint, ignoreCase = true) }
                if (matching != null) {
                    _uiState.update {
                        it.copy(
                            decryptKeyHex = matching.keyHex,
                            decryptKeyAlias = matching.alias
                        )
                    }
                }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(decryptError = "Failed to parse package: ${e.message}") }
        }
    }

    private fun tryAutoParsePackage(text: String) {
        val trimmed = text.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("-----BEGIN")) {
            try {
                importPackageString(trimmed)
            } catch (_: Exception) {}
        }
    }

    fun executeDecryption() {
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.update { it.copy(isDecrypting = true, decryptError = null, decryptSuccess = null) }

            try {
                val keyBytes = HashingEngine.hexToBytes(state.decryptKeyHex.trim())
                if (keyBytes.size != 32) {
                    throw IllegalArgumentException("Decryption key must be exactly 32 bytes (64 hex characters)")
                }

                // If input text contains full package/armored text, prefer armored or package decrypt
                val inputText = state.decryptInputText.trim()
                val decryptedBytes = withContext(Dispatchers.Default) {
                    if (state.decryptNonceHex.isNotBlank() && state.decryptTagHex.isNotBlank() && state.decryptCiphertextHex.isNotBlank()) {
                        val nonceBytes = HashingEngine.hexToBytes(state.decryptNonceHex.trim())
                        val tagBytes = HashingEngine.hexToBytes(state.decryptTagHex.trim())
                        val cipherBytes = HashingEngine.hexToBytes(state.decryptCiphertextHex.trim())
                        val aadBytes = if (state.decryptAadInput.isNotBlank()) state.decryptAadInput.toByteArray(Charsets.UTF_8) else null

                        XChaCha20Poly1305Engine.decrypt(
                            ciphertext = cipherBytes,
                            key = keyBytes,
                            nonce = nonceBytes,
                            tag = tagBytes,
                            aad = aadBytes
                        )
                    } else if (inputText.contains("-----BEGIN") || inputText.startsWith("{")) {
                        val pkg = VeriCryptPackage.parseAuto(inputText)
                        val nonceBytes = HashingEngine.hexToBytes(pkg.nonceHex)
                        val tagBytes = HashingEngine.hexToBytes(pkg.tagHex)
                        val cipherBytes = if (pkg.ciphertextHex.isNotBlank()) {
                            HashingEngine.hexToBytes(pkg.ciphertextHex)
                        } else {
                            android.util.Base64.decode(pkg.ciphertextBase64, android.util.Base64.NO_WRAP)
                        }
                        val aadBytes = if (pkg.aadText.isNotBlank()) pkg.aadText.toByteArray(Charsets.UTF_8) else null

                        XChaCha20Poly1305Engine.decrypt(
                            ciphertext = cipherBytes,
                            key = keyBytes,
                            nonce = nonceBytes,
                            tag = tagBytes,
                            aad = aadBytes
                        )
                    } else {
                        throw IllegalArgumentException("Please provide Nonce (24 bytes), Poly1305 Tag (16 bytes), and Ciphertext, or paste an armored/JSON package.")
                    }
                }

                val plaintextStr = String(decryptedBytes, Charsets.UTF_8)

                _uiState.update {
                    it.copy(
                        isDecrypting = false,
                        decryptedPlaintext = plaintextStr,
                        decryptSuccess = true,
                        decryptError = null
                    )
                }
            } catch (authEx: CryptoAuthenticationException) {
                _uiState.update {
                    it.copy(
                        isDecrypting = false,
                        decryptSuccess = false,
                        decryptError = "AUTHENTICATION FAILED: Poly1305 tag mismatch. The ciphertext has been modified, tampered with, or the key is incorrect. Decryption rejected."
                    )
                }
            } catch (e: Exception) {
                val keyLen = try { HashingEngine.hexToBytes(state.decryptKeyHex.trim()).size } catch (_: Exception) { null }
                val diagnostic = com.example.security.CryptoDiagnostics.triageDecryptionFailure(e, keyLen)
                _uiState.update {
                    it.copy(
                        isDecrypting = false,
                        decryptSuccess = false,
                        decryptError = diagnostic
                    )
                }
            }
        }
    }
}
