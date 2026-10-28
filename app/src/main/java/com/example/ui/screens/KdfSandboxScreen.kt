package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.security.ClipboardSecurityHelper
import com.example.ui.components.MonospaceValueCard
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SecurityBlue
import com.example.ui.theme.SecurityGreen

@Composable
fun KdfSandboxScreen(
    viewModel: KdfSandboxViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var isPassphraseVisible by remember { mutableStateOf(false) }
    var showSaveKeyDialog by remember { mutableStateOf(false) }
    var saveKeyAliasInput by remember { mutableStateOf("Derived-PBKDF2-Key") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("kdf_sandbox_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Info Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "PBKDF2 Key Derivation Sandbox",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Cryptographic key stretching with HMAC-SHA256 PRF",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Passphrase & Salt Parameters Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "PASSPHRASE & SALT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )

                    OutlinedTextField(
                        value = state.passphraseInput,
                        onValueChange = { viewModel.setPassphrase(it) },
                        label = { Text("Master Passphrase") },
                        modifier = Modifier.fillMaxWidth().testTag("kdf_passphrase_input"),
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = if (isPassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isPassphraseVisible = !isPassphraseVisible }) {
                                Icon(
                                    imageVector = if (isPassphraseVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Passphrase Visibility"
                                )
                            }
                        }
                    )

                    OutlinedTextField(
                        value = state.saltHex,
                        onValueChange = { viewModel.setSaltHex(it) },
                        label = { Text("Salt (Hex Bytes)") },
                        modifier = Modifier.fillMaxWidth().testTag("kdf_salt_input"),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        trailingIcon = {
                            IconButton(
                                onClick = { viewModel.generateRandomSalt(16) },
                                modifier = Modifier.testTag("kdf_random_salt_btn")
                            ) {
                                Icon(Icons.Default.Autorenew, contentDescription = "Generate 16-byte CSPRNG Salt")
                            }
                        }
                    )
                }
            }
        }

        // Iterations & Target Length Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "ITERATION WORK FACTOR",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(10_000, 100_000, 250_000, 600_000).forEach { iters ->
                            FilterChip(
                                selected = state.iterations == iters,
                                onClick = { viewModel.setIterations(iters) },
                                label = {
                                    val label = when (iters) {
                                        10_000 -> "10k"
                                        100_000 -> "100k (Std)"
                                        250_000 -> "250k"
                                        else -> "600k (OWASP)"
                                    }
                                    Text(label, fontSize = 11.sp)
                                }
                            )
                        }
                    }

                    Text(
                        text = "TARGET DERIVED KEY LENGTH",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(128, 256, 512).forEach { bits ->
                            FilterChip(
                                selected = state.keyLengthBits == bits,
                                onClick = { viewModel.setKeyLengthBits(bits) },
                                label = { Text("$bits-bit (${bits / 8} bytes)") }
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.deriveKey() },
                        modifier = Modifier.fillMaxWidth().testTag("kdf_derive_button"),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !state.isDeriving
                    ) {
                        if (state.isDeriving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Stretching Key...")
                        } else {
                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Execute PBKDF2 Derivation")
                        }
                    }
                }
            }
        }

        // Benchmark & Results Card
        if (state.derivedKeyHex.isNotEmpty()) {
            item {
                Surface(
                    color = SecurityBlue.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SecurityBlue.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = SecurityBlue, modifier = Modifier.size(20.dp))
                        Column {
                            Text(
                                text = "Device Execution Benchmark: ${state.executionTimeMs} ms",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = SecurityBlue
                            )
                            Text(
                                text = "${state.iterations} HMAC-SHA256 cycles computed locally on device",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                MonospaceValueCard(
                    title = "Derived Cryptographic Key (Hex)",
                    value = state.derivedKeyHex,
                    subtitle = "${state.keyLengthBits / 8} bytes key output",
                    testTag = "kdf_result_hex"
                )
            }

            item {
                MonospaceValueCard(
                    title = "Derived Cryptographic Key (Base64)",
                    value = state.derivedKeyBase64,
                    testTag = "kdf_result_base64"
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showSaveKeyDialog = true },
                        modifier = Modifier.weight(1f).testTag("kdf_save_to_keyring_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (state.isSavedToKeyRing) "Saved to Ring!" else "Save to Key Ring")
                    }

                    Button(
                        onClick = {
                            ClipboardSecurityHelper.copySensitiveText(
                                context,
                                "Derived Key",
                                state.derivedKeyHex,
                                customToastMessage = "Derived key copied securely to clipboard"
                            )
                        },
                        modifier = Modifier.weight(1f).testTag("kdf_copy_key_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Key")
                    }
                }
            }
        }
    }

    if (showSaveKeyDialog) {
        AlertDialog(
            onDismissRequest = { showSaveKeyDialog = false },
            title = { Text("Save Derived Key to Key Ring") },
            text = {
                OutlinedTextField(
                    value = saveKeyAliasInput,
                    onValueChange = { saveKeyAliasInput = it },
                    label = { Text("Key Alias Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveDerivedKeyToKeyRing(saveKeyAliasInput) {
                            showSaveKeyDialog = false
                            Toast.makeText(context, "Derived key saved to Key Ring", Toast.LENGTH_SHORT).show()
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
