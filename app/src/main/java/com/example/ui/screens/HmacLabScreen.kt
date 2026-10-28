package com.example.ui.screens

import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
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
import com.example.security.ClipboardSecurityHelper
import com.example.security.HmacAlgorithm
import com.example.ui.components.ModernSegmentedControl
import com.example.ui.components.MonospaceValueCard
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SecurityAmber
import com.example.ui.theme.SecurityGreen
import com.example.ui.theme.SecurityRed

@Composable
fun HmacLabScreen(
    viewModel: HmacLabViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val savedKeys by viewModel.savedKeys.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Compute HMAC", "Verify & Authenticate", "API Stamping")

    var showSaveKeyDialog by remember { mutableStateOf(false) }
    var saveKeyAliasInput by remember { mutableStateOf("") }
    var saveKeyNoteInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("hmac_lab_screen")
    ) {
        ModernSegmentedControl(
            items = tabs,
            selectedIndex = selectedTab,
            onItemSelected = { selectedTab = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    // Compute HMAC Tab
                    item {
                        AlgorithmSelectorCard(
                            selectedAlgorithm = state.algorithm,
                            onSelect = { viewModel.setAlgorithm(it) }
                        )
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "SECRET AUTHENTICATION KEY",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 1.sp
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = { viewModel.generateRandomKey() },
                                            modifier = Modifier.size(32.dp).testTag("hmac_generate_key_btn")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Autorenew,
                                                contentDescription = "Generate New Key",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                saveKeyAliasInput = "HMAC-${state.algorithm.displayName}"
                                                showSaveKeyDialog = true
                                            },
                                            modifier = Modifier.size(32.dp).testTag("hmac_save_key_btn")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.BookmarkAdd,
                                                contentDescription = "Save Key to KeyRing",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                if (state.activeKeyAlias != null) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "Active Keyring Alias: ${state.activeKeyAlias}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = state.keyInput,
                                    onValueChange = { viewModel.setKeyInput(it, state.isKeyHex) },
                                    label = { Text("Key (${if (state.isKeyHex) "Hex Bytes" else "UTF-8"})") },
                                    modifier = Modifier.fillMaxWidth().testTag("hmac_key_input"),
                                    shape = RoundedCornerShape(12.dp),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                ClipboardSecurityHelper.copySensitiveText(context, "HMAC Key", state.keyInput)
                                            }
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Key", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = state.isKeyHex,
                                        onClick = { viewModel.setKeyInput(state.keyInput, true) },
                                        label = { Text("Hex Format") }
                                    )
                                    FilterChip(
                                        selected = !state.isKeyHex,
                                        onClick = { viewModel.setKeyInput(state.keyInput, false) },
                                        label = { Text("Raw UTF-8") }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "MESSAGE TO AUTHENTICATE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )

                                OutlinedTextField(
                                    value = state.messageInput,
                                    onValueChange = { viewModel.setMessageInput(it) },
                                    placeholder = { Text("Enter plain text or payload data...") },
                                    modifier = Modifier.fillMaxWidth().testTag("hmac_message_input"),
                                    minLines = 3,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }

                    if (state.computedMacHex.isNotEmpty()) {
                        item {
                            MonospaceValueCard(
                                title = "Computed ${state.algorithm.displayName} (Hex)",
                                value = state.computedMacHex,
                                subtitle = "${state.computedMacHex.length / 2} bytes authentication tag",
                                testTag = "hmac_result_hex"
                            )
                        }

                        item {
                            MonospaceValueCard(
                                title = "Computed ${state.algorithm.displayName} (Base64)",
                                value = state.computedMacBase64,
                                testTag = "hmac_result_base64"
                            )
                        }

                        item {
                            Button(
                                onClick = {
                                    viewModel.copyCurrentCalculatedToVerify()
                                    selectedTab = 1
                                },
                                modifier = Modifier.fillMaxWidth().testTag("hmac_send_to_verify_btn"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Test In Verify & Authenticate Tab")
                            }
                        }
                    }
                }

                1 -> {
                    // Verify Tab
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "VERIFICATION PARAMETERS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )

                                OutlinedTextField(
                                    value = state.verifyMessageInput,
                                    onValueChange = { viewModel.setVerifyMessage(it) },
                                    label = { Text("Received Message Payload") },
                                    modifier = Modifier.fillMaxWidth().testTag("hmac_verify_message"),
                                    minLines = 2,
                                    shape = RoundedCornerShape(12.dp)
                                )

                                OutlinedTextField(
                                    value = state.verifyKeyInput,
                                    onValueChange = { viewModel.setVerifyKey(it, state.isVerifyKeyHex) },
                                    label = { Text("Shared Secret Key") },
                                    modifier = Modifier.fillMaxWidth().testTag("hmac_verify_key"),
                                    shape = RoundedCornerShape(12.dp),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily)
                                )

                                OutlinedTextField(
                                    value = state.verifyTargetMac,
                                    onValueChange = { viewModel.setVerifyTargetMac(it) },
                                    label = { Text("Candidate MAC Tag (Hex / Base64 / sha256=...)") },
                                    modifier = Modifier.fillMaxWidth().testTag("hmac_verify_target_mac"),
                                    shape = RoundedCornerShape(12.dp),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily)
                                )

                                Button(
                                    onClick = { viewModel.verifyIntegrity() },
                                    modifier = Modifier.fillMaxWidth().testTag("hmac_execute_verify_btn"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Verify Authenticity (Constant-Time)")
                                }
                            }
                        }
                    }

                    if (state.verificationResult != null) {
                        item {
                            VerificationStatusCard(
                                isAuthentic = state.verificationResult == true,
                                diagnosticText = state.verificationDiagnostic ?: ""
                            )
                        }
                    }
                }

                2 -> {
                    // API Stamping Tab
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "API & WEBHOOK SIGNATURE TEMPLATES",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )

                                Text(
                                    text = "Industry standard HMAC authentication formats for GitHub webhooks, AWS API requests, and microservice integrity headers.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                MonospaceValueCard(
                                    title = "X-Hub-Signature-256 (GitHub Webhook)",
                                    value = state.webhookSignature,
                                    testTag = "hmac_webhook_stamp"
                                )

                                MonospaceValueCard(
                                    title = "RFC 2104 HTTP Authorization Header",
                                    value = "Authorization: HmacSHA256 ${state.computedMacBase64}",
                                    testTag = "hmac_http_auth_header"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSaveKeyDialog) {
        AlertDialog(
            onDismissRequest = { showSaveKeyDialog = false },
            title = { Text("Save HMAC Key to Key Ring") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = saveKeyAliasInput,
                        onValueChange = { saveKeyAliasInput = it },
                        label = { Text("Key Alias Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = saveKeyNoteInput,
                        onValueChange = { saveKeyNoteInput = it },
                        label = { Text("Note / Description (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveKeyToKeyRing(saveKeyAliasInput, saveKeyNoteInput) {
                            showSaveKeyDialog = false
                            Toast.makeText(context, "HMAC key saved to Key Ring", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Save to Key Ring")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveKeyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AlgorithmSelectorCard(
    selectedAlgorithm: HmacAlgorithm,
    onSelect: (HmacAlgorithm) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "SELECT HMAC ALGORITHM",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HmacAlgorithm.values().forEach { algo ->
                    FilterChip(
                        selected = selectedAlgorithm == algo,
                        onClick = { onSelect(algo) },
                        label = { Text(algo.displayName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VerificationStatusCard(
    isAuthentic: Boolean,
    diagnosticText: String
) {
    val bgColor = if (isAuthentic) SecurityGreen.copy(alpha = 0.12f) else SecurityRed.copy(alpha = 0.12f)
    val borderColor = if (isAuthentic) SecurityGreen else SecurityRed
    val icon = if (isAuthentic) Icons.Default.CheckCircle else Icons.Default.Error
    val title = if (isAuthentic) "AUTHENTIC & VERIFIED" else "INTEGRITY CHECK FAILED"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hmac_verification_status_card"),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = borderColor,
                modifier = Modifier.size(28.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = borderColor
                )
                Text(
                    text = diagnosticText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
