package com.example.ui.screens

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.security.ClipboardSecurityHelper
import com.example.ui.components.ModernSegmentedControl
import com.example.ui.components.MonospaceValueCard
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SecurityGreen
import com.example.ui.theme.SecurityOrange

@Composable
fun CsprngTokenScreen(
    viewModel: CsprngTokenViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val modeTabs = listOf("Passphrase", "Password", "Hex Secret", "Base64 Seed")
    val selectedIndex = when (state.mode) {
        GeneratorMode.DICEWARE_PASSPHRASE -> 0
        GeneratorMode.ALPHANUMERIC_PASSWORD -> 1
        GeneratorMode.HEX_SECRET -> 2
        GeneratorMode.BASE64_TOKEN -> 3
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("csprng_token_screen")
    ) {
        ModernSegmentedControl(
            items = modeTabs,
            selectedIndex = selectedIndex,
            onItemSelected = { index ->
                val newMode = when (index) {
                    0 -> GeneratorMode.DICEWARE_PASSPHRASE
                    1 -> GeneratorMode.ALPHANUMERIC_PASSWORD
                    2 -> GeneratorMode.HEX_SECRET
                    else -> GeneratorMode.BASE64_TOKEN
                }
                viewModel.setMode(newMode)
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Mode-specific configuration card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (state.mode) {
                            GeneratorMode.DICEWARE_PASSPHRASE -> {
                                Text(
                                    text = "DICEWARE WORD COUNT: ${state.dicewareWords}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Slider(
                                    value = state.dicewareWords.toFloat(),
                                    onValueChange = { viewModel.setDicewareWords(it.toInt()) },
                                    valueRange = 3f..8f,
                                    steps = 4,
                                    modifier = Modifier.testTag("diceware_slider")
                                )
                            }
                            GeneratorMode.ALPHANUMERIC_PASSWORD -> {
                                Text(
                                    text = "PASSWORD LENGTH: ${state.passwordLength} CHARS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Slider(
                                    value = state.passwordLength.toFloat(),
                                    onValueChange = { viewModel.setPasswordLength(it.toInt()) },
                                    valueRange = 8f..64f,
                                    steps = 55,
                                    modifier = Modifier.testTag("password_len_slider")
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = state.includeUpper,
                                            onCheckedChange = { viewModel.setPasswordToggles(it, state.includeLower, state.includeDigits, state.includeSymbols) }
                                        )
                                        Text("A-Z", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = state.includeLower,
                                            onCheckedChange = { viewModel.setPasswordToggles(state.includeUpper, it, state.includeDigits, state.includeSymbols) }
                                        )
                                        Text("a-z", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = state.includeDigits,
                                            onCheckedChange = { viewModel.setPasswordToggles(state.includeUpper, state.includeLower, it, state.includeSymbols) }
                                        )
                                        Text("0-9", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = state.includeSymbols,
                                            onCheckedChange = { viewModel.setPasswordToggles(state.includeUpper, state.includeLower, state.includeDigits, it) }
                                        )
                                        Text("!@#$", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            GeneratorMode.HEX_SECRET, GeneratorMode.BASE64_TOKEN -> {
                                Text(
                                    text = "KEY BYTE LENGTH",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(16, 24, 32, 64).forEach { bytes ->
                                        FilterChip(
                                            selected = state.hexByteLength == bytes,
                                            onClick = { viewModel.setHexByteLength(bytes) },
                                            label = { Text("$bytes bytes (${bytes * 8}-bit)") }
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { viewModel.generateToken() },
                            modifier = Modifier.fillMaxWidth().testTag("generate_token_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate Cryptographic Randomness")
                        }
                    }
                }
            }

            // Output & Entropy Card
            val result = state.currentResult
            if (result != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("generated_token_output_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = result.tokenType.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )

                                IconButton(
                                    onClick = {
                                        ClipboardSecurityHelper.copySensitiveText(context, result.tokenType, result.token)
                                    },
                                    modifier = Modifier.size(32.dp).testTag("copy_token_btn")
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Token", modifier = Modifier.size(18.dp))
                                }
                            }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ) {
                                Text(
                                    text = result.token,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }

                // Entropy & Resistance Assessment Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = SecurityGreen, modifier = Modifier.size(20.dp))
                                Text(
                                    text = "ENTROPY & STRENGTH ASSESSMENT",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Estimated Pool Entropy: ${result.entropyBits.toInt()} bits",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "%.2f bits/byte".format(result.shannonEntropyPerByte),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = JetBrainsMonoFamily,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            val progress = (result.entropyBits / 128.0).toFloat().coerceIn(0.1f, 1f)
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = if (progress >= 0.7f) SecurityGreen else SecurityOrange
                            )

                            Text(
                                text = "Strength: ${result.strengthRating}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (progress >= 0.7f) SecurityGreen else SecurityOrange
                            )

                            Text(
                                text = "Brute-force resistance: ${result.bruteForceEstimate}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
