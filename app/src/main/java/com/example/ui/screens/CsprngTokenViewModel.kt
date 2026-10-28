package com.example.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.security.CsprngTokenEngine
import com.example.security.GeneratedTokenResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class GeneratorMode {
    DICEWARE_PASSPHRASE,
    ALPHANUMERIC_PASSWORD,
    HEX_SECRET,
    BASE64_TOKEN
}

data class CsprngTokenUiState(
    val mode: GeneratorMode = GeneratorMode.DICEWARE_PASSPHRASE,
    val dicewareWords: Int = 5,
    val dicewareSeparator: String = "-",
    val passwordLength: Int = 20,
    val includeUpper: Boolean = true,
    val includeLower: Boolean = true,
    val includeDigits: Boolean = true,
    val includeSymbols: Boolean = true,
    val hexByteLength: Int = 32,
    val currentResult: GeneratedTokenResult? = null
)

class CsprngTokenViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CsprngTokenUiState())
    val uiState: StateFlow<CsprngTokenUiState> = _uiState.asStateFlow()

    init {
        generateToken()
    }

    fun setMode(mode: GeneratorMode) {
        _uiState.update { it.copy(mode = mode) }
        generateToken()
    }

    fun setDicewareWords(count: Int) {
        _uiState.update { it.copy(dicewareWords = count.coerceIn(3, 8)) }
        generateToken()
    }

    fun setDicewareSeparator(sep: String) {
        _uiState.update { it.copy(dicewareSeparator = sep) }
        generateToken()
    }

    fun setPasswordLength(length: Int) {
        _uiState.update { it.copy(passwordLength = length.coerceIn(8, 64)) }
        generateToken()
    }

    fun setPasswordToggles(upper: Boolean, lower: Boolean, digits: Boolean, symbols: Boolean) {
        _uiState.update {
            it.copy(
                includeUpper = upper,
                includeLower = lower,
                includeDigits = digits,
                includeSymbols = symbols
            )
        }
        generateToken()
    }

    fun setHexByteLength(bytes: Int) {
        _uiState.update { it.copy(hexByteLength = bytes) }
        generateToken()
    }

    fun generateToken() {
        val state = _uiState.value
        val result = when (state.mode) {
            GeneratorMode.DICEWARE_PASSPHRASE -> CsprngTokenEngine.generatePassphrase(
                wordCount = state.dicewareWords,
                separator = state.dicewareSeparator
            )
            GeneratorMode.ALPHANUMERIC_PASSWORD -> CsprngTokenEngine.generatePassword(
                length = state.passwordLength,
                includeUpper = state.includeUpper,
                includeLower = state.includeLower,
                includeDigits = state.includeDigits,
                includeSymbols = state.includeSymbols
            )
            GeneratorMode.HEX_SECRET -> CsprngTokenEngine.generateHexToken(state.hexByteLength)
            GeneratorMode.BASE64_TOKEN -> CsprngTokenEngine.generateBase64Token(state.hexByteLength)
        }

        _uiState.update { it.copy(currentResult = result) }
    }
}
