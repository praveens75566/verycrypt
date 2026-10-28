package com.example.ui.screens

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.model.AuditAction
import com.example.data.model.AuditEvent
import com.example.data.model.AuditStatus
import com.example.security.CryptoAuthenticationException
import com.example.security.CryptoEngine
import com.example.security.DecryptionResult
import com.example.security.EncryptionResult
import com.example.security.EntropyEngine
import com.example.security.EntropyResult
import com.example.security.HashingEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EncryptUiState(
    val selectedUri: Uri? = null,
    val fileName: String = "",
    val sizeBytes: Long = 0L,
    val isEncrypting: Boolean = false,
    val progress: Float = 0f,
    val result: EncryptionResult? = null,
    val error: String? = null
)

data class DecryptUiState(
    val selectedUri: Uri? = null,
    val fileName: String = "",
    val sizeBytes: Long = 0L,
    val isDecrypting: Boolean = false,
    val progress: Float = 0f,
    val result: DecryptionResult? = null,
    val error: String? = null,
    val isTamperDetected: Boolean = false
)

data class TextEncryptState(
    val plainTextInput: String = "",
    val passwordInput: String = "",
    val isEncrypting: Boolean = false,
    val armoredOutput: String = "",
    val entropyResult: EntropyResult? = null,
    val selectedAlgorithm: com.example.security.CipherAlgorithm = com.example.security.CipherAlgorithm.AES_256_GCM,
    val error: String? = null
)

data class TextDecryptState(
    val armoredInput: String = "",
    val passwordInput: String = "",
    val isDecrypting: Boolean = false,
    val plainTextOutput: String = "",
    val isTamperDetected: Boolean = false,
    val error: String? = null
)

class CryptoViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val context = application.applicationContext

    private val _encryptState = MutableStateFlow(EncryptUiState())
    val encryptState: StateFlow<EncryptUiState> = _encryptState.asStateFlow()

    private val _decryptState = MutableStateFlow(DecryptUiState())
    val decryptState: StateFlow<DecryptUiState> = _decryptState.asStateFlow()

    private val _textEncryptState = MutableStateFlow(TextEncryptState())
    val textEncryptState: StateFlow<TextEncryptState> = _textEncryptState.asStateFlow()

    private val _textDecryptState = MutableStateFlow(TextDecryptState())
    val textDecryptState: StateFlow<TextDecryptState> = _textDecryptState.asStateFlow()

    fun selectFileForEncryption(uri: Uri) {
        val (name, size) = HashingEngine.getUriMetadata(context, uri)
        _encryptState.update {
            it.copy(
                selectedUri = uri,
                fileName = name,
                sizeBytes = size,
                result = null,
                error = null,
                progress = 0f
            )
        }
    }

    fun selectFileForDecryption(uri: Uri) {
        val (name, size) = HashingEngine.getUriMetadata(context, uri)
        _decryptState.update {
            it.copy(
                selectedUri = uri,
                fileName = name,
                sizeBytes = size,
                result = null,
                error = null,
                progress = 0f,
                isTamperDetected = false
            )
        }
    }

    fun encryptToFile(
        sourceUri: Uri,
        destinationUri: Uri,
        password: String
    ) {
        viewModelScope.launch {
            _encryptState.update { it.copy(isEncrypting = true, error = null, progress = 0f) }
            val (origName, size) = HashingEngine.getUriMetadata(context, sourceUri)

            try {
                val inputStream = context.contentResolver.openInputStream(sourceUri)
                    ?: throw IllegalStateException("Cannot open input stream for $origName")
                val outputStream = context.contentResolver.openOutputStream(destinationUri)
                    ?: throw IllegalStateException("Cannot open output stream for destination")

                val result = CryptoEngine.encryptStream(
                    inputStream = inputStream,
                    outputStream = outputStream,
                    originalFileName = origName,
                    password = password.toCharArray(),
                    onProgress = { prog -> _encryptState.update { it.copy(progress = prog) } },
                    totalBytes = size
                )

                _encryptState.update {
                    it.copy(
                        isEncrypting = false,
                        result = result,
                        progress = 1f
                    )
                }

                db.auditDao().insertEvent(
                    AuditEvent(
                        action = AuditAction.ENCRYPT,
                        itemName = origName,
                        status = AuditStatus.SUCCESS,
                        details = "AES-256-GCM encrypted. Salt: ${result.saltHex.take(8)}... IV: ${result.ivHex.take(8)}..."
                    )
                )
            } catch (e: Exception) {
                com.example.security.DiagnosticsEngine.recordError("CryptoViewModel", "Encryption failed for $origName: ${e.message}", e)
                _encryptState.update {
                    it.copy(isEncrypting = false, error = e.localizedMessage ?: "Encryption failed")
                }
                db.auditDao().insertEvent(
                    AuditEvent(
                        action = AuditAction.ENCRYPT,
                        itemName = origName,
                        status = AuditStatus.FAILED,
                        details = e.localizedMessage ?: "Unknown error"
                    )
                )
            }
        }
    }

    fun decryptToFile(
        sourceUri: Uri,
        destinationUri: Uri,
        password: String
    ) {
        viewModelScope.launch {
            _decryptState.update {
                it.copy(
                    isDecrypting = true,
                    error = null,
                    progress = 0f,
                    isTamperDetected = false
                )
            }
            val (sourceName, size) = HashingEngine.getUriMetadata(context, sourceUri)

            try {
                val inputStream = context.contentResolver.openInputStream(sourceUri)
                    ?: throw IllegalStateException("Cannot open encrypted file stream")
                val outputStream = context.contentResolver.openOutputStream(destinationUri)
                    ?: throw IllegalStateException("Cannot open destination file stream")

                val result = CryptoEngine.decryptStream(
                    inputStream = inputStream,
                    outputStream = outputStream,
                    password = password.toCharArray(),
                    onProgress = { prog -> _decryptState.update { it.copy(progress = prog) } },
                    totalBytes = size
                )

                _decryptState.update {
                    it.copy(
                        isDecrypting = false,
                        result = result,
                        progress = 1f
                    )
                }

                db.auditDao().insertEvent(
                    AuditEvent(
                        action = AuditAction.DECRYPT,
                        itemName = result.restoredFileName,
                        status = AuditStatus.SUCCESS,
                        details = "Decrypted authenticated container. Restored ${result.decryptedSizeBytes} bytes."
                    )
                )
            } catch (e: Exception) {
                com.example.security.DiagnosticsEngine.recordError("CryptoViewModel", "Decryption failed for $sourceName: ${e.message}", e)
                val isTamper = e is CryptoAuthenticationException ||
                        e.message?.contains("tamper", ignoreCase = true) == true ||
                        e.message?.contains("tag", ignoreCase = true) == true

                _decryptState.update {
                    it.copy(
                        isDecrypting = false,
                        isTamperDetected = isTamper,
                        error = if (isTamper) {
                            "Authentication Failed: Password incorrect or ciphertext has been modified/corrupted."
                        } else {
                            e.localizedMessage ?: "Decryption failed"
                        }
                    )
                }

                db.auditDao().insertEvent(
                    AuditEvent(
                        action = AuditAction.DECRYPT,
                        itemName = sourceName,
                        status = if (isTamper) AuditStatus.TAMPER_DETECTED else AuditStatus.FAILED,
                        details = if (isTamper) "AEAD Tag authentication failed." else (e.localizedMessage ?: "Error")
                    )
                )
            }
        }
    }

    // Direct Text Cryptography
    fun updateTextEncryptInput(text: String) {
        _textEncryptState.update { it.copy(plainTextInput = text, error = null) }
    }

    fun updateTextEncryptPassword(pass: String) {
        _textEncryptState.update { it.copy(passwordInput = pass, error = null) }
    }

    fun selectAlgorithm(algo: com.example.security.CipherAlgorithm) {
        _textEncryptState.update { it.copy(selectedAlgorithm = algo) }
    }

    fun encryptDirectText() {
        val text = _textEncryptState.value.plainTextInput
        val pass = _textEncryptState.value.passwordInput
        val algo = _textEncryptState.value.selectedAlgorithm
        if (text.isBlank() || pass.isBlank()) return

        viewModelScope.launch {
            _textEncryptState.update { it.copy(isEncrypting = true, error = null) }
            try {
                val armored = CryptoEngine.encryptText(text, pass, algo)
                val entropy = EntropyEngine.calculateTextEntropy(armored)
                _textEncryptState.update {
                    it.copy(
                        isEncrypting = false,
                        armoredOutput = armored,
                        entropyResult = entropy
                    )
                }
                db.auditDao().insertEvent(
                    AuditEvent(
                        action = AuditAction.ENCRYPT,
                        itemName = "Direct Text (${algo.shortName})",
                        status = AuditStatus.SUCCESS,
                        details = "Encrypted plain text via ${algo.displayName}."
                    )
                )
            } catch (e: Exception) {
                _textEncryptState.update {
                    it.copy(isEncrypting = false, error = e.localizedMessage ?: "Text encryption failed")
                }
            }
        }
    }

    fun updateTextDecryptInput(armored: String) {
        _textDecryptState.update { it.copy(armoredInput = armored, error = null, isTamperDetected = false) }
    }

    fun updateTextDecryptPassword(pass: String) {
        _textDecryptState.update { it.copy(passwordInput = pass, error = null, isTamperDetected = false) }
    }

    fun decryptDirectText() {
        val armored = _textDecryptState.value.armoredInput
        val pass = _textDecryptState.value.passwordInput
        if (armored.isBlank() || pass.isBlank()) return

        viewModelScope.launch {
            _textDecryptState.update {
                it.copy(isDecrypting = true, error = null, isTamperDetected = false)
            }
            try {
                val restored = CryptoEngine.decryptText(armored, pass)
                _textDecryptState.update {
                    it.copy(
                        isDecrypting = false,
                        plainTextOutput = restored
                    )
                }
                db.auditDao().insertEvent(
                    AuditEvent(
                        action = AuditAction.DECRYPT,
                        itemName = "Direct Text Snippet",
                        status = AuditStatus.SUCCESS,
                        details = "Decrypted armored message (${restored.length} chars restored)."
                    )
                )
            } catch (e: Exception) {
                val isTamper = e is CryptoAuthenticationException ||
                        e.message?.contains("tamper", ignoreCase = true) == true ||
                        e.message?.contains("tag", ignoreCase = true) == true

                _textDecryptState.update {
                    it.copy(
                        isDecrypting = false,
                        isTamperDetected = isTamper,
                        error = if (isTamper) {
                            "Authentication Failed: Password incorrect or ciphertext altered."
                        } else {
                            e.localizedMessage ?: "Decryption failed"
                        }
                    )
                }

                db.auditDao().insertEvent(
                    AuditEvent(
                        action = AuditAction.DECRYPT,
                        itemName = "Direct Text Snippet",
                        status = if (isTamper) AuditStatus.TAMPER_DETECTED else AuditStatus.FAILED,
                        details = if (isTamper) "AEAD Bad Tag." else (e.localizedMessage ?: "Failed")
                    )
                )
            }
        }
    }

    fun calculatePasswordEntropy(password: String): Int {
        if (password.isEmpty()) return 0
        var pool = 0
        if (password.any { it.isLowerCase() }) pool += 26
        if (password.any { it.isUpperCase() }) pool += 26
        if (password.any { it.isDigit() }) pool += 10
        if (password.any { !it.isLetterOrDigit() }) pool += 32
        val entropy = (password.length * (Math.log(pool.toDouble()) / Math.log(2.0))).toInt()
        return entropy.coerceIn(0, 100)
    }
}
