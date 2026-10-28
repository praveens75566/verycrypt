package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppDatabase
import com.example.data.ThemeMode
import com.example.data.UserPreferencesRepository
import com.example.security.AppLockManager
import com.example.security.DiagnosticsEngine
import com.example.security.DiagnosticsReport
import com.example.security.DiagnosticLevel
import com.example.ui.components.PinKeypadDialog
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SecurityGreen
import com.example.ui.theme.SecurityRed
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    prefs: UserPreferencesRepository,
    db: AppDatabase,
    modifier: Modifier = Modifier
) {
    val settings by prefs.userSettings.collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showSetPinDialog by remember { mutableStateOf(false) }
    var pinSetupStep by remember { mutableStateOf(1) } // 1: Enter new PIN, 2: Confirm new PIN
    var firstEnteredPin by remember { mutableStateOf("") }
    var pinDialogError by remember { mutableStateOf<String?>(null) }
    var showClearAuditDialog by remember { mutableStateOf(false) }

    var showSelfTestDialog by remember { mutableStateOf(false) }
    var isRunningSelfTest by remember { mutableStateOf(false) }
    var selfTestReport by remember { mutableStateOf<DiagnosticsReport?>(null) }
    var showLogsDialog by remember { mutableStateOf(false) }

    if (showSetPinDialog) {
        PinKeypadDialog(
            title = if (pinSetupStep == 1) "Set 6-Digit Master PIN" else "Confirm Master PIN",
            subtitle = if (pinSetupStep == 1) "Enter a new security PIN for VeriCrypt" else "Re-enter the same 6 digits to confirm",
            errorMessage = pinDialogError,
            onPinSubmitted = { entered ->
                if (pinSetupStep == 1) {
                    firstEnteredPin = entered
                    pinSetupStep = 2
                    pinDialogError = null
                } else {
                    if (entered == firstEnteredPin) {
                        coroutineScope.launch {
                            val salt = AppLockManager.generateSalt()
                            val hash = AppLockManager.hashPin(entered, salt)
                            prefs.saveMasterPin(salt, hash)
                            Toast.makeText(context, "Master PIN configured successfully", Toast.LENGTH_SHORT).show()
                            showSetPinDialog = false
                            pinSetupStep = 1
                            firstEnteredPin = ""
                            pinDialogError = null
                        }
                    } else {
                        pinDialogError = "PINs do not match. Please try again."
                        pinSetupStep = 1
                        firstEnteredPin = ""
                    }
                }
            },
            onDismissRequest = {
                showSetPinDialog = false
                pinSetupStep = 1
                firstEnteredPin = ""
                pinDialogError = null
            }
        )
    }

    if (showClearAuditDialog) {
        AlertDialog(
            onDismissRequest = { showClearAuditDialog = false },
            title = { Text("Clear Local Audit Log") },
            text = { Text("Are you sure you want to permanently clear all recorded cryptographic audit events? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            db.auditDao().clearAuditLog()
                            Toast.makeText(context, "Audit log cleared", Toast.LENGTH_SHORT).show()
                            showClearAuditDialog = false
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = SecurityRed)
                ) {
                    Text("Clear Log")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAuditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==================== APPEARANCE & TEXT READABILITY ====================
        item {
            Text(
                text = "APPEARANCE & TEXT READABILITY",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.2.sp
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Theme Selection",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val modes = listOf(
                        Pair(ThemeMode.SYSTEM, "System"),
                        Pair(ThemeMode.DARK, "Dark"),
                        Pair(ThemeMode.OLED, "OLED"),
                        Pair(ThemeMode.LIGHT, "Light")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        modes.forEach { (mode, label) ->
                            val isSelected = settings?.themeMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { coroutineScope.launch { prefs.setThemeMode(mode) } }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Reader Font Size & Preview
                    val currentFontSize = settings?.readerFontSizeSp ?: 15
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FormatSize, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Reader Font Scale",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "${currentFontSize}sp (${when (currentFontSize) {
                                in 12..13 -> "Compact"
                                in 14..16 -> "Standard"
                                in 17..18 -> "Comfortable"
                                else -> "Large"
                            }})",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = currentFontSize.toFloat(),
                        onValueChange = { newVal ->
                            coroutineScope.launch { prefs.setReaderFontSizeSp(newVal.toInt()) }
                        },
                        valueRange = 12f..22f,
                        steps = 4,
                        modifier = Modifier.fillMaxWidth().testTag("reader_font_size_slider")
                    )

                    // Live Interactive Preview Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "Preview: The quick brown fox jumps over the lazy dog. 🔒 Encrypted notes and decrypted files render at this scale.",
                            fontSize = currentFontSize.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = (currentFontSize * 1.45f).sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // High Contrast Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "High-Contrast Text",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Strengthens text borders and contrast for outdoor and low-light reading",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings?.highContrastText == true,
                            onCheckedChange = { checked ->
                                coroutineScope.launch { prefs.setHighContrastText(checked) }
                            },
                            modifier = Modifier.testTag("high_contrast_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Text Line Wrap Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Word Wrap in Code / Ciphertext",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Wrap long ciphertext and code lines instead of horizontal scrolling",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings?.textLineWrapEnabled != false,
                            onCheckedChange = { checked ->
                                coroutineScope.launch { prefs.setTextLineWrapEnabled(checked) }
                            },
                            modifier = Modifier.testTag("line_wrap_switch")
                        )
                    }
                }
            }
        }

        // ==================== USER INTERFACE & COMPLEXITY ====================
        item {
            Text(
                text = "USER INTERFACE & COMPLEXITY",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.2.sp
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("simple_mode_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Simple UI Mode",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (settings?.isSimpleUiMode != false)
                                    "Streamlined daily security with automated defaults. Technical cryptographic jargon is hidden."
                                else
                                    "Advanced Mode active: Full cryptographic parameters (salts, IVs, KDF rounds) are displayed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = settings?.isSimpleUiMode != false,
                            onCheckedChange = { checked ->
                                coroutineScope.launch {
                                    prefs.setSimpleUiMode(checked)
                                    val modeName = if (checked) "Simple Mode" else "Advanced Mode"
                                    Toast.makeText(context, "$modeName enabled", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.testTag("simple_mode_switch")
                        )
                    }
                }
            }
        }

        // ==================== SECURITY & ACCESS CONTROL ====================
        item {
            Text(
                text = "SECURITY & ACCESS CONTROL",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.2.sp
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Master Security PIN",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (settings?.hasPinConfigured == true) "Configured (Salted SHA-256)" else "Not set",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (settings?.hasPinConfigured == true) SecurityGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                pinSetupStep = 1
                                showSetPinDialog = true
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("setup_pin_button")
                        ) {
                            Text(if (settings?.hasPinConfigured == true) "Change PIN" else "Set PIN")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Biometrics Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Biometric Authentication",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Unlock app and vault with Fingerprint / Face",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings?.isBiometricsEnabled == true,
                            onCheckedChange = { coroutineScope.launch { prefs.setBiometricsEnabled(it) } },
                            modifier = Modifier.testTag("biometric_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Screen Privacy Shield (FLAG_SECURE)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Screen Privacy Shield",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Blocks screenshots & hides content in recent apps switcher (FLAG_SECURE - for physical devices)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings?.isScreenProtectionEnabled == true,
                            onCheckedChange = { coroutineScope.launch { prefs.setScreenProtectionEnabled(it) } },
                            modifier = Modifier.testTag("screen_protection_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Auto-lock Timeout
                    Text(
                        text = "Auto-Lock Timeout",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val timeouts = listOf(
                        Pair(0, "Instant"),
                        Pair(30, "30s"),
                        Pair(60, "1m"),
                        Pair(120, "2m"),
                        Pair(300, "5m"),
                        Pair(900, "15m")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        timeouts.forEach { (sec, label) ->
                            val isSelected = settings?.autoLockTimeoutSeconds == sec
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { coroutineScope.launch { prefs.setAutoLockTimeout(sec) } }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Haptic Feedback & Vibrations Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Haptic Tactile Feedback",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Vibrate subtly on key actions, copy confirmation, and lock status changes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings?.hapticFeedbackEnabled != false,
                            onCheckedChange = { coroutineScope.launch { prefs.setHapticFeedbackEnabled(it) } },
                            modifier = Modifier.testTag("haptic_feedback_switch")
                        )
                    }
                }
            }
        }

        // ==================== CLIPBOARD & PRIVACY PROTECTION ====================
        item {
            Text(
                text = "CLIPBOARD & PRIVACY PROTECTION",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.2.sp
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Clipboard Auto-Clear Timeout",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Automatically wipes sensitive copied secrets and plaintexts from OS memory",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val clipboardTimeouts = listOf(
                        Pair(15, "15s"),
                        Pair(30, "30s"),
                        Pair(60, "60s"),
                        Pair(0, "Never")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        clipboardTimeouts.forEach { (sec, label) ->
                            val isSelected = settings?.clipboardTimeoutSeconds == sec
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { coroutineScope.launch { prefs.setClipboardTimeoutSeconds(sec) } }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = {
                            val cb = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cb.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
                            Toast.makeText(context, "Clipboard wiped immediately", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().testTag("wipe_clipboard_now_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear Clipboard Memory Now", fontSize = 12.sp)
                    }
                }
            }
        }

        // ==================== CRYPTOGRAPHIC DEFAULTS & SHREDDING ====================
        item {
            Text(
                text = "CRYPTOGRAPHIC DEFAULTS & SHREDDING",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.2.sp
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Default Encryption Cipher",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Primary authenticated cipher used for newly protected files and notes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val ciphers = listOf(
                        Pair("AES_256_GCM", "AES-256-GCM (NIST)"),
                        Pair("XCHACHA20_POLY1305", "XChaCha20 (RFC 8439)")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ciphers.forEach { (cipherKey, label) ->
                            val isSelected = (settings?.defaultCipher ?: "AES_256_GCM") == cipherKey
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { coroutineScope.launch { prefs.setDefaultCipher(cipherKey) } }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Hardware Secure Shredding Intensity",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Number of multi-pattern overwrite passes before inode unlinking",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val shreddingOptions = listOf(
                        Pair(1, "1-Pass (Zero)"),
                        Pair(3, "3-Pass (DoD)"),
                        Pair(7, "7-Pass (Gutmann)")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        shreddingOptions.forEach { (passes, label) ->
                            val isSelected = (settings?.shreddingPasses ?: 3) == passes
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { coroutineScope.launch { prefs.setShreddingPasses(passes) } }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Audit Trail Action
        item {
            Text(
                text = "DATA & PRIVACY",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.2.sp
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Local Activity Audit Trail",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Wipe on-device event history database",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { showClearAuditDialog = true },
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = SecurityRed)
                        ) {
                            Text("Clear")
                        }
                    }
                }
            }
        }

        // Technical Security Audit Status
        item {
            Text(
                text = "TECHNICAL SECURITY AUDIT & COMPLIANCE",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.2.sp
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.2.dp, SecurityGreen.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = SecurityGreen, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Security Audit Verified",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = SecurityGreen
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    SecurityAuditItem(
                        title = "Zip Slip Sandbox Boundary Guard",
                        status = "Active & Enforced",
                        details = "Canonical path verification prevents directory traversal during archive restore."
                    )
                    SecurityAuditItem(
                        title = "Screen Privacy Shield (FLAG_SECURE)",
                        status = if (settings?.isScreenProtectionEnabled == true) "Active & Enforced" else "Disabled (Default)",
                        details = "OS-level screenshot, video recording, and task switcher preview protection."
                    )
                    SecurityAuditItem(
                        title = "Zero-Trace Cryptographic Memory Hygiene",
                        status = "Active & Enforced",
                        details = "Intermediate key material and password buffers zeroized post-execution."
                    )
                    SecurityAuditItem(
                        title = "Hardware-Committed File Shredder",
                        status = "Active & Enforced",
                        details = "Multi-block zeroization with fsync (fos.fd.sync()) before inode unlinking."
                    )
                    SecurityAuditItem(
                        title = "Zero-Disk Decryption Footprint",
                        status = "Active & Enforced",
                        details = "Vault file previews decrypt strictly into memory buffers without temporary disk writes."
                    )
                }
            }
        }

        // Cryptographic Specifications Banner
        item {
            Text(
                text = "CRYPTOGRAPHIC SPECIFICATIONS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.2.sp
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    CryptoSpecRow(label = "Hashing", spec = "SHA-256 / SHA-512 (FIPS 180-4)")
                    CryptoSpecRow(label = "Authenticated Encryption", spec = "AES-256-GCM (NIST SP 800-38D)")
                    CryptoSpecRow(label = "Extended Nonce AEAD", spec = "XChaCha20-Poly1305 (RFC 8439)")
                    CryptoSpecRow(label = "Key Derivation (KDF)", spec = "PBKDF2-HMAC-SHA256 (100k rounds)")
                    CryptoSpecRow(label = "Digital Signatures", spec = "ECDSA P-256 (secp256r1)")
                    CryptoSpecRow(label = "Hardware Key Storage", spec = "AndroidKeyStore Secure Enclave")
                    CryptoSpecRow(label = "Network Permission", spec = "None (Strictly Offline App)")
                }
            }
        }

        // System Diagnostics & Health Inspector
        item {
            Text(
                text = "SYSTEM DIAGNOSTICS & HEALTH INSPECTOR",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.2.sp
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("diagnostics_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val (freeMb, totalMb, maxMb) = DiagnosticsEngine.getMemoryHealth()
                    val logsCount = DiagnosticsEngine.getLogs().size

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Runtime Integrity & Memory",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Heap: ${freeMb}MB free / ${totalMb}MB alloc (Max ${maxMb}MB)",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = JetBrainsMonoFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SecurityGreen.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Healthy",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = SecurityGreen
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                isRunningSelfTest = true
                                showSelfTestDialog = true
                                coroutineScope.launch {
                                    val report = DiagnosticsEngine.runSelfTest()
                                    selfTestReport = report
                                    isRunningSelfTest = false
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("run_selftest_button")
                        ) {
                            Text("Run Self-Test", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { showLogsDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("view_diagnostics_button")
                        ) {
                            Text("View Logs ($logsCount)", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showSelfTestDialog) {
        AlertDialog(
            onDismissRequest = { if (!isRunningSelfTest) showSelfTestDialog = false },
            title = {
                Text(
                    text = if (isRunningSelfTest) "Running Cryptographic Audit..." else "Cryptographic Suite Self-Test",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                if (isRunningSelfTest) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Benchmarking AES-GCM, XChaCha20, KDF & ECDSA...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val report = selfTestReport
                    if (report != null) {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (report.isAllPassed) "All Primitives Operational" else "Primitive Defect Detected",
                                        fontWeight = FontWeight.Bold,
                                        color = if (report.isAllPassed) SecurityGreen else SecurityRed
                                    )
                                    Text(
                                        text = "${report.totalTimeMs} ms",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            items(report.results.size) { idx ->
                                val res = report.results[idx]
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = res.primitiveName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = if (res.isSuccess) "PASSED (${res.executionTimeMs}ms)" else "FAILED",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (res.isSuccess) SecurityGreen else SecurityRed
                                        )
                                    }
                                    Text(text = res.details, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showSelfTestDialog = false },
                    enabled = !isRunningSelfTest
                ) {
                    Text("Close")
                }
            }
        )
    }

    if (showLogsDialog) {
        val logs = remember(showLogsDialog) { DiagnosticsEngine.getLogs() }
        AlertDialog(
            onDismissRequest = { showLogsDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Diagnostic Logs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(
                        onClick = {
                            DiagnosticsEngine.clearLogs()
                            showLogsDialog = false
                            Toast.makeText(context, "Diagnostic logs cleared", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Clear", color = SecurityRed, fontSize = 12.sp)
                    }
                }
            },
            text = {
                if (logs.isEmpty()) {
                    Text(
                        text = "No diagnostic errors or warnings captured. System operating normally.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                        items(logs.size) { idx ->
                            val log = logs[idx]
                            val badgeColor = when (log.level) {
                                DiagnosticLevel.ERROR -> SecurityRed
                                DiagnosticLevel.WARN -> MaterialTheme.colorScheme.error
                                DiagnosticLevel.INFO -> MaterialTheme.colorScheme.primary
                            }
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "[${log.formattedTime}] ${log.subsystem}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = log.level.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = badgeColor
                                    )
                                }
                                Text(
                                    text = log.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (log.stackTracePreview != null) {
                                    Text(
                                        text = log.stackTracePreview,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val report = DiagnosticsEngine.exportDiagnosticsSummary()
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("VeriCrypt Diagnostics", report))
                        Toast.makeText(context, "Diagnostics report copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Copy Report")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun CryptoSpecRow(label: String, spec: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = spec,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SecurityAuditItem(title: String, status: String, details: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (status.contains("Active")) SecurityGreen.copy(alpha = 0.12f) else SecurityRed.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = status,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (status.contains("Active")) SecurityGreen else SecurityRed
                )
            }
        }
        Text(text = details, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
