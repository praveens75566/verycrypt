package com.example.ui.screens

import android.app.Application
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.model.AuditAction
import com.example.data.model.AuditEvent
import com.example.data.model.AuditStatus
import com.example.data.model.KeyPairMeta
import com.example.security.HashingEngine
import com.example.security.SignatureEngine
import com.example.security.SignatureResult
import com.example.security.VerificationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.PublicKey

data class SignUiState(
    val selectedFileUri: Uri? = null,
    val fileName: String = "",
    val sizeBytes: Long = 0L,
    val selectedAlias: String = "",
    val isSigning: Boolean = false,
    val signatureResult: SignatureResult? = null,
    val error: String? = null
)

data class VerifyUiState(
    val selectedFileUri: Uri? = null,
    val fileName: String = "",
    val signatureBase64: String = "",
    val publicKeyPem: String = "",
    val isVerifying: Boolean = false,
    val verificationResult: VerificationResult? = null,
    val error: String? = null
)

class SignatureViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val context = application.applicationContext

    val savedKeys: StateFlow<List<KeyPairMeta>> = db.keyPairDao().getAllKeyPairs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _signState = MutableStateFlow(SignUiState())
    val signState: StateFlow<SignUiState> = _signState.asStateFlow()

    private val _verifyState = MutableStateFlow(VerifyUiState())
    val verifyState: StateFlow<VerifyUiState> = _verifyState.asStateFlow()

    fun generateNewKeyPair(alias: String) {
        val cleanAlias = alias.trim()
        if (cleanAlias.isBlank()) return

        viewModelScope.launch {
            try {
                val pubKey = SignatureEngine.generateKeystoreKeyPair(cleanAlias)
                val pem = SignatureEngine.exportPublicKeyToPem(pubKey)
                val fingerprint = SignatureEngine.computePublicKeyFingerprint(pubKey)

                val meta = KeyPairMeta(
                    alias = cleanAlias,
                    algorithm = "ECDSA",
                    curve = "secp256r1 (NIST P-256)",
                    publicKeyPem = pem,
                    fingerprintSha256 = fingerprint,
                    isKeystoreBacked = true
                )

                db.keyPairDao().insertKeyPair(meta)
                db.auditDao().insertEvent(
                    AuditEvent(
                        action = AuditAction.KEY_GENERATE,
                        itemName = cleanAlias,
                        status = AuditStatus.SUCCESS,
                        details = "Generated ECDSA NIST P-256 key pair in Android Keystore."
                    )
                )

                _signState.update { it.copy(selectedAlias = cleanAlias) }
            } catch (e: Exception) {
                _signState.update { it.copy(error = e.localizedMessage ?: "Key generation failed") }
            }
        }
    }

    fun importExternalPublicKey(alias: String, pem: String) {
        val cleanAlias = alias.trim()
        val cleanPem = pem.trim()
        if (cleanAlias.isBlank() || cleanPem.isBlank()) return

        viewModelScope.launch {
            try {
                val pubKey = SignatureEngine.importPublicKeyFromPem(cleanPem)
                val fingerprint = SignatureEngine.computePublicKeyFingerprint(pubKey)

                val meta = KeyPairMeta(
                    alias = cleanAlias,
                    algorithm = "ECDSA",
                    curve = "secp256r1 (Imported PEM)",
                    publicKeyPem = cleanPem,
                    fingerprintSha256 = fingerprint,
                    isKeystoreBacked = false
                )

                db.keyPairDao().insertKeyPair(meta)
                db.auditDao().insertEvent(
                    AuditEvent(
                        action = AuditAction.KEY_GENERATE,
                        itemName = cleanAlias,
                        status = AuditStatus.SUCCESS,
                        details = "Imported external ECDSA public key. Fingerprint: ${fingerprint.take(12)}..."
                    )
                )
                _verifyState.update { it.copy(publicKeyPem = cleanPem) }
            } catch (e: Exception) {
                _verifyState.update { it.copy(error = "Invalid Public Key PEM: ${e.localizedMessage}") }
            }
        }
    }

    fun selectFileToSign(uri: Uri) {
        val (name, size) = HashingEngine.getUriMetadata(context, uri)
        _signState.update {
            it.copy(
                selectedFileUri = uri,
                fileName = name,
                sizeBytes = size,
                signatureResult = null,
                error = null
            )
        }
    }

    fun setSelectedAlias(alias: String) {
        _signState.update { it.copy(selectedAlias = alias) }
    }

    fun signSelectedFile() {
        val state = _signState.value
        val uri = state.selectedFileUri ?: return
        val alias = state.selectedAlias
        if (alias.isBlank()) {
            _signState.update { it.copy(error = "Please select or create an ECDSA key pair first.") }
            return
        }

        viewModelScope.launch {
            _signState.update { it.copy(isSigning = true, error = null) }
            try {
                val privKey = SignatureEngine.getPrivateKeyFromKeystore(alias)
                    ?: throw IllegalStateException("Private key '$alias' not found in Android Keystore")

                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Cannot read file to sign")

                val result = SignatureEngine.signStream(inputStream, privKey, alias)
                _signState.update {
                    it.copy(
                        isSigning = false,
                        signatureResult = result
                    )
                }

                db.auditDao().insertEvent(
                    AuditEvent(
                        action = AuditAction.SIGN,
                        itemName = state.fileName,
                        status = AuditStatus.SUCCESS,
                        details = "Signed with key '$alias'. Sig: ${result.signatureHex.take(12)}..."
                    )
                )
            } catch (e: Exception) {
                _signState.update {
                    it.copy(isSigning = false, error = e.localizedMessage ?: "Signing failed")
                }
            }
        }
    }

    fun selectFileToVerify(uri: Uri) {
        val (name, _) = HashingEngine.getUriMetadata(context, uri)
        _verifyState.update {
            it.copy(
                selectedFileUri = uri,
                fileName = name,
                verificationResult = null,
                error = null
            )
        }
    }

    fun setVerifySignature(sigBase64: String) {
        _verifyState.update { it.copy(signatureBase64 = sigBase64.trim(), verificationResult = null) }
    }

    fun setVerifyPublicKeyPem(pem: String) {
        _verifyState.update { it.copy(publicKeyPem = pem.trim(), verificationResult = null) }
    }

    fun verifyFileSignature() {
        val state = _verifyState.value
        val uri = state.selectedFileUri ?: return
        if (state.signatureBase64.isBlank()) {
            _verifyState.update { it.copy(error = "Please provide the signature (Base64).") }
            return
        }
        if (state.publicKeyPem.isBlank()) {
            _verifyState.update { it.copy(error = "Please provide the Public Key (PEM).") }
            return
        }

        viewModelScope.launch {
            _verifyState.update { it.copy(isVerifying = true, error = null) }
            try {
                val sigBytes = Base64.decode(state.signatureBase64, Base64.DEFAULT)
                val pubKey: PublicKey = SignatureEngine.importPublicKeyFromPem(state.publicKeyPem)

                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Cannot open file stream for verification")

                val result = SignatureEngine.verifyStream(inputStream, sigBytes, pubKey)
                _verifyState.update {
                    it.copy(
                        isVerifying = false,
                        verificationResult = result
                    )
                }

                db.auditDao().insertEvent(
                    AuditEvent(
                        action = AuditAction.VERIFY,
                        itemName = state.fileName,
                        status = if (result.isValid) AuditStatus.VERIFIED_VALID else AuditStatus.VERIFIED_INVALID,
                        details = result.details
                    )
                )
            } catch (e: Exception) {
                _verifyState.update {
                    it.copy(isVerifying = false, error = e.localizedMessage ?: "Verification error")
                }
            }
        }
    }

    fun deleteKey(alias: String) {
        viewModelScope.launch {
            SignatureEngine.deleteKeystoreKey(alias)
            db.keyPairDao().deleteKeyPairByAlias(alias)
        }
    }
}
