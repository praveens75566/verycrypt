package com.example.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.model.SymmetricKeyMeta
import com.example.security.HashingEngine
import com.example.security.KdfEngine
import com.example.security.KdfResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class KdfSandboxUiState(
    val passphraseInput: String = "CorrectHorseBatteryStaple",
    val saltHex: String = "",
    val iterations: Int = 100_000,
    val keyLengthBits: Int = 256,
    val derivedKeyHex: String = "",
    val derivedKeyBase64: String = "",
    val executionTimeMs: Long = 0L,
    val isDeriving: Boolean = false,
    val error: String? = null,
    val isSavedToKeyRing: Boolean = false
)

class KdfSandboxViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val keyDao = db.symmetricKeyDao()

    val savedKeys: StateFlow<List<SymmetricKeyMeta>> = keyDao.getAllSymmetricKeys()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(KdfSandboxUiState())
    val uiState: StateFlow<KdfSandboxUiState> = _uiState.asStateFlow()

    init {
        generateRandomSalt()
    }

    fun setPassphrase(passphrase: String) {
        _uiState.update { it.copy(passphraseInput = passphrase, isSavedToKeyRing = false) }
    }

    fun setSaltHex(salt: String) {
        _uiState.update { it.copy(saltHex = salt, isSavedToKeyRing = false) }
    }

    fun setIterations(iterations: Int) {
        _uiState.update { it.copy(iterations = iterations, isSavedToKeyRing = false) }
    }

    fun setKeyLengthBits(bits: Int) {
        _uiState.update { it.copy(keyLengthBits = bits, isSavedToKeyRing = false) }
    }

    fun generateRandomSalt(lengthBytes: Int = 16) {
        val salt = KdfEngine.generateSalt(lengthBytes)
        val saltHex = salt.joinToString("") { "%02x".format(it) }
        _uiState.update { it.copy(saltHex = saltHex, isSavedToKeyRing = false) }
        deriveKey()
    }

    fun deriveKey() {
        val state = _uiState.value
        val rawPass = state.passphraseInput
        val rawSalt = state.saltHex.trim()

        if (rawPass.isEmpty()) {
            _uiState.update { it.copy(error = "Passphrase cannot be empty") }
            return
        }

        val saltBytes = try {
            if (rawSalt.matches(Regex("^[0-9a-fA-F]+$")) && rawSalt.length % 2 == 0) {
                rawSalt.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            } else {
                rawSalt.toByteArray(Charsets.UTF_8)
            }
        } catch (_: Exception) {
            rawSalt.toByteArray(Charsets.UTF_8)
        }

        if (saltBytes.isEmpty()) {
            _uiState.update { it.copy(error = "Salt cannot be empty") }
            return
        }

        _uiState.update { it.copy(isDeriving = true, error = null) }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val result: KdfResult = KdfEngine.deriveKey(
                    passphrase = rawPass.toCharArray(),
                    salt = saltBytes,
                    iterations = state.iterations,
                    keyLengthBits = state.keyLengthBits
                )

                _uiState.update {
                    it.copy(
                        derivedKeyHex = result.keyHex,
                        derivedKeyBase64 = result.keyBase64,
                        executionTimeMs = result.executionTimeMs,
                        isDeriving = false,
                        error = null,
                        isSavedToKeyRing = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDeriving = false,
                        error = "PBKDF2 derivation failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun saveDerivedKeyToKeyRing(alias: String, onComplete: () -> Unit) {
        val state = _uiState.value
        val hex = state.derivedKeyHex
        if (alias.isBlank() || hex.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            val keyBytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val fingerprint = HashingEngine.computeSha256Bytes(keyBytes)
            val algoName = when (state.keyLengthBits) {
                256 -> "PBKDF2-Derived-256"
                128 -> "PBKDF2-Derived-128"
                else -> "PBKDF2-Derived-${state.keyLengthBits}"
            }

            val meta = SymmetricKeyMeta(
                alias = alias.trim(),
                algorithm = algoName,
                keyHex = hex,
                fingerprintSha256 = fingerprint,
                note = "Derived via PBKDF2 (${state.iterations} iterations, salt: ${state.saltHex.take(8)}...)"
            )

            keyDao.insertSymmetricKey(meta)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(isSavedToKeyRing = true) }
                onComplete()
            }
        }
    }
}
