package com.example.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.model.SymmetricKeyMeta
import com.example.security.HashingEngine
import com.example.security.HmacAlgorithm
import com.example.security.HmacEngine
import com.example.security.HmacResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HmacLabUiState(
    // Calculate Tab
    val algorithm: HmacAlgorithm = HmacAlgorithm.HMAC_SHA256,
    val messageInput: String = "Hello VeriCrypt! Authenticated message integrity payload.",
    val keyInput: String = "",
    val isKeyHex: Boolean = true,
    val activeKeyAlias: String? = null,
    val computedMacHex: String = "",
    val computedMacBase64: String = "",
    val webhookSignature: String = "",
    val isComputing: Boolean = false,
    val calculateError: String? = null,

    // Verify Tab
    val verifyMessageInput: String = "Hello VeriCrypt! Authenticated message integrity payload.",
    val verifyKeyInput: String = "",
    val isVerifyKeyHex: Boolean = true,
    val verifyTargetMac: String = "",
    val isVerifying: Boolean = false,
    val verificationResult: Boolean? = null,
    val verificationDiagnostic: String? = null
)

class HmacLabViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val keyDao = db.symmetricKeyDao()

    val savedKeys: StateFlow<List<SymmetricKeyMeta>> = keyDao.getAllSymmetricKeys()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(HmacLabUiState())
    val uiState: StateFlow<HmacLabUiState> = _uiState.asStateFlow()

    init {
        generateRandomKey()
    }

    fun setAlgorithm(algorithm: HmacAlgorithm) {
        _uiState.update { it.copy(algorithm = algorithm) }
        generateRandomKey()
    }

    fun setMessageInput(message: String) {
        _uiState.update { it.copy(messageInput = message) }
        calculateHmac()
    }

    fun setKeyInput(key: String, isHex: Boolean = true) {
        _uiState.update { it.copy(keyInput = key, isKeyHex = isHex, activeKeyAlias = null) }
        calculateHmac()
    }

    fun generateRandomKey() {
        val keyBytes = HmacEngine.generateRandomKey(_uiState.value.algorithm)
        val keyHex = keyBytes.joinToString("") { "%02x".format(it) }
        _uiState.update {
            it.copy(
                keyInput = keyHex,
                isKeyHex = true,
                activeKeyAlias = null,
                verifyKeyInput = keyHex,
                isVerifyKeyHex = true
            )
        }
        calculateHmac()
    }

    fun selectKeyFromKeyRing(keyMeta: SymmetricKeyMeta) {
        _uiState.update {
            it.copy(
                keyInput = keyMeta.keyHex,
                isKeyHex = true,
                activeKeyAlias = keyMeta.alias,
                verifyKeyInput = keyMeta.keyHex,
                isVerifyKeyHex = true
            )
        }
        calculateHmac()
    }

    fun calculateHmac() {
        val state = _uiState.value
        val rawKey = state.keyInput.trim()
        val rawMessage = state.messageInput

        if (rawKey.isEmpty()) {
            _uiState.update { it.copy(calculateError = "Key cannot be empty", computedMacHex = "", computedMacBase64 = "") }
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val keyBytes = if (state.isKeyHex && rawKey.matches(Regex("^[0-9a-fA-F]+$")) && rawKey.length % 2 == 0) {
                    rawKey.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                } else {
                    rawKey.toByteArray(Charsets.UTF_8)
                }

                val dataBytes = rawMessage.toByteArray(Charsets.UTF_8)
                val result: HmacResult = HmacEngine.computeHmac(state.algorithm, keyBytes, dataBytes)
                val webhook = HmacEngine.formatAsWebhookSignature(state.algorithm, result.macHex)

                _uiState.update {
                    it.copy(
                        computedMacHex = result.macHex,
                        computedMacBase64 = result.macBase64,
                        webhookSignature = webhook,
                        calculateError = null,
                        verifyTargetMac = if (it.verifyTargetMac.isEmpty()) result.macHex else it.verifyTargetMac
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        calculateError = "HMAC Computation failed: ${e.message}",
                        computedMacHex = "",
                        computedMacBase64 = ""
                    )
                }
            }
        }
    }

    // Verify Tab Actions
    fun setVerifyMessage(message: String) {
        _uiState.update { it.copy(verifyMessageInput = message, verificationResult = null) }
    }

    fun setVerifyKey(key: String, isHex: Boolean = true) {
        _uiState.update { it.copy(verifyKeyInput = key, isVerifyKeyHex = isHex, verificationResult = null) }
    }

    fun setVerifyTargetMac(targetMac: String) {
        _uiState.update { it.copy(verifyTargetMac = targetMac, verificationResult = null) }
    }

    fun copyCurrentCalculatedToVerify() {
        val current = _uiState.value
        _uiState.update {
            it.copy(
                verifyMessageInput = current.messageInput,
                verifyKeyInput = current.keyInput,
                isVerifyKeyHex = current.isKeyHex,
                verifyTargetMac = current.computedMacHex,
                verificationResult = null,
                verificationDiagnostic = null
            )
        }
    }

    fun verifyIntegrity() {
        val state = _uiState.value
        val rawKey = state.verifyKeyInput.trim()
        val rawMessage = state.verifyMessageInput
        val rawMac = state.verifyTargetMac.trim().removePrefix("sha256=").removePrefix("sha512=").removePrefix("sha384=")

        if (rawKey.isEmpty() || rawMac.isEmpty()) {
            _uiState.update {
                it.copy(
                    verificationResult = false,
                    verificationDiagnostic = "Both Secret Key and Candidate MAC are required for verification"
                )
            }
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val keyBytes = if (state.isVerifyKeyHex && rawKey.matches(Regex("^[0-9a-fA-F]+$")) && rawKey.length % 2 == 0) {
                    rawKey.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                } else {
                    rawKey.toByteArray(Charsets.UTF_8)
                }

                val dataBytes = rawMessage.toByteArray(Charsets.UTF_8)
                val expectedResult = HmacEngine.computeHmac(state.algorithm, keyBytes, dataBytes)
                val isValid = HmacEngine.verifyHmac(expectedResult.macBytes, rawMac)

                val diagnostic = if (isValid) {
                    "AUTHENTIC: Constant-time comparison passed. Message integrity and key authenticity verified 100%."
                } else {
                    "TAMPERED / FORGED: MAC signature does not match expected digest. Either message data was modified in transit or the secret key is invalid."
                }

                _uiState.update {
                    it.copy(
                        verificationResult = isValid,
                        verificationDiagnostic = diagnostic
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        verificationResult = false,
                        verificationDiagnostic = "Verification check failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun saveKeyToKeyRing(alias: String, note: String, onComplete: () -> Unit) {
        val state = _uiState.value
        val keyHex = state.keyInput.trim()
        if (alias.isBlank() || keyHex.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            val keyBytes = try {
                if (state.isKeyHex && keyHex.matches(Regex("^[0-9a-fA-F]+$"))) {
                    keyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                } else {
                    keyHex.toByteArray(Charsets.UTF_8)
                }
            } catch (_: Exception) {
                keyHex.toByteArray(Charsets.UTF_8)
            }

            val fingerprint = HashingEngine.computeSha256Bytes(keyBytes)

            val meta = SymmetricKeyMeta(
                alias = alias.trim(),
                algorithm = state.algorithm.displayName,
                keyHex = keyHex,
                fingerprintSha256 = fingerprint,
                note = note.trim()
            )
            keyDao.insertSymmetricKey(meta)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(activeKeyAlias = alias.trim()) }
                onComplete()
            }
        }
    }
}
