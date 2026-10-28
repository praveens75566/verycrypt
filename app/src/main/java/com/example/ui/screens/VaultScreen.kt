package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.example.security.DecryptedFilePayload
import com.example.security.VaultStorage
import com.example.ui.components.MarkdownReaderView
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.UserPreferencesRepository
import com.example.data.model.NoteSecretField
import com.example.data.model.VaultItem
import com.example.data.model.VaultNote
import com.example.security.AppLockManager
import com.example.security.CsprngTokenEngine
import com.example.ui.components.ModernSegmentedControl
import com.example.ui.components.PinKeypadDialog
import com.example.ui.components.SearchAndFilterBar
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SecurityGreen
import com.example.ui.theme.SecurityRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    prefs: UserPreferencesRepository,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by prefs.userSettings.collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    var showPinDialog by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var itemToExport by remember { mutableStateOf<VaultItem?>(null) }

    var vaultViewMode by remember { mutableIntStateOf(1) } // Default to Notes tab (mode 1)
    var showNoteEditor by remember { mutableStateOf(false) }
    var noteToEdit by remember { mutableStateOf<VaultNote?>(null) }
    var noteEditPlainContent by remember { mutableStateOf("") }
    var noteEditSecretFields by remember { mutableStateOf<List<NoteSecretField>>(emptyList()) }
    var showImportNoteDialog by remember { mutableStateOf(false) }
    var showCategoryMoveDialog by remember { mutableStateOf(false) }
    var showShredConfirmDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        val item = itemToExport
        if (uri != null && item != null) {
            viewModel.exportItemToUri(item, uri)
            Toast.makeText(context, "Exporting ${item.originalName}...", Toast.LENGTH_SHORT).show()
        }
        itemToExport = null
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val category = if (uiState.selectedCategory != "All") uiState.selectedCategory else "Documents"
            viewModel.importFile(uri, category)
            Toast.makeText(context, "Encrypting file into vault...", Toast.LENGTH_SHORT).show()
        }
    }

    // Master PIN Dialog for Unlocking Vault
    if (showPinDialog) {
        PinKeypadDialog(
            title = "Unlock Secure Vault",
            subtitle = "Enter master security PIN",
            errorMessage = pinError,
            isBiometricAvailable = AppLockManager.isBiometricHardwareAvailable(context),
            onBiometricClick = {
                if (activity != null) {
                    AppLockManager.showBiometricPrompt(
                        activity = activity,
                        title = "Unlock Secure Vault",
                        onSuccess = {
                            viewModel.unlockVault("BIOMETRIC_AUTH")
                            showPinDialog = false
                            pinError = null
                        },
                        onError = { pinError = it }
                    )
                }
            },
            onPinSubmitted = { enteredPin ->
                val isValid = if (settings?.hasPinConfigured == true) {
                    AppLockManager.hashPin(enteredPin, settings!!.pinSalt).equals(settings!!.pinHash, ignoreCase = true)
                } else true

                if (isValid) {
                    viewModel.unlockVault(enteredPin)
                    showPinDialog = false
                    pinError = null
                } else {
                    pinError = "Incorrect PIN. Please try again."
                }
            },
            onDismissRequest = {
                showPinDialog = false
                pinError = null
            }
        )
    }

    // Comprehensive Note Editor Dialog (Create or Edit)
    if (showNoteEditor) {
        NoteEditorDialog(
            initialNote = noteToEdit,
            initialPlainContent = noteEditPlainContent,
            initialSecretFields = noteEditSecretFields,
            availableCategories = uiState.noteCategories,
            availableKeyringAliases = uiState.availableKeyringAliases,
            onDismiss = {
                showNoteEditor = false
                noteToEdit = null
                noteEditPlainContent = ""
                noteEditSecretFields = emptyList()
            },
            onSave = { noteId, title, content, secrets, category, colorIdx, tags, algo, keyAlias, customPassphrase, isPinned ->
                viewModel.saveOrUpdateNote(
                    existingNoteId = noteId,
                    title = title,
                    plainContent = content,
                    secretFields = secrets,
                    category = category,
                    colorIndex = colorIdx,
                    tags = tags,
                    algorithm = algo,
                    keyAlias = keyAlias,
                    customPassphrase = customPassphrase,
                    isPinned = isPinned
                )
            }
        )
    }

    // Decrypted Note Viewer Dialog
    if (uiState.activeDecryptedNote != null) {
        val decryptedData = uiState.activeDecryptedNote!!
        DecryptedNoteDialog(
            data = decryptedData,
            onDismiss = { viewModel.closeNote() },
            onEdit = {
                noteToEdit = decryptedData.note
                noteEditPlainContent = decryptedData.plainContent
                noteEditSecretFields = decryptedData.secretFields
                viewModel.closeNote()
                showNoteEditor = true
            },
            onTogglePin = {
                viewModel.togglePin(decryptedData.note)
            },
            onExportJson = {
                val json = viewModel.generateNoteExportJson(
                    note = decryptedData.note,
                    plainContent = decryptedData.plainContent,
                    secretFields = decryptedData.secretFields,
                    includeDecrypted = false
                )
                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cb.setPrimaryClip(ClipData.newPlainText("VeriCrypt Note Export", json))
                Toast.makeText(context, "Encrypted note export JSON copied to clipboard!", Toast.LENGTH_LONG).show()
            },
            onCopySensitive = { label, text ->
                viewModel.copyToClipboardWithAutoClear(label, text, 45)
            }
        )
    }

    // Import Note Dialog
    if (showImportNoteDialog) {
        ImportNoteDialog(
            onDismiss = { showImportNoteDialog = false },
            onImport = { jsonStr ->
                viewModel.importNoteFromJson(jsonStr)
            }
        )
    }

    // Decrypted Content Preview Dialog (for files)
    if (uiState.previewItem != null) {
        AlertDialog(
            onDismissRequest = { viewModel.closePreview() },
            title = {
                Text(
                    text = uiState.previewItem?.originalName ?: "File Preview",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                if (uiState.isPreviewLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = uiState.previewText ?: "No content",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.closePreview() }) {
                    Text("Close")
                }
            }
        )
    }

    if (!uiState.isUnlocked) {
        // LOCKED CURTAIN
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp)
                .testTag("vault_locked_curtain"),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Vault Locked",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Secure Vault Locked",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Confidential notes, credentials, and encrypted files protected by AES-256-GCM, XChaCha20-Poly1305, and Keyring passphrases.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { showPinDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("unlock_vault_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.LockOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unlock Vault", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    if (AppLockManager.isBiometricHardwareAvailable(context) && activity != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                AppLockManager.showBiometricPrompt(
                                    activity = activity,
                                    title = "Unlock Secure Vault",
                                    onSuccess = {
                                        viewModel.unlockVault("BIOMETRIC_AUTH")
                                    },
                                    onError = { err ->
                                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("biometric_vault_unlock_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Fingerprint, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Biometric Authentication", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    } else {
        // UNLOCKED VAULT INTERFACE
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("vault_unlocked_view"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Notification Message/Error Banner
            if (uiState.message != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SecurityGreen.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = SecurityGreen, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = uiState.message ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                            }
                            IconButton(onClick = { viewModel.clearMessages() }, modifier = Modifier.size(24.dp)) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            if (uiState.error != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SecurityRed.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(imageVector = Icons.Default.ErrorOutline, contentDescription = null, tint = SecurityRed, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = uiState.error ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                            }
                            IconButton(onClick = { viewModel.clearMessages() }, modifier = Modifier.size(24.dp)) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            // Top Status & Quick Action Bar
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(SecurityGreen))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Vault Unlocked",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "${uiState.notes.size} Notes • ${uiState.items.size} Files • ${formatFileSize(uiState.totalSizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewModel.openBackupDialog() },
                                modifier = Modifier.size(36.dp).testTag("vault_backup_button")
                            ) {
                                Icon(imageVector = Icons.Default.Archive, contentDescription = "Backup Vault", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                            IconButton(
                                onClick = { viewModel.openRestoreDialog() },
                                modifier = Modifier.size(36.dp).testTag("vault_restore_button")
                            ) {
                                Icon(imageVector = Icons.Default.Unarchive, contentDescription = "Restore Vault", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            }
                            IconButton(
                                onClick = { viewModel.toggleSelectionMode() },
                                modifier = Modifier.size(36.dp).testTag("vault_select_mode_toggle")
                            ) {
                                Icon(
                                    imageVector = if (uiState.isSelectionMode) Icons.Default.Close else Icons.Default.Checklist,
                                    contentDescription = "Batch Actions",
                                    tint = if (uiState.isSelectionMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Button(
                                onClick = { viewModel.lockVault() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("lock_vault_now_button")
                            ) {
                                Icon(imageVector = Icons.Default.Lock, contentDescription = "Lock", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Lock", fontSize = 12.sp)
                            }
                        }
                    }

                    // Selection Mode Toolbar
                    if (uiState.isSelectionMode) {
                        Spacer(modifier = Modifier.height(10.dp))
                        val count = if (vaultViewMode == 1) uiState.selectedNoteIds.size else uiState.selectedItemIds.size
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$count selected",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = { viewModel.selectAll(if (vaultViewMode == 1) 1 else 0) }
                                    ) {
                                        Text("All", fontSize = 12.sp)
                                    }
                                    TextButton(
                                        onClick = { viewModel.clearSelection() }
                                    ) {
                                        Text("Clear", fontSize = 12.sp)
                                    }
                                    IconButton(
                                        onClick = { showCategoryMoveDialog = true },
                                        enabled = count > 0,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.DriveFileMove, contentDescription = "Move Category", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = { showShredConfirmDialog = true },
                                        enabled = count > 0,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Shred Selected", tint = SecurityRed, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 1-Tap Quick Action Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        onClick = {
                            vaultViewMode = 1
                            noteToEdit = null
                            noteEditPlainContent = ""
                            noteEditSecretFields = emptyList()
                            showNoteEditor = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_action_new_note"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "New Note",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Passwords & keys",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Card(
                        onClick = {
                            vaultViewMode = 0
                            importLauncher.launch("*/*")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_action_protect_file"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.secondary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileUpload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Protect File",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Import & encrypt",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Search Bar for Vault Items & Notes
            item {
                SearchAndFilterBar(
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    placeholder = if (vaultViewMode == 1) "Search encrypted notes, tags, seeds..." else "Search files & documents...",
                    testTag = "vault_search_filter_bar"
                )
            }

            // View Mode Switcher (Notes vs Files)
            item {
                ModernSegmentedControl(
                    items = listOf("Encrypted Notes (${uiState.notes.size})", "Vault Files (${uiState.items.size})"),
                    selectedIndex = if (vaultViewMode == 1) 0 else 1,
                    onItemSelected = { vaultViewMode = if (it == 0) 1 else 0 },
                    testTagPrefix = "vault_mode_tab"
                )
            }

            if (vaultViewMode == 1) {
                // ==================== ENCRYPTED NOTES VIEW ====================
                // Category Filter Chips
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(uiState.noteCategories) { cat ->
                            val isSelected = uiState.selectedNoteCategory == cat
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setNoteCategory(cat) },
                                label = { Text(cat, fontSize = 12.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }

                // Action Row: "Add New Note" & "Import Note"
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                noteToEdit = null
                                noteEditPlainContent = ""
                                noteEditSecretFields = emptyList()
                                showNoteEditor = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("create_vault_note_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New Secure Note", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = { showImportNoteDialog = true },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("import_note_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import", fontSize = 13.sp)
                        }
                    }
                }

                // PINNED NOTES SECTION
                if (uiState.pinnedNotes.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.PushPin, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "PINNED SECRETS (${uiState.pinnedNotes.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    items(uiState.pinnedNotes, key = { "pinned_${it.id}" }) { note ->
                        VaultNoteCard(
                            note = note,
                            onRead = { viewModel.viewNote(note) },
                            onEdit = {
                                viewModel.viewNote(note)
                            },
                            onTogglePin = { viewModel.togglePin(note) },
                            onDelete = { viewModel.deleteNote(note) },
                            isSelectionMode = uiState.isSelectionMode,
                            isSelected = uiState.selectedNoteIds.contains(note.id),
                            onToggleSelect = { viewModel.toggleSelectNote(note.id) }
                        )
                    }
                }

                // ALL NOTES SECTION
                item {
                    Text(
                        text = if (uiState.selectedNoteCategory != "All") "${uiState.selectedNoteCategory.uppercase()} NOTES (${uiState.notes.size})" else "ALL NOTES (${uiState.notes.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                if (uiState.notes.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (uiState.searchQuery.isNotBlank()) "No notes match '${uiState.searchQuery}'" else "No secure notes in ${uiState.selectedNoteCategory}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Create recovery seeds, credential cards, API secrets, or encrypted memos.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Button(
                                        onClick = {
                                            noteToEdit = null
                                            noteEditPlainContent = ""
                                            noteEditSecretFields = emptyList()
                                            showNoteEditor = true
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Create Encrypted Note")
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "OR START WITH A TEMPLATE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            noteToEdit = null
                                            noteEditPlainContent = "Website/Service URL:\nSecurity Questions / 2FA Backup:\n"
                                            noteEditSecretFields = listOf(
                                                NoteSecretField(id = UUID.randomUUID().toString(), label = "Username / Email", value = ""),
                                                NoteSecretField(id = UUID.randomUUID().toString(), label = "Password", value = CsprngTokenEngine.generatePassword(16).token)
                                            )
                                            showNoteEditor = true
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("🔑 Login / Password", fontSize = 11.sp)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            noteToEdit = null
                                            noteEditPlainContent = "Derivation Path: m/84'/0'/0'\nWallet Type: Hardware Wallet\n"
                                            noteEditSecretFields = listOf(
                                                NoteSecretField(id = UUID.randomUUID().toString(), label = "Recovery Seed (12/24 words)", value = ""),
                                                NoteSecretField(id = UUID.randomUUID().toString(), label = "Passphrase (optional)", value = "")
                                            )
                                            showNoteEditor = true
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("🌱 Recovery Seed", fontSize = 11.sp)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            noteToEdit = null
                                            noteEditPlainContent = "Cardholder Name:\nIssuing Bank:\n"
                                            noteEditSecretFields = listOf(
                                                NoteSecretField(id = UUID.randomUUID().toString(), label = "Card Number", value = ""),
                                                NoteSecretField(id = UUID.randomUUID().toString(), label = "Expiry (MM/YY)", value = ""),
                                                NoteSecretField(id = UUID.randomUUID().toString(), label = "CVV / CVC", value = "")
                                            )
                                            showNoteEditor = true
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("💳 Payment Card", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(uiState.notes, key = { it.id }) { note ->
                        VaultNoteCard(
                            note = note,
                            onRead = { viewModel.viewNote(note) },
                            onEdit = {
                                viewModel.viewNote(note)
                            },
                            onTogglePin = { viewModel.togglePin(note) },
                            onDelete = { viewModel.deleteNote(note) },
                            isSelectionMode = uiState.isSelectionMode,
                            isSelected = uiState.selectedNoteIds.contains(note.id),
                            onToggleSelect = { viewModel.toggleSelectNote(note.id) }
                        )
                    }
                }
            } else {
                // ==================== VAULT FILES VIEW ====================
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(uiState.availableCategories) { category ->
                            val isSelected = uiState.selectedCategory == category
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setCategory(category) },
                                label = { Text(category, fontSize = 12.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }

                item {
                    Button(
                        onClick = { importLauncher.launch("*/*") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("import_file_to_vault_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Import & Encrypt File to ${if (uiState.selectedCategory != "All") uiState.selectedCategory else "Vault"}",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (uiState.items.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (uiState.searchQuery.isNotBlank()) "No files match '${uiState.searchQuery}'" else "No files in ${uiState.selectedCategory}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Import confidential PDFs, images, or documents into hardware-backed encrypted storage.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { importLauncher.launch("*/*") },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("empty_state_import_file_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Import File Now")
                                }
                            }
                        }
                    }
                } else {
                    items(uiState.items, key = { it.id }) { item ->
                        VaultItemCard(
                            item = item,
                            onPreview = { viewModel.openRichPreview(item) },
                            onExport = {
                                itemToExport = item
                                exportLauncher.launch(item.originalName)
                            },
                            onDelete = { viewModel.deleteItem(item) },
                            isSelectionMode = uiState.isSelectionMode,
                            isSelected = uiState.selectedItemIds.contains(item.id),
                            onToggleSelect = { viewModel.toggleSelectItem(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultItemCard(
    item: VaultItem,
    onPreview: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {}
) {
    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(item.createdAt))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { if (isSelectionMode) onToggleSelect() else onPreview() })
            .testTag("vault_item_card_${item.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (isSelectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelect() },
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.extension.take(3).uppercase().ifEmpty { "DOC" },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = item.originalName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = "${item.folderCategory} • ${formatFileSize(item.sizeBytes)} • $dateStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }

                Row {
                    IconButton(onClick = onPreview, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Visibility, contentDescription = "Preview", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onExport, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = "Export", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = SecurityRed, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "SHA-256: ${item.sha256Checksum}",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun RichFilePreviewDialog(
    payload: DecryptedFilePayload,
    onDismiss: () -> Unit,
    onExport: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = remember(payload) {
        if (payload.isImage) listOf("Image View", "Hex Inspector")
        else if (payload.isText) listOf("Text Content", "Hex Inspector")
        else listOf("Hex & ASCII Inspector")
    }

    val hexDump = remember(payload) {
        VaultStorage.generateHexDump(payload.rawBytes, 4096).joinToString("\n")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = payload.fileName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${payload.mimeType} • ${formatFileSize(payload.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Zero-disk indicator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(SecurityGreen.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "🔒 Zero-Disk In-Memory Decryption Active",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SecurityGreen
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tabs if applicable
                if (tabs.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tabs.forEachIndexed { index, tabTitle ->
                            FilterChip(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                label = { Text(tabTitle, fontSize = 11.sp) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Tab Content Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(8.dp)
                ) {
                    if (payload.isImage && selectedTab == 0) {
                        val bmp = payload.imageBitmap
                        if (bmp != null) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = payload.fileName,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Unable to render bitmap image", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else if (payload.isText && selectedTab == 0) {
                        MarkdownReaderView(
                            text = payload.textContent ?: "",
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        )
                    } else {
                        // Hex Inspector
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = hexDump,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "SHA-256: ${payload.sha256}",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        },
        confirmButton = {
            Button(onClick = onExport) {
                Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
