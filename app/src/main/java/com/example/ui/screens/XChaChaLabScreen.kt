package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.SymmetricKeyMeta
import com.example.security.XChaCha20Poly1305Engine
import com.example.ui.components.ModernSegmentedControl
import com.example.ui.components.MonospaceValueCard
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SecurityAmber
import com.example.ui.theme.SecurityGreen
import com.example.ui.theme.SecurityRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun XChaChaLabScreen(
    viewModel: XChaChaLabViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val savedKeys by viewModel.savedKeys.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var selectedMode by remember { mutableIntStateOf(0) } // 0: Encrypt & Lab, 1: Decrypt & Verify, 2: Key Ring
    val modeTabs = listOf("Encrypt & Lab", "Decrypt & Verify", "Key Ring (${savedKeys.size})")

    var showSaveKeyDialog by remember { mutableStateOf(false) }
    var saveKeyAliasInput by remember { mutableStateOf("") }
    var saveKeyNoteInput by remember { mutableStateOf("") }

    // Launcher for saving export package file (.json)
    val savePackageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(state.jsonPackageOutput.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Encrypted package saved to file", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to save file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Launcher for importing package file (.json or .txt)
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: ""
                if (content.isNotBlank()) {
                    viewModel.importPackageString(content)
                    Toast.makeText(context, "Package imported successfully", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Modal to Save Key to Ring
    if (showSaveKeyDialog) {
        AlertDialog(
            onDismissRequest = { showSaveKeyDialog = false },
            title = {
                Text(
                    text = "Save Key to Symmetric Key Ring",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Assign an identifiable alias to reuse this 256-bit key at any time for encryption and decryption.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = saveKeyAliasInput,
                        onValueChange = { saveKeyAliasInput = it },
                        label = { Text("Key Alias (e.g. Work Cloud Key, Team Sync)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("save_key_alias_input"),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = saveKeyNoteInput,
                        onValueChange = { saveKeyNoteInput = it },
                        label = { Text("Optional Note") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("save_key_note_input"),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (saveKeyAliasInput.isNotBlank()) {
                            viewModel.saveKeyToRing(saveKeyAliasInput, state.secretKeyHex, saveKeyNoteInput)
                            showSaveKeyDialog = false
                            saveKeyAliasInput = ""
                            saveKeyNoteInput = ""
                            Toast.makeText(context, "Key saved to Key Ring", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("confirm_save_key_btn"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save to Key Ring", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveKeyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("xchacha_lab_screen")
    ) {
        // Mode Selector Bar
        ModernSegmentedControl(
            items = modeTabs,
            selectedIndex = selectedMode,
            onItemSelected = { selectedMode = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            testTagPrefix = "xchacha_mode_tab"
        )

        when (selectedMode) {
            0 -> EncryptAndLabView(
                state = state,
                savedKeys = savedKeys,
                viewModel = viewModel,
                onSaveCurrentKey = { showSaveKeyDialog = true },
                onSaveFile = { savePackageLauncher.launch("xchacha_payload_${System.currentTimeMillis()}.json") },
                onNavigateToDecrypt = {
                    viewModel.loadCurrentEncryptionIntoDecrypt()
                    selectedMode = 1
                }
            )
            1 -> DecryptAndVerifyView(
                state = state,
                savedKeys = savedKeys,
                viewModel = viewModel,
                onImportFile = { importFileLauncher.launch("*/*") }
            )
            2 -> KeyRingView(
                savedKeys = savedKeys,
                activeKeyHex = state.secretKeyHex,
                onSelectKey = { key ->
                    viewModel.selectKeyForEncryption(key)
                    selectedMode = 0
                    Toast.makeText(context, "Active key switched to '${key.alias}'", Toast.LENGTH_SHORT).show()
                },
                onDeleteKey = { viewModel.deleteKeyFromRing(it) },
                onAddCustomKey = { alias, hex -> viewModel.saveKeyToRing(alias, hex) }
            )
        }
    }
}

@Composable
private fun EncryptAndLabView(
    state: XChaChaLabUiState,
    savedKeys: List<SymmetricKeyMeta>,
    viewModel: XChaChaLabViewModel,
    onSaveCurrentKey: () -> Unit,
    onSaveFile: () -> Unit,
    onNavigateToDecrypt: () -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Header
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().testTag("xchacha_hero_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "XChaCha20-Poly1305 AEAD",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "192-BIT NONCE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "RFC 8439 Authenticated Encryption with Extended 24-byte Nonce",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Key Selection & Nonce Generation
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Cryptographic Material",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (state.activeKeyAlias != null) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = state.activeKeyAlias,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(
                                onClick = onSaveCurrentKey,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("save_current_key_btn")
                            ) {
                                Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save Key", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { viewModel.generateNewKey() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("gen_key_btn")
                            ) {
                                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New Key", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { viewModel.generateNewNonce() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("gen_nonce_btn")
                            ) {
                                Icon(Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New Nonce", fontSize = 11.sp)
                            }
                        }
                    }

                    // Key Quick-Picker from Key Ring
                    if (savedKeys.isNotEmpty()) {
                        Text(
                            text = "Switch Saved Key:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(savedKeys) { keyMeta ->
                                val isSelected = state.secretKeyHex.equals(keyMeta.keyHex, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.selectKeyForEncryption(keyMeta) },
                                    label = { Text(keyMeta.alias, fontSize = 11.sp) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(13.dp)) }
                                    } else null
                                )
                            }
                        }
                    }

                    MonospaceValueCard(
                        title = "256-BIT SECRET KEY (32 BYTES)",
                        value = state.secretKeyHex,
                        testTag = "xchacha_key_value"
                    )

                    MonospaceValueCard(
                        title = "192-BIT EXTENDED NONCE (24 BYTES)",
                        value = state.nonceHex,
                        subtitle = "Statistically collision-free for trillions of randomly generated messages.",
                        testTag = "xchacha_nonce_value"
                    )
                }
            }
        }

        // Inputs
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Plaintext & Authenticated Data (AAD)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = state.plainTextInput,
                        onValueChange = { viewModel.updatePlainText(it) },
                        label = { Text("Plaintext Message to Encrypt") },
                        modifier = Modifier.fillMaxWidth().testTag("xchacha_plaintext_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = state.aadInput,
                        onValueChange = { viewModel.updateAad(it) },
                        label = { Text("Additional Authenticated Data (AAD)") },
                        supportingText = { Text("Authenticated by Poly1305 MAC without being encrypted") },
                        modifier = Modifier.fillMaxWidth().testTag("xchacha_aad_input"),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        }

        // Tamper Simulation Lab
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().testTag("xchacha_tamper_card")
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Tamper Simulation & Integrity Resistance",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Simulate forgery attacks to verify that Poly1305 MAC instantly rejects any tampered byte.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.testTamper(XChaCha20Poly1305Engine.TamperTarget.CIPHERTEXT) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.weight(1f).testTag("tamper_ciphertext_btn"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Flip Cipher", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.testTamper(XChaCha20Poly1305Engine.TamperTarget.TAG) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            modifier = Modifier.weight(1f).testTag("tamper_tag_btn"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Corrupt Tag", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.testTamper(XChaCha20Poly1305Engine.TamperTarget.AAD) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.outline),
                            modifier = Modifier.weight(1f).testTag("tamper_aad_btn"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Alter AAD", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (state.tamperStatus != null) {
                        val isRejected = state.isTamperRejected == true
                        val bannerColor = if (isRejected) SecurityGreen else SecurityRed
                        val icon = if (isRejected) Icons.Default.Shield else Icons.Default.Error

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(bannerColor.copy(alpha = 0.15f))
                                .border(1.dp, bannerColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                                .testTag("tamper_result_banner")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(imageVector = icon, contentDescription = null, tint = bannerColor)
                                Text(
                                    text = state.tamperStatus ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = bannerColor
                                )
                            }
                        }
                    }
                }
            }
        }

        // Ciphertext & 1-Click Export Package
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "1-Click Shareable Encrypted Package",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Export structured package with all cryptographic parameters (Nonce, Tag, Ciphertext, AAD) safely omitting the secret key.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 1-Click Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, state.jsonPackageOutput)
                                    type = "application/json"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share VeriCrypt JSON Package"))
                            },
                            modifier = Modifier.weight(1f).testTag("share_json_package_btn"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share JSON", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onSaveFile,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.weight(1f).testTag("save_json_file_btn"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save File", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onNavigateToDecrypt,
                            modifier = Modifier.weight(1f).testTag("test_decrypt_tab_btn"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test Decrypt", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    MonospaceValueCard(
                        title = "POLY1305 AUTHENTICATION TAG (128 BITS)",
                        value = state.tagHex,
                        testTag = "xchacha_tag_value"
                    )

                    MonospaceValueCard(
                        title = "XCHACHA20 CIPHERTEXT (HEX)",
                        value = state.cipherTextHex,
                        testTag = "xchacha_cipher_value"
                    )

                    MonospaceValueCard(
                        title = "VERICRYPT JSON PACKAGE (SELF-CONTAINED)",
                        value = state.jsonPackageOutput,
                        testTag = "xchacha_json_package_value"
                    )
                }
            }
        }
    }
}

@Composable
private fun DecryptAndVerifyView(
    state: XChaChaLabUiState,
    savedKeys: List<SymmetricKeyMeta>,
    viewModel: XChaChaLabViewModel,
    onImportFile: () -> Unit
) {
    val context = LocalContext.current
    val packageInspection = remember(state.decryptInputText) {
        if (state.decryptInputText.isNotBlank()) com.example.security.CryptoDiagnostics.inspectPackage(state.decryptInputText) else null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Import Toolbar
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "1-Click Package Import",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Load an encrypted package from file or paste JSON/armored text.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onImportFile,
                            modifier = Modifier.weight(1f).testTag("import_package_file_btn"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open File", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = cb.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                if (clip.isNotBlank()) {
                                    viewModel.importPackageString(clip)
                                    Toast.makeText(context, "Pasted & parsed from clipboard", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("paste_package_clipboard_btn"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Paste Clip", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.loadCurrentEncryptionIntoDecrypt() },
                            modifier = Modifier.weight(1f).testTag("load_lab_cipher_btn"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Load Lab", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Pre-Flight Package Inspector Card (if package envelope is pasted/loaded)
        if (packageInspection != null && packageInspection.isRecognizedPackage) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("package_inspection_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text(
                                text = "PRE-FLIGHT ENVELOPE INSPECTION",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                        }
                        Text(
                            text = "Format: ${packageInspection.formatType} (VeriCrypt v${packageInspection.version}) • Algorithm: ${packageInspection.algorithm}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Nonce: ${packageInspection.nonceByteCount} bytes (${if (packageInspection.isNonceValid) "Valid" else "Invalid"}) • Tag: ${packageInspection.tagByteCount} bytes (${if (packageInspection.isTagValid) "Valid" else "Invalid"}) • Cipher: ${packageInspection.ciphertextByteCount} bytes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SecurityGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = packageInspection.structuralIntegrityStatus,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = SecurityGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Key Selection for Decryption
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Decryption Secret Key (256-bit)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (state.decryptKeyAlias != null) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = state.decryptKeyAlias,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (state.decryptKeyHint != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SecurityAmber.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Key, contentDescription = null, tint = SecurityAmber, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Package Key Hint: ${state.decryptKeyHint}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SecurityAmber,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Key Picker from Ring
                    if (savedKeys.isNotEmpty()) {
                        Text(
                            text = "Select Key from Ring:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(savedKeys) { keyMeta ->
                                val isSelected = state.decryptKeyHex.equals(keyMeta.keyHex, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.selectKeyForDecryption(keyMeta) },
                                    label = { Text(keyMeta.alias, fontSize = 11.sp) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(13.dp)) }
                                    } else null
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = state.decryptKeyHex,
                        onValueChange = { viewModel.setCustomDecryptKeyHex(it) },
                        label = { Text("Secret Key Hex (64 characters)") },
                        modifier = Modifier.fillMaxWidth().testTag("decrypt_key_hex_input"),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        }

        // Cipher Components Input
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Cipher Parameters",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = state.decryptNonceHex,
                        onValueChange = { viewModel.updateDecryptNonceHex(it) },
                        label = { Text("192-bit Nonce Hex (24 bytes / 48 hex chars)") },
                        modifier = Modifier.fillMaxWidth().testTag("decrypt_nonce_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = state.decryptTagHex,
                        onValueChange = { viewModel.updateDecryptTagHex(it) },
                        label = { Text("128-bit Poly1305 Tag Hex (16 bytes / 32 hex chars)") },
                        modifier = Modifier.fillMaxWidth().testTag("decrypt_tag_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = state.decryptCiphertextHex,
                        onValueChange = { viewModel.updateDecryptCiphertextHex(it) },
                        label = { Text("Ciphertext Stream (Hex)") },
                        modifier = Modifier.fillMaxWidth().height(100.dp).testTag("decrypt_cipher_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = state.decryptAadInput,
                        onValueChange = { viewModel.updateDecryptAad(it) },
                        label = { Text("Optional AAD (Additional Authenticated Data)") },
                        modifier = Modifier.fillMaxWidth().testTag("decrypt_aad_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = { viewModel.executeDecryption() },
                        enabled = !state.isDecrypting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("execute_xchacha_decrypt_btn"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.LockOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (state.isDecrypting) "Authenticating & Decrypting..." else "Authenticate Tag & Decrypt",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Decryption Result
        if (state.decryptedPlaintext.isNotBlank() && state.decryptSuccess == true) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SecurityGreen),
                    modifier = Modifier.fillMaxWidth().testTag("decrypt_success_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SecurityGreen, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "POLY1305 AUTHENTICATION VERIFIED",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = SecurityGreen
                                )
                            }
                            IconButton(
                                onClick = {
                                    com.example.security.ClipboardSecurityHelper.copySensitiveText(
                                        context,
                                        "Decrypted Plaintext",
                                        state.decryptedPlaintext,
                                        customToastMessage = "Plaintext copied securely"
                                    )
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = state.decryptedPlaintext,
                                fontFamily = JetBrainsMonoFamily,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

        if (state.decryptError != null && state.decryptSuccess == false) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SecurityRed),
                    modifier = Modifier.fillMaxWidth().testTag("decrypt_error_card")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = SecurityRed, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "AUTHENTICATION REJECTED",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = SecurityRed
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = state.decryptError ?: "Poly1305 tag mismatch. The ciphertext or AAD has been tampered with or the decryption key is incorrect.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyRingView(
    savedKeys: List<SymmetricKeyMeta>,
    activeKeyHex: String,
    onSelectKey: (SymmetricKeyMeta) -> Unit,
    onDeleteKey: (SymmetricKeyMeta) -> Unit,
    onAddCustomKey: (String, String) -> Unit
) {
    val context = LocalContext.current
    var showCreateKeyDialog by remember { mutableStateOf(false) }
    var newKeyAlias by remember { mutableStateOf("") }
    var newKeyHexInput by remember { mutableStateOf("") }

    if (showCreateKeyDialog) {
        AlertDialog(
            onDismissRequest = { showCreateKeyDialog = false },
            title = {
                Text(
                    text = "Add Symmetric Key to Ring",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter a name for the key. Leave Key Hex blank to automatically generate a secure random 256-bit key.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newKeyAlias,
                        onValueChange = { newKeyAlias = it },
                        label = { Text("Key Alias Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = newKeyHexInput,
                        onValueChange = { newKeyHexInput = it },
                        label = { Text("Key Hex (Optional: 64 hex chars)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newKeyAlias.isNotBlank()) {
                            val hex = if (newKeyHexInput.trim().length == 64) {
                                newKeyHexInput.trim()
                            } else {
                                val gen = XChaCha20Poly1305Engine.generateSecureKey()
                                com.example.security.HashingEngine.bytesToHex(gen)
                            }
                            onAddCustomKey(newKeyAlias, hex)
                            showCreateKeyDialog = false
                            newKeyAlias = ""
                            newKeyHexInput = ""
                            Toast.makeText(context, "Symmetric key added to Key Ring", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Add Key", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateKeyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Button(
                onClick = { showCreateKeyDialog = true },
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("add_key_to_ring_btn"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate & Store New Symmetric Key", fontWeight = FontWeight.Bold)
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STORED SYMMETRIC KEYS (${savedKeys.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.2.sp
                )
            }
        }

        if (savedKeys.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No keys saved in the Key Ring yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Save your current XChaCha20 key or tap 'Generate & Store New Symmetric Key' above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        } else {
            items(savedKeys, key = { it.id }) { keyMeta ->
                val isCurrentActive = activeKeyHex.equals(keyMeta.keyHex, ignoreCase = true)
                val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(keyMeta.createdAt))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isCurrentActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = keyMeta.alias,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isCurrentActive) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "ACTIVE",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "${keyMeta.algorithm} • Created $dateStr",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row {
                                IconButton(
                                    onClick = {
                                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cb.setPrimaryClip(ClipData.newPlainText("Key Hex", keyMeta.keyHex))
                                        Toast.makeText(context, "Key hex copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Key", modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = { onDeleteKey(keyMeta) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Key", tint = SecurityRed, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Fingerprint: ${keyMeta.fingerprintSha256}... • Key: ${keyMeta.keyHex.take(16)}...${keyMeta.keyHex.takeLast(8)}",
                                fontFamily = JetBrainsMonoFamily,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(8.dp),
                                fontSize = 11.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = { onSelectKey(keyMeta) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isCurrentActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary,
                                contentColor = if (isCurrentActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(if (isCurrentActive) "Currently Active in Lab" else "Select & Use Key in Lab", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
