package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.ui.components.MarkdownReaderView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NoteSecretField
import com.example.data.model.VaultNote
import com.example.security.CsprngTokenEngine
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SecurityGreen
import com.example.ui.theme.SecurityRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object NoteColorPalette {
    val colors = listOf(
        Color(0xFF64748B), // Slate/Neutral
        Color(0xFF10B981), // Emerald
        Color(0xFFF59E0B), // Amber
        Color(0xFF3B82F6), // Blue
        Color(0xFFF43F5E), // Rose
        Color(0xFF8B5CF6)  // Purple
    )
    val names = listOf("Slate", "Emerald", "Amber", "Blue", "Rose", "Purple")

    fun getColor(index: Int): Color {
        return colors.getOrElse(index.coerceIn(0, colors.size - 1)) { colors[0] }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VaultNoteCard(
    note: VaultNote,
    onRead: () -> Unit,
    onEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {}
) {
    val dateStr = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(note.updatedAt))
    val accentColor = NoteColorPalette.getColor(note.colorIndex)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = { if (isSelectionMode) onToggleSelect() else onRead() })
            .testTag("vault_note_card_${note.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            if (isSelected) 2.dp else 1.2.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else if (note.isPinned) accentColor.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Color bar, Pin status, Category, Date, Pin button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (isSelectionMode) {
                        androidx.compose.material3.Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelect() },
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    // Note Color Dot
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Category Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = note.category,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }

                // Pin toggle button
                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier.size(32.dp).testTag("note_pin_button_${note.id}")
                ) {
                    Icon(
                        imageVector = if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (note.isPinned) "Unpin" else "Pin",
                        tint = if (note.isPinned) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Note Title
            Text(
                text = note.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Tags (if any)
            if (note.tags.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    note.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "#$tag",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = JetBrainsMonoFamily
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer Row: Algorithm badge, secret fields indicator, and Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Security Algorithm Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SecurityGreen.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = note.algorithm,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SecurityGreen
                        )
                    }

                    if (note.encryptedSecretFields.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(accentColor.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "🔐 Protected Secrets",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = accentColor
                            )
                        }
                    }
                }

                // Actions: View, Edit, Delete
                Row {
                    IconButton(
                        onClick = onRead,
                        modifier = Modifier.size(32.dp).testTag("note_view_button_${note.id}")
                    ) {
                        Icon(imageVector = Icons.Default.Visibility, contentDescription = "View Note", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp).testTag("note_edit_button_${note.id}")
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Note", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp).testTag("note_delete_button_${note.id}")
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Note", tint = SecurityRed, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/**
 * Full-featured Note Editor Dialog supporting title, category, tags, colors,
 * cryptographic algorithms, key alias selection, freeform text, and structured secret fields.
 */
@Composable
fun NoteEditorDialog(
    initialNote: VaultNote? = null,
    initialPlainContent: String = "",
    initialSecretFields: List<NoteSecretField> = emptyList(),
    availableCategories: List<String>,
    availableKeyringAliases: List<String>,
    onDismiss: () -> Unit,
    onSave: (
        noteId: Long?,
        title: String,
        plainContent: String,
        secretFields: List<NoteSecretField>,
        category: String,
        colorIndex: Int,
        tags: String,
        algorithm: String,
        keyAlias: String,
        customPassphrase: String?,
        isPinned: Boolean
    ) -> Unit
) {
    var title by remember { mutableStateOf(initialNote?.title ?: "") }
    var category by remember { mutableStateOf(initialNote?.category ?: if (availableCategories.contains("Credentials")) "Credentials" else "General") }
    var colorIndex by remember { mutableIntStateOf(initialNote?.colorIndex ?: 0) }
    var tags by remember { mutableStateOf(initialNote?.tags ?: "") }
    var isPinned by remember { mutableStateOf(initialNote?.isPinned ?: false) }
    var algorithm by remember { mutableStateOf(initialNote?.algorithm ?: "AES-256-GCM") }
    var keyAlias by remember { mutableStateOf(initialNote?.keyAlias ?: "MASTER_VAULT") }
    var customPassphrase by remember { mutableStateOf("") }
    var plainContent by remember { mutableStateOf(initialPlainContent) }

    val secretFields = remember {
        mutableStateListOf<NoteSecretField>().apply {
            addAll(initialSecretFields)
        }
    }

    var showSecretValuesInEditor by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (initialNote != null) "Edit Encrypted Note" else "New Encrypted Note",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 1-Tap Quick Templates for New Notes
                if (initialNote == null) {
                    Text(
                        text = "1-TAP TEMPLATES",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier
                                .clickable {
                                    title = "Account Login"
                                    category = "Credentials"
                                    colorIndex = 3 // Blue
                                    tags = "login, account"
                                    secretFields.clear()
                                    secretFields.add(NoteSecretField(id = UUID.randomUUID().toString(), label = "Username / Email", value = ""))
                                    val strongPass = CsprngTokenEngine.generatePassword(16).token
                                    secretFields.add(NoteSecretField(id = UUID.randomUUID().toString(), label = "Password", value = strongPass))
                                    plainContent = "Website/Service URL:\nSecurity Questions / 2FA Backup:\n"
                                }
                        ) {
                            Text(
                                text = "🔑 Login / Password",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier
                                .clickable {
                                    title = "Wallet Recovery Seed"
                                    category = "Crypto Seed"
                                    colorIndex = 2 // Amber
                                    tags = "crypto, seed, btc"
                                    secretFields.clear()
                                    secretFields.add(NoteSecretField(id = UUID.randomUUID().toString(), label = "Recovery Seed (12/24 words)", value = ""))
                                    secretFields.add(NoteSecretField(id = UUID.randomUUID().toString(), label = "Passphrase (optional)", value = ""))
                                    plainContent = "Derivation Path: m/84'/0'/0'\nWallet Type: Hardware Wallet\n"
                                }
                        ) {
                            Text(
                                text = "🌱 Recovery Seed",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier
                                .clickable {
                                    title = "Payment Card"
                                    category = "Personal"
                                    colorIndex = 1 // Emerald
                                    tags = "bank, payment, card"
                                    secretFields.clear()
                                    secretFields.add(NoteSecretField(id = UUID.randomUUID().toString(), label = "Card Number", value = ""))
                                    secretFields.add(NoteSecretField(id = UUID.randomUUID().toString(), label = "Expiry (MM/YY)", value = ""))
                                    secretFields.add(NoteSecretField(id = UUID.randomUUID().toString(), label = "CVV / CVC", value = ""))
                                    secretFields.add(NoteSecretField(id = UUID.randomUUID().toString(), label = "PIN", value = ""))
                                    plainContent = "Cardholder Name:\nIssuing Bank:\n"
                                }
                        ) {
                            Text(
                                text = "💳 Payment Card",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier
                                .clickable {
                                    title = "Encrypted Memo"
                                    category = "General"
                                    colorIndex = 0 // Slate
                                    tags = "memo, private"
                                    secretFields.clear()
                                    plainContent = ""
                                }
                        ) {
                            Text(
                                text = "📝 Secure Memo",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Title Field
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Note Title") },
                    placeholder = { Text("e.g. Coldcard Seed, Server Credentials") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("note_editor_title_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Category selector
                Text(text = "Category", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val categories = listOf("Credentials", "Crypto Seed", "Personal", "Work", "API Keys", "General")
                    categories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, fontSize = 11.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Color accent picker
                Text(text = "Color Accent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NoteColorPalette.colors.forEachIndexed { idx, color ->
                        val isSelected = colorIndex == idx
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { colorIndex = idx }
                                .then(
                                    if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.White))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tags Field
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (comma separated)") },
                    placeholder = { Text("e.g. btc, backup, prod") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("note_editor_tags_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Cryptography Options Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "Encryption Settings", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = algorithm == "AES-256-GCM",
                                onClick = { algorithm = "AES-256-GCM" },
                                label = { Text("AES-256-GCM", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = algorithm == "XChaCha20-Poly1305",
                                onClick = { algorithm = "XChaCha20-Poly1305" },
                                label = { Text("XChaCha20", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "Key Source:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = keyAlias == "MASTER_VAULT",
                                onClick = { keyAlias = "MASTER_VAULT" },
                                label = { Text("Master Vault", fontSize = 11.sp) }
                            )
                            availableKeyringAliases.forEach { alias ->
                                FilterChip(
                                    selected = keyAlias == alias,
                                    onClick = { keyAlias = alias },
                                    label = { Text("Key: $alias", fontSize = 11.sp) }
                                )
                            }
                            FilterChip(
                                selected = keyAlias == "CUSTOM",
                                onClick = { keyAlias = "CUSTOM" },
                                label = { Text("Custom Passphrase", fontSize = 11.sp) }
                            )
                        }

                        if (keyAlias == "CUSTOM") {
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = customPassphrase,
                                onValueChange = { customPassphrase = it },
                                label = { Text("Dedicated Note Passphrase") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Freeform Content
                OutlinedTextField(
                    value = plainContent,
                    onValueChange = { plainContent = it },
                    label = { Text("Encrypted Note Body") },
                    placeholder = { Text("Write your confidential notes, instructions, or recovery guides here...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .testTag("note_editor_content_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Structured Secret Fields Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Secret Fields (${secretFields.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Structured credentials with reveal/mask toggle",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                    TextButton(onClick = { showSecretValuesInEditor = !showSecretValuesInEditor }) {
                        Text(if (showSecretValuesInEditor) "Mask" else "Reveal", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Quick Templates
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val templates = listOf(
                        "Recovery Seed" to "24-word seed phrase",
                        "API Key" to "API Key / Token",
                        "Password" to "Master Password",
                        "PIN" to "Security PIN"
                    )
                    templates.forEach { (tmplLabel, tmplValue) ->
                        OutlinedButton(
                            onClick = {
                                secretFields.add(NoteSecretField(label = tmplLabel, value = ""))
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(tmplLabel, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Secret Fields List
                secretFields.forEachIndexed { index, field ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = field.label,
                                    onValueChange = { newLabel ->
                                        secretFields[index] = field.copy(label = newLabel)
                                    },
                                    label = { Text("Label", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = { secretFields.removeAt(index) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove field", tint = SecurityRed, modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = field.value,
                                onValueChange = { newVal ->
                                    secretFields[index] = field.copy(value = newVal)
                                },
                                label = { Text("Secret Value", fontSize = 11.sp) },
                                singleLine = false,
                                visualTransformation = if (showSecretValuesInEditor) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*-_=+"
                                            val random = java.security.SecureRandom()
                                            val generated = (1..20).map { chars[random.nextInt(chars.length)] }.joinToString("")
                                            secretFields[index] = field.copy(value = generated)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Generate CSPRNG Secret",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Pin toggle switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Pin this note to top", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isPinned, onCheckedChange = { isPinned = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() || plainContent.isNotBlank() || secretFields.isNotEmpty()) {
                        onSave(
                            initialNote?.id,
                            title,
                            plainContent,
                            secretFields.toList(),
                            category,
                            colorIndex,
                            tags,
                            algorithm,
                            keyAlias,
                            customPassphrase.ifBlank { null },
                            isPinned
                        )
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("note_editor_save_button")
            ) {
                Text(if (initialNote != null) "Update Note" else "Save & Encrypt")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Decrypted Note Viewer Dialog with interactive reveal/mask secret fields,
 * 1-click clipboard copy with auto-clear, export, and edit actions.
 */
@Composable
fun DecryptedNoteDialog(
    data: DecryptedNoteData,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onExportJson: () -> Unit,
    onCopySensitive: (label: String, text: String) -> Unit
) {
    val note = data.note
    val accentColor = NoteColorPalette.getColor(note.colorIndex)
    val dateStr = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(note.updatedAt))

    // Track revealed states for each secret field
    val revealedMap = remember { mutableStateOf(mapOf<String, Boolean>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(accentColor))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = note.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
                }

                Row {
                    IconButton(onClick = onTogglePin, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Pin",
                            tint = if (note.isPinned) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Metadata pill row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = note.category,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Text(
                        text = "${note.algorithm} • $dateStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }

                if (note.tags.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tags: " + note.tags.split(",").joinToString(" ") { "#${it.trim()}" },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = JetBrainsMonoFamily
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Freeform Content Section with Text Readiness & Markdown Reader
                if (data.plainContent.isNotBlank()) {
                    MarkdownReaderView(
                        text = data.plainContent,
                        modifier = Modifier.fillMaxWidth(),
                        onCopyCleanText = { onCopySensitive(note.title, data.plainContent) }
                    )
                }

                // Structured Secret Fields Section
                if (data.secretFields.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Secret Fields (${data.secretFields.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )

                        val allRevealed = data.secretFields.all { revealedMap.value[it.id] == true }
                        TextButton(
                            onClick = {
                                val nextState = !allRevealed
                                revealedMap.value = data.secretFields.associate { it.id to nextState }
                            }
                        ) {
                            Text(if (allRevealed) "Hide All" else "Reveal All", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    data.secretFields.forEach { field ->
                        val isRevealed = revealedMap.value[field.id] == true
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = field.label.ifBlank { "Secret" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (isRevealed) field.value else "••••••••••••••••",
                                        fontFamily = if (isRevealed) JetBrainsMonoFamily else FontFamily.Default,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            revealedMap.value = revealedMap.value.toMutableMap().apply {
                                                put(field.id, !isRevealed)
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (isRevealed) "Hide" else "Reveal",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { onCopySensitive(field.label, field.value) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy Secret", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onExportJson) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export", fontSize = 12.sp)
                }
                Button(onClick = onEdit) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit Note", fontSize = 12.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * Import Note Dialog to restore notes from armored export packages.
 */
@Composable
fun ImportNoteDialog(
    onDismiss: () -> Unit,
    onImport: (jsonContent: String) -> Unit
) {
    var jsonText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Encrypted Note", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Paste the exported VeriCrypt note JSON payload below:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = jsonText,
                    onValueChange = { jsonText = it },
                    placeholder = { Text("{\n  \"vericrypt_version\": \"1.0\",\n  ...\n}") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = JetBrainsMonoFamily)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (jsonText.isNotBlank()) {
                        onImport(jsonText)
                        onDismiss()
                    }
                }
            ) {
                Text("Import to Vault")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
