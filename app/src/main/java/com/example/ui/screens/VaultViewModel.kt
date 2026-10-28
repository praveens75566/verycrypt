package com.example.ui.screens

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.UserPreferencesRepository
import com.example.data.model.AuditAction
import com.example.data.model.AuditEvent
import com.example.data.model.AuditStatus
import com.example.data.model.NoteSecretField
import com.example.data.model.SymmetricKeyMeta
import com.example.data.model.VaultItem
import com.example.data.model.VaultNote
import com.example.security.CipherAlgorithm
import com.example.security.CryptoEngine
import com.example.security.DecryptedFilePayload
import com.example.security.HashingEngine
import com.example.security.VaultArchiveEngine
import com.example.security.VaultStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStream

data class DecryptedNoteData(
    val note: VaultNote,
    val plainContent: String,
    val secretFields: List<NoteSecretField>,
    val decryptionKeyUsed: String
)

data class VaultUiState(
    val isUnlocked: Boolean = false,
    val items: List<VaultItem> = emptyList(),
    val notes: List<VaultNote> = emptyList(),
    val pinnedNotes: List<VaultNote> = emptyList(),
    val totalSizeBytes: Long = 0L,
    val selectedCategory: String = "All",
    val availableCategories: List<String> = listOf("All", "Documents", "Images", "Keys", "Other"),
    val noteCategories: List<String> = listOf("All", "Credentials", "Crypto Seed", "Personal", "Work", "API Keys", "General"),
    val selectedNoteCategory: String = "All",
    val isImporting: Boolean = false,
    val previewItem: VaultItem? = null,
    val previewText: String? = null,
    val isPreviewLoading: Boolean = false,
    val richPreviewPayload: DecryptedFilePayload? = null,
    val isSelectionMode: Boolean = false,
    val selectedNoteIds: Set<Long> = emptySet(),
    val selectedItemIds: Set<Long> = emptySet(),
    val isBackupRunning: Boolean = false,
    val isRestoreRunning: Boolean = false,
    val showBackupDialog: Boolean = false,
    val showRestoreDialog: Boolean = false,
    val activeDecryptedNote: DecryptedNoteData? = null,
    val searchQuery: String = "",
    val availableKeyringAliases: List<String> = emptyList(),
    val message: String? = null,
    val error: String? = null
)

private data class VaultInteractiveState(
    val richPreview: DecryptedFilePayload? = null,
    val isSelectionMode: Boolean = false,
    val selectedNoteIds: Set<Long> = emptySet(),
    val selectedItemIds: Set<Long> = emptySet(),
    val isBackupRunning: Boolean = false,
    val isRestoreRunning: Boolean = false,
    val showBackupDialog: Boolean = false,
    val showRestoreDialog: Boolean = false
)

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val prefs = UserPreferencesRepository(application)
    private val context = application.applicationContext

    private val _isUnlocked = MutableStateFlow(false)
    private val _selectedCategory = MutableStateFlow("All")
    private val _selectedNoteCategory = MutableStateFlow("All")
    private val _searchQuery = MutableStateFlow("")
    private val _isImporting = MutableStateFlow(false)
    private val _previewItem = MutableStateFlow<VaultItem?>(null)
    private val _previewText = MutableStateFlow<String?>(null)
    private val _isPreviewLoading = MutableStateFlow(false)
    private val _activeDecryptedNote = MutableStateFlow<DecryptedNoteData?>(null)
    private val _message = MutableStateFlow<String?>(null)
    private val _error = MutableStateFlow<String?>(null)

    // Deep vault capabilities: rich preview, batch selections, backup/restore
    private val _richPreviewPayload = MutableStateFlow<DecryptedFilePayload?>(null)
    private val _isSelectionMode = MutableStateFlow(false)
    private val _selectedNoteIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _selectedItemIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _isBackupRunning = MutableStateFlow(false)
    private val _isRestoreRunning = MutableStateFlow(false)
    private val _showBackupDialog = MutableStateFlow(false)
    private val _showRestoreDialog = MutableStateFlow(false)

    private val selectionFlow = combine(
        _isSelectionMode,
        _selectedNoteIds,
        _selectedItemIds
    ) { mode, notes, items -> Triple(mode, notes, items) }

    private val backupStateFlow = combine(
        _isBackupRunning,
        _isRestoreRunning,
        _showBackupDialog,
        _showRestoreDialog
    ) { b1, b2, b3, b4 -> listOf(b1, b2, b3, b4) }

    private val interactiveFlow = combine(
        _richPreviewPayload,
        selectionFlow,
        backupStateFlow
    ) { richPreview, selection, backup ->
        VaultInteractiveState(
            richPreview = richPreview,
            isSelectionMode = selection.first,
            selectedNoteIds = selection.second,
            selectedItemIds = selection.third,
            isBackupRunning = backup[0],
            isRestoreRunning = backup[1],
            showBackupDialog = backup[2],
            showRestoreDialog = backup[3]
        )
    }

    private var activeVaultKey: String = "VeriCrypt_Default_Master_Key"

    private data class VaultData(
        val items: List<VaultItem>,
        val notes: List<VaultNote>,
        val totalSize: Long,
        val keyringAliases: List<String>
    )

    private val vaultDataFlow: Flow<VaultData> = combine(
        db.vaultDao().getAllVaultItems(),
        db.vaultNoteDao().getAllNotes(),
        db.vaultDao().getTotalVaultSizeBytes(),
        db.symmetricKeyDao().getAllSymmetricKeys()
    ) { allItems: List<VaultItem>, allNotes: List<VaultNote>, totalSize: Long?, keyringKeys: List<SymmetricKeyMeta> ->
        VaultData(allItems, allNotes, totalSize ?: 0L, keyringKeys.map { it.alias })
    }

    private data class FilterState(
        val fileCat: String,
        val noteCat: String,
        val query: String
    )

    private val filterFlow: Flow<FilterState> = combine(
        _selectedCategory,
        _selectedNoteCategory,
        _searchQuery
    ) { fileCat: String, noteCat: String, query: String ->
        FilterState(fileCat, noteCat, query)
    }

    val uiState: StateFlow<VaultUiState> = combine(
        _isUnlocked,
        vaultDataFlow,
        filterFlow,
        interactiveFlow
    ) { isUnlocked: Boolean, data: VaultData, filter: FilterState, interactive: VaultInteractiveState ->
        val catItems = if (filter.fileCat == "All") data.items else data.items.filter { it.folderCategory.equals(filter.fileCat, ignoreCase = true) }
        val catNotes = if (filter.noteCat == "All") data.notes else data.notes.filter { it.category.equals(filter.noteCat, ignoreCase = true) }

        val q = filter.query.trim()
        val filteredItems = if (q.isBlank()) catItems else catItems.filter {
            it.originalName.contains(q, ignoreCase = true) || it.folderCategory.contains(q, ignoreCase = true)
        }
        val filteredNotes = if (q.isBlank()) catNotes else catNotes.filter {
            it.title.contains(q, ignoreCase = true) ||
            it.category.contains(q, ignoreCase = true) ||
            it.tags.contains(q, ignoreCase = true)
        }

        val pinnedNotes = filteredNotes.filter { it.isPinned }

        VaultUiState(
            isUnlocked = isUnlocked,
            items = filteredItems,
            notes = filteredNotes,
            pinnedNotes = pinnedNotes,
            totalSizeBytes = data.totalSize,
            selectedCategory = filter.fileCat,
            selectedNoteCategory = filter.noteCat,
            searchQuery = filter.query,
            isImporting = _isImporting.value,
            previewItem = _previewItem.value,
            previewText = _previewText.value,
            isPreviewLoading = _isPreviewLoading.value,
            richPreviewPayload = interactive.richPreview,
            isSelectionMode = interactive.isSelectionMode,
            selectedNoteIds = interactive.selectedNoteIds,
            selectedItemIds = interactive.selectedItemIds,
            isBackupRunning = interactive.isBackupRunning,
            isRestoreRunning = interactive.isRestoreRunning,
            showBackupDialog = interactive.showBackupDialog,
            showRestoreDialog = interactive.showRestoreDialog,
            activeDecryptedNote = _activeDecryptedNote.value,
            availableKeyringAliases = data.keyringAliases,
            message = _message.value,
            error = _error.value
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VaultUiState()
    )

    fun unlockVault(pinOrKey: String) {
        activeVaultKey = if (pinOrKey.isNotBlank()) pinOrKey else "VeriCrypt_Default_Master_Key"
        _isUnlocked.value = true
        _error.value = null
    }

    fun lockVault() {
        _isUnlocked.value = false
        activeVaultKey = "VeriCrypt_Default_Master_Key"
        _previewItem.value = null
        _previewText.value = null
        _activeDecryptedNote.value = null
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setNoteCategory(category: String) {
        _selectedNoteCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearMessages() {
        _message.value = null
        _error.value = null
    }

    // --- File Operations ---
    fun importFile(uri: Uri, category: String = "Documents") {
        viewModelScope.launch {
            _isImporting.value = true
            _error.value = null
            try {
                VaultStorage.importFileToVault(
                    context = context,
                    uri = uri,
                    folderCategory = category,
                    masterPassword = activeVaultKey,
                    db = db
                )
                _message.value = "File imported & encrypted successfully"
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Failed to import file to vault"
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun exportItemToUri(item: VaultItem, destinationUri: Uri) {
        viewModelScope.launch {
            try {
                val outputStream = context.contentResolver.openOutputStream(destinationUri)
                    ?: throw IllegalStateException("Cannot open output stream for export")
                VaultStorage.exportVaultItem(
                    context = context,
                    vaultItem = item,
                    targetOutputStream = outputStream,
                    masterPassword = activeVaultKey,
                    db = db
                )
                _message.value = "File '${item.originalName}' exported successfully"
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Failed to export vault item"
            }
        }
    }

    fun openPreview(item: VaultItem) {
        _previewItem.value = item
        _previewText.value = null
        _isPreviewLoading.value = true

        viewModelScope.launch {
            try {
                val text = VaultStorage.readVaultItemPreview(
                    context = context,
                    vaultItem = item,
                    masterPassword = activeVaultKey
                )
                _previewText.value = text
            } catch (e: Exception) {
                _previewText.value = "Unable to decrypt preview: ${e.localizedMessage}"
            } finally {
                _isPreviewLoading.value = false
            }
        }
    }

    fun closePreview() {
        _previewItem.value = null
        _previewText.value = null
    }

    fun deleteItem(item: VaultItem) {
        viewModelScope.launch {
            try {
                VaultStorage.deleteVaultItem(context, item, db)
                _message.value = "Vault item securely shredded"
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Failed to delete vault item"
            }
        }
    }

    // --- Deep Notes Architecture ---

    /**
     * Resolves the encryption key string based on chosen alias:
     * - MASTER_VAULT: active vault key
     * - Keyring alias: fetches keyHex from DB
     * - Custom Passphrase: uses custom input
     */
    private suspend fun resolveEncryptionKey(keyAlias: String, customPassphrase: String?): String {
        return when {
            keyAlias == "MASTER_VAULT" -> activeVaultKey
            keyAlias == "CUSTOM" -> {
                if (!customPassphrase.isNullOrBlank()) customPassphrase else activeVaultKey
            }
            else -> {
                val keyringKey = withContext(Dispatchers.IO) {
                    db.symmetricKeyDao().getSymmetricKeyByAlias(keyAlias)
                }
                keyringKey?.keyHex ?: activeVaultKey
            }
        }
    }

    /**
     * Save or update an encrypted note with support for structured secret fields,
     * tags, color themes, and custom algorithms.
     */
    fun saveOrUpdateNote(
        existingNoteId: Long? = null,
        title: String,
        plainContent: String,
        secretFields: List<NoteSecretField>,
        category: String = "General",
        colorIndex: Int = 0,
        tags: String = "",
        algorithm: String = "AES-256-GCM",
        keyAlias: String = "MASTER_VAULT",
        customPassphrase: String? = null,
        isPinned: Boolean = false
    ) {
        viewModelScope.launch {
            try {
                val effectiveKey = resolveEncryptionKey(keyAlias, customPassphrase)
                val cipherAlgo = if (algorithm == "XChaCha20-Poly1305") {
                    CipherAlgorithm.XCHACHA20_POLY1305
                } else {
                    CipherAlgorithm.AES_256_GCM
                }

                // Encrypt main text
                val encryptedMain = CryptoEngine.encryptText(plainContent, effectiveKey, cipherAlgo)

                // Encrypt structured secret fields (if present)
                val encryptedSecrets = if (secretFields.isNotEmpty()) {
                    val secretsJson = NoteSecretField.listToJsonString(secretFields)
                    CryptoEngine.encryptText(secretsJson, effectiveKey, cipherAlgo)
                } else {
                    ""
                }

                val finalTitle = title.trim().ifBlank { "Untitled Note" }

                val note = if (existingNoteId != null && existingNoteId > 0) {
                    val oldNote = db.vaultNoteDao().getNoteById(existingNoteId)
                    VaultNote(
                        id = existingNoteId,
                        title = finalTitle,
                        encryptedContent = encryptedMain,
                        category = category,
                        isPinned = isPinned,
                        colorIndex = colorIndex,
                        tags = tags.trim(),
                        algorithm = algorithm,
                        keyAlias = keyAlias,
                        encryptedSecretFields = encryptedSecrets,
                        createdAt = oldNote?.createdAt ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                } else {
                    VaultNote(
                        title = finalTitle,
                        encryptedContent = encryptedMain,
                        category = category,
                        isPinned = isPinned,
                        colorIndex = colorIndex,
                        tags = tags.trim(),
                        algorithm = algorithm,
                        keyAlias = keyAlias,
                        encryptedSecretFields = encryptedSecrets,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                }

                if (existingNoteId != null && existingNoteId > 0) {
                    db.vaultNoteDao().updateNote(note)
                    // If this note is currently open, refresh decrypted state
                    if (_activeDecryptedNote.value?.note?.id == existingNoteId) {
                        _activeDecryptedNote.value = DecryptedNoteData(
                            note = note,
                            plainContent = plainContent,
                            secretFields = secretFields,
                            decryptionKeyUsed = keyAlias
                        )
                    }
                    db.auditDao().insertEvent(
                        AuditEvent(
                            action = AuditAction.ENCRYPT,
                            itemName = "Note Updated: $finalTitle",
                            status = AuditStatus.SUCCESS,
                            details = "Updated note in category '$category' ($algorithm)."
                        )
                    )
                    _message.value = "Note '$finalTitle' updated successfully"
                } else {
                    db.vaultNoteDao().insertNote(note)
                    db.auditDao().insertEvent(
                        AuditEvent(
                            action = AuditAction.ENCRYPT,
                            itemName = "Note Created: $finalTitle",
                            status = AuditStatus.SUCCESS,
                            details = "Created encrypted note with ${secretFields.size} secret fields ($algorithm)."
                        )
                    )
                    _message.value = "Encrypted note '$finalTitle' created"
                }
            } catch (e: Exception) {
                _error.value = "Failed to save note: ${e.localizedMessage}"
            }
        }
    }

    /**
     * Toggle pin status for high priority quick-access.
     */
    fun togglePin(note: VaultNote) {
        viewModelScope.launch {
            try {
                db.vaultNoteDao().togglePin(note.id)
                val newStatus = !note.isPinned
                db.auditDao().insertEvent(
                    AuditEvent(
                        action = AuditAction.ENCRYPT,
                        itemName = "Note: ${note.title}",
                        status = AuditStatus.SUCCESS,
                        details = if (newStatus) "Pinned note to top" else "Unpinned note"
                    )
                )
            } catch (e: Exception) {
                _error.value = "Failed to toggle pin: ${e.localizedMessage}"
            }
        }
    }

    /**
     * Decrypt and view note with its structured secret fields.
     */
    fun viewNote(note: VaultNote, customPassphrase: String? = null) {
        viewModelScope.launch {
            try {
                val effectiveKey = resolveEncryptionKey(note.keyAlias, customPassphrase)

                // Decrypt main content
                val decryptedMain = CryptoEngine.decryptText(note.encryptedContent, effectiveKey)

                // Decrypt secret fields if any
                val secretFields = if (note.encryptedSecretFields.isNotBlank()) {
                    val decryptedSecretsJson = CryptoEngine.decryptText(note.encryptedSecretFields, effectiveKey)
                    NoteSecretField.listFromJsonString(decryptedSecretsJson)
                } else {
                    emptyList()
                }

                _activeDecryptedNote.value = DecryptedNoteData(
                    note = note,
                    plainContent = decryptedMain,
                    secretFields = secretFields,
                    decryptionKeyUsed = note.keyAlias
                )
            } catch (e: Exception) {
                _error.value = "Failed to decrypt note '${note.title}'. Key mismatch or bad password."
            }
        }
    }

    fun closeNote() {
        _activeDecryptedNote.value = null
    }

    fun deleteNote(note: VaultNote) {
        viewModelScope.launch {
            try {
                db.vaultNoteDao().deleteNote(note)
                if (_activeDecryptedNote.value?.note?.id == note.id) {
                    _activeDecryptedNote.value = null
                }
                db.auditDao().insertEvent(
                    AuditEvent(
                        action = AuditAction.VAULT_DELETE,
                        itemName = "Vault Note: ${note.title}",
                        status = AuditStatus.SUCCESS,
                        details = "Permanently deleted encrypted note."
                    )
                )
                _message.value = "Note '${note.title}' permanently deleted"
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Failed to delete note"
            }
        }
    }

    /**
     * Generates an exportable JSON payload of a single note.
     */
    fun generateNoteExportJson(
        note: VaultNote,
        plainContent: String? = null,
        secretFields: List<NoteSecretField>? = null,
        includeDecrypted: Boolean = false
    ): String {
        val root = JSONObject()
        root.put("vericrypt_version", "1.0")
        root.put("type", "vericrypt_vault_note_export")
        root.put("timestamp", System.currentTimeMillis())
        root.put("title", note.title)
        root.put("category", note.category)
        root.put("tags", note.tags)
        root.put("algorithm", note.algorithm)
        root.put("keyAlias", note.keyAlias)
        root.put("colorIndex", note.colorIndex)
        root.put("encryptedContent", note.encryptedContent)
        root.put("encryptedSecretFields", note.encryptedSecretFields)

        if (includeDecrypted && plainContent != null) {
            root.put("warning", "CONFIDENTIAL: Unencrypted payload export")
            root.put("plainContent", plainContent)
            if (secretFields != null) {
                root.put("secretFields", NoteSecretField.listToJsonString(secretFields))
            }
        }

        val checksum = HashingEngine.computeSha256Bytes(note.encryptedContent.toByteArray(Charsets.UTF_8))
        root.put("sha256_checksum", checksum)
        return root.toString(2)
    }

    /**
     * Import a note from export JSON payload.
     */
    fun importNoteFromJson(jsonString: String) {
        viewModelScope.launch {
            try {
                val obj = JSONObject(jsonString)
                val title = obj.optString("title", "Imported Note")
                val category = obj.optString("category", "General")
                val tags = obj.optString("tags", "imported")
                val algorithm = obj.optString("algorithm", "AES-256-GCM")
                val keyAlias = obj.optString("keyAlias", "MASTER_VAULT")
                val colorIndex = obj.optInt("colorIndex", 0)
                val encryptedContent = obj.optString("encryptedContent", "")
                val encryptedSecretFields = obj.optString("encryptedSecretFields", "")

                if (encryptedContent.isBlank()) {
                    throw IllegalArgumentException("Invalid export format: missing encrypted content.")
                }

                val note = VaultNote(
                    title = title,
                    encryptedContent = encryptedContent,
                    category = category,
                    tags = tags,
                    algorithm = algorithm,
                    keyAlias = keyAlias,
                    colorIndex = colorIndex,
                    encryptedSecretFields = encryptedSecretFields,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                db.vaultNoteDao().insertNote(note)
                db.auditDao().insertEvent(
                    AuditEvent(
                        action = AuditAction.DECRYPT,
                        itemName = "Note Imported: $title",
                        status = AuditStatus.SUCCESS,
                        details = "Imported note package into category '$category'."
                    )
                )
                _message.value = "Note '$title' imported successfully into Vault"
            } catch (e: Exception) {
                _error.value = "Failed to import note: ${e.localizedMessage}"
            }
        }
    }

    fun openRichPreview(item: VaultItem) {
        viewModelScope.launch {
            try {
                val payload = VaultStorage.readVaultItemRichPreview(
                    context = context,
                    vaultItem = item,
                    masterPassword = activeVaultKey
                )
                _richPreviewPayload.value = payload
                db.auditDao().insertEvent(
                    AuditEvent(
                        action = AuditAction.DECRYPT,
                        itemName = "Preview: ${item.originalName}",
                        status = AuditStatus.SUCCESS,
                        details = "Zero-disk in-memory rich preview opened (${payload.mimeType})."
                    )
                )
            } catch (e: Exception) {
                _error.value = "Failed to preview item: ${e.localizedMessage}"
            }
        }
    }

    fun closeRichPreview() {
        _richPreviewPayload.value = null
    }

    // --- Selection & Batch Operations ---
    fun toggleSelectionMode() {
        _isSelectionMode.update { current ->
            val next = !current
            if (!next) {
                _selectedNoteIds.value = emptySet()
                _selectedItemIds.value = emptySet()
            }
            next
        }
    }

    fun toggleSelectNote(noteId: Long) {
        _selectedNoteIds.update { current ->
            if (current.contains(noteId)) current - noteId else current + noteId
        }
    }

    fun toggleSelectItem(itemId: Long) {
        _selectedItemIds.update { current ->
            if (current.contains(itemId)) current - itemId else current + itemId
        }
    }

    fun selectAll(mode: Int) {
        if (mode == 0) {
            _selectedItemIds.value = uiState.value.items.map { it.id }.toSet()
        } else {
            _selectedNoteIds.value = uiState.value.notes.map { it.id }.toSet()
        }
    }

    fun clearSelection() {
        _selectedNoteIds.value = emptySet()
        _selectedItemIds.value = emptySet()
    }

    fun bulkDeleteSelected(mode: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val failedCount = java.util.concurrent.atomic.AtomicInteger(0)
            try {
                if (mode == 0) {
                    val targetIds = _selectedItemIds.value
                    val allItems = db.vaultDao().getAllVaultItemsSnapshot()
                    val toDelete = allItems.filter { targetIds.contains(it.id) }
                    val deletedIds = mutableSetOf<Long>()
                    toDelete.forEach { item ->
                        try {
                            VaultStorage.deleteVaultItem(context, item, db)
                            deletedIds.add(item.id)
                        } catch (e: Exception) {
                            failedCount.incrementAndGet()
                            com.example.security.DiagnosticsEngine.recordError("VaultViewModel", "Failed to shred vault item ${item.originalName}", e)
                        }
                    }
                    _selectedItemIds.value = _selectedItemIds.value - deletedIds
                    if (failedCount.get() == 0) {
                        _message.value = "Securely shredded ${deletedIds.size} vault files"
                    } else {
                        _error.value = "Shredded ${deletedIds.size} files, but ${failedCount.get()} failed"
                    }
                } else {
                    val targetIds = _selectedNoteIds.value
                    val deletedIds = mutableSetOf<Long>()
                    targetIds.forEach { noteId ->
                        try {
                            db.vaultNoteDao().deleteNoteById(noteId)
                            deletedIds.add(noteId)
                        } catch (e: Exception) {
                            failedCount.incrementAndGet()
                            com.example.security.DiagnosticsEngine.recordError("VaultViewModel", "Failed to delete note id $noteId", e)
                        }
                    }
                    _selectedNoteIds.value = _selectedNoteIds.value - deletedIds
                    if (failedCount.get() == 0) {
                        _message.value = "Permanently deleted ${deletedIds.size} encrypted notes"
                    } else {
                        _error.value = "Deleted ${deletedIds.size} notes, but ${failedCount.get()} failed"
                    }
                }
                _isSelectionMode.value = false
            } catch (e: Exception) {
                com.example.security.DiagnosticsEngine.recordError("VaultViewModel", "Bulk delete encountered unexpected error", e)
                _error.value = "Bulk delete failed: ${e.localizedMessage}"
            }
        }
    }

    fun bulkMoveCategory(mode: Int, newCategory: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (mode == 0) {
                    val targetIds = _selectedItemIds.value.toList()
                    db.vaultDao().updateCategoryForItems(targetIds, newCategory)
                    _selectedItemIds.value = emptySet()
                    _message.value = "Moved ${targetIds.size} files to '$newCategory'"
                } else {
                    val targetIds = _selectedNoteIds.value.toList()
                    db.vaultNoteDao().updateCategoryForNotes(targetIds, newCategory)
                    _selectedNoteIds.value = emptySet()
                    _message.value = "Moved ${targetIds.size} notes to '$newCategory'"
                }
                _isSelectionMode.value = false
            } catch (e: Exception) {
                _error.value = "Bulk category move failed: ${e.localizedMessage}"
            }
        }
    }

    // --- Master Vault Backup & Restore ---
    fun openBackupDialog() {
        _showBackupDialog.value = true
    }

    fun closeBackupDialog() {
        _showBackupDialog.value = false
    }

    fun openRestoreDialog() {
        _showRestoreDialog.value = true
    }

    fun closeRestoreDialog() {
        _showRestoreDialog.value = false
    }

    fun createFullVaultBackup(destinationUri: Uri, backupPassphrase: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isBackupRunning.value = true
            _showBackupDialog.value = false
            try {
                val effectivePass = if (backupPassphrase.isNotBlank()) backupPassphrase else activeVaultKey
                VaultArchiveEngine.createVaultBackup(context, db, destinationUri, effectivePass)
                db.auditDao().insertEvent(
                    AuditEvent(
                        action = AuditAction.ENCRYPT,
                        itemName = "Vault Backup (.vcrypt-vault)",
                        status = AuditStatus.SUCCESS,
                        details = "Exported encrypted master vault backup archive."
                    )
                )
                _message.value = "Master Vault Archive exported & encrypted successfully!"
            } catch (e: Exception) {
                _error.value = "Vault backup failed: ${e.localizedMessage}"
            } finally {
                _isBackupRunning.value = false
            }
        }
    }

    fun restoreFullVaultBackup(sourceUri: Uri, backupPassphrase: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isRestoreRunning.value = true
            _showRestoreDialog.value = false
            try {
                val effectivePass = if (backupPassphrase.isNotBlank()) backupPassphrase else activeVaultKey
                val result = VaultArchiveEngine.restoreVaultBackup(context, db, sourceUri, effectivePass)
                db.auditDao().insertEvent(
                    AuditEvent(
                        action = AuditAction.DECRYPT,
                        itemName = "Vault Restore (.vcrypt-vault)",
                        status = AuditStatus.SUCCESS,
                        details = "Restored ${result.restoredFiles} files and ${result.restoredNotes} notes into vault."
                    )
                )
                _message.value = "Vault restored successfully: ${result.restoredFiles} files, ${result.restoredNotes} notes."
            } catch (e: Exception) {
                _error.value = "Vault restore failed: ${e.localizedMessage}"
            } finally {
                _isRestoreRunning.value = false
            }
        }
    }

    /**
     * Copies sensitive text to clipboard and schedules safe auto-clearing after timeout.
     */
    fun copyToClipboardWithAutoClear(label: String, text: String, autoClearSeconds: Long = 45) {
        com.example.security.ClipboardSecurityHelper.copySensitiveTextWithAutoClear(
            context = context,
            scope = viewModelScope,
            label = label,
            text = text,
            autoClearSeconds = autoClearSeconds
        )
    }
}
