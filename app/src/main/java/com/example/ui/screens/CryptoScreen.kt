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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import com.example.ui.components.ModernSegmentedControl
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.EntropyVisualizer
import com.example.ui.components.MarkdownReaderView
import com.example.ui.components.MonospaceValueCard
import com.example.ui.components.ProgressCard
import com.example.ui.theme.SecurityAmber
import com.example.ui.theme.SecurityGreen
import com.example.ui.theme.SecurityRed

@Composable
fun CryptoScreen(
    viewModel: CryptoViewModel,
    modifier: Modifier = Modifier
) {
    val encryptState by viewModel.encryptState.collectAsStateWithLifecycle()
    val decryptState by viewModel.decryptState.collectAsStateWithLifecycle()
    val textEncryptState by viewModel.textEncryptState.collectAsStateWithLifecycle()
    val textDecryptState by viewModel.textDecryptState.collectAsStateWithLifecycle()

    var primaryTabIndex by remember { mutableIntStateOf(0) }
    var fileSubModeIndex by remember { mutableIntStateOf(0) }
    var textSubModeIndex by remember { mutableIntStateOf(0) }
    var labsSubModeIndex by remember { mutableIntStateOf(0) }

    val primaryTabs = listOf("Files", "Text & Messages", "Crypto Labs")

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("crypto_screen")
    ) {
        TabRow(
            selectedTabIndex = primaryTabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            primaryTabs.forEachIndexed { index, title ->
                Tab(
                    selected = primaryTabIndex == index,
                    onClick = { primaryTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (primaryTabIndex == index) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.testTag("crypto_tab_$index")
                )
            }
        }

        when (primaryTabIndex) {
            0 -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        ModernSegmentedControl(
                            items = listOf("Encrypt File", "Decrypt Container"),
                            selectedIndex = fileSubModeIndex,
                            onItemSelected = { fileSubModeIndex = it },
                            testTagPrefix = "crypto_file_mode"
                        )
                    }
                    if (fileSubModeIndex == 0) {
                        EncryptTab(encryptState = encryptState, viewModel = viewModel)
                    } else {
                        DecryptTab(decryptState = decryptState, viewModel = viewModel)
                    }
                }
            }
            1 -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        ModernSegmentedControl(
                            items = listOf("Encrypt Text", "Decrypt Message"),
                            selectedIndex = textSubModeIndex,
                            onItemSelected = { textSubModeIndex = it },
                            testTagPrefix = "crypto_text_mode"
                        )
                    }
                    if (textSubModeIndex == 0) {
                        TextEncryptTab(textEncryptState = textEncryptState, viewModel = viewModel)
                    } else {
                        TextDecryptTab(textDecryptState = textDecryptState, viewModel = viewModel)
                    }
                }
            }
            2 -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        ModernSegmentedControl(
                            items = listOf("XChaCha20 AEAD", "HMAC Lab", "KDF Sandbox"),
                            selectedIndex = labsSubModeIndex,
                            onItemSelected = { labsSubModeIndex = it },
                            testTagPrefix = "crypto_lab_mode"
                        )
                    }
                    when (labsSubModeIndex) {
                        0 -> {
                            val labViewModel: XChaChaLabViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                            XChaChaLabScreen(viewModel = labViewModel)
                        }
                        1 -> {
                            val hmacViewModel: HmacLabViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                            HmacLabScreen(viewModel = hmacViewModel)
                        }
                        2 -> {
                            val kdfViewModel: KdfSandboxViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                            KdfSandboxScreen(viewModel = kdfViewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextEncryptTab(
    textEncryptState: TextEncryptState,
    viewModel: CryptoViewModel
) {
    val context = LocalContext.current
    var passwordVisible by remember { mutableStateOf(false) }
    val entropy = viewModel.calculatePasswordEntropy(textEncryptState.passwordInput)

    val saveArmoredLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(textEncryptState.armoredOutput.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Armored message saved to file", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to save file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Direct Plaintext Encryption",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Encrypt text snippets into authenticated armored Base64 messages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = textEncryptState.plainTextInput,
                        onValueChange = { viewModel.updateTextEncryptInput(it) },
                        label = { Text("Plaintext Message to Encrypt") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .testTag("text_encrypt_input"),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = textEncryptState.passwordInput,
                        onValueChange = { viewModel.updateTextEncryptPassword(it) },
                        label = { Text("Encryption Password") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("text_encrypt_password_input"),
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Entropy meter
                    LinearProgressIndicator(
                        progress = { entropy / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = when {
                            entropy < 40 -> SecurityRed
                            entropy < 70 -> SecurityAmber
                            else -> SecurityGreen
                        },
                        trackColor = MaterialTheme.colorScheme.outlineVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    var showAdvancedParams by remember { mutableStateOf(false) }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .clickable { showAdvancedParams = !showAdvancedParams },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Advanced Cryptographic Parameters",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = textEncryptState.selectedAlgorithm.shortName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = if (showAdvancedParams) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            if (showAdvancedParams) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Select Authenticated Cipher:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    com.example.security.CipherAlgorithm.values().forEach { algo ->
                                        val selected = textEncryptState.selectedAlgorithm == algo
                                        androidx.compose.material3.FilterChip(
                                            selected = selected,
                                            onClick = { viewModel.selectAlgorithm(algo) },
                                            label = {
                                                Text(
                                                    text = algo.shortName,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            leadingIcon = if (selected) {
                                                {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            } else null,
                                            modifier = Modifier.testTag("cipher_chip_${algo.name.lowercase()}")
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "• Key Derivation: PBKDF2-HMAC-SHA256 (100,000 rounds)\n• Salt: 16-byte cryptographically secure random\n• Authentication Tag: 128-bit MAC verification",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.encryptDirectText() },
                        enabled = textEncryptState.plainTextInput.isNotBlank() &&
                                textEncryptState.passwordInput.length >= 6 &&
                                !textEncryptState.isEncrypting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("execute_text_encrypt_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (textEncryptState.isEncrypting) "Encrypting..." else "Encrypt to Armored Message",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (textEncryptState.armoredOutput.isNotBlank()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, textEncryptState.armoredOutput)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Armored Message"))
                        },
                        modifier = Modifier.weight(1f).testTag("share_armored_text_btn"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share Message", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { saveArmoredLauncher.launch("encrypted_message_${System.currentTimeMillis()}.txt") },
                        modifier = Modifier.weight(1f).testTag("save_armored_file_btn"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save .txt File", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                MonospaceValueCard(
                    title = "ARMORED ENCRYPTED MESSAGE (BASE64)",
                    value = textEncryptState.armoredOutput,
                    subtitle = "Encrypted with AES-256-GCM authenticated cipher. Safe for copying and sharing.",
                    testTag = "armored_message_result"
                )
            }

            textEncryptState.entropyResult?.let { entropy ->
                item {
                    EntropyVisualizer(
                        result = entropy,
                        title = "Ciphertext Shannon Entropy & Obfuscation"
                    )
                }
            }
        }

        if (textEncryptState.error != null) {
            item {
                Text(text = textEncryptState.error ?: "", color = SecurityRed)
            }
        }
    }
}

@Composable
private fun TextDecryptTab(
    textDecryptState: TextDecryptState,
    viewModel: CryptoViewModel
) {
    val context = LocalContext.current
    var passwordVisible by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Decrypt Armored Text Message",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = textDecryptState.armoredInput,
                        onValueChange = { viewModel.updateTextDecryptInput(it) },
                        label = { Text("Paste -----BEGIN VERICRYPT ENCRYPTED MESSAGE-----") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .testTag("text_decrypt_input"),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = cb.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                viewModel.updateTextDecryptInput(clip)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Paste Armored Text")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = textDecryptState.passwordInput,
                        onValueChange = { viewModel.updateTextDecryptPassword(it) },
                        label = { Text("Decryption Password") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("text_decrypt_password_input"),
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.decryptDirectText() },
                        enabled = textDecryptState.armoredInput.isNotBlank() &&
                                textDecryptState.passwordInput.isNotBlank() &&
                                !textDecryptState.isDecrypting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("execute_text_decrypt_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.LockOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (textDecryptState.isDecrypting) "Decrypting..." else "Decrypt to Plaintext",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (textDecryptState.plainTextOutput.isNotBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SecurityGreen)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = SecurityGreen, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("DECRYPTED PLAINTEXT", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = SecurityGreen)
                            }
                            IconButton(
                                onClick = {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cb.setPrimaryClip(ClipData.newPlainText("Decrypted Plaintext", textDecryptState.plainTextOutput))
                                    Toast.makeText(context, "Copied plaintext to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        MarkdownReaderView(
                            text = textDecryptState.plainTextOutput,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        if (textDecryptState.isTamperDetected) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SecurityRed.copy(alpha = 0.15f))
                        .border(1.dp, SecurityRed, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Error, contentDescription = null, tint = SecurityRed, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("AUTHENTICATION FAILED", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = SecurityRed)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Incorrect password or the armored message has been tampered with or corrupted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EncryptTab(
    encryptState: EncryptUiState,
    viewModel: CryptoViewModel
) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectFileForEncryption(it) }
    }

    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { targetUri: Uri? ->
        val srcUri = encryptState.selectedUri
        if (targetUri != null && srcUri != null && password.isNotBlank()) {
            viewModel.encryptToFile(srcUri, targetUri, password)
        }
    }

    val entropy = viewModel.calculatePasswordEntropy(password)
    val isPasswordValid = password.length >= 8 && password == confirmPassword

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Button(
                onClick = { filePicker.launch("*/*") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("select_file_for_encrypt_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FileOpen,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (encryptState.fileName.isBlank()) "Select File to Encrypt" else "Change Selected File",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (encryptState.fileName.isNotBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = encryptState.fileName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Size: ${encryptState.sizeBytes} bytes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Encryption Password",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Keys are derived using PBKDF2-HMAC-SHA256 (100,000 rounds) + 16-byte random salt",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Enter Encryption Password") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("encrypt_password_input"),
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                    )
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("Confirm Password") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("encrypt_confirm_password_input"),
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            shape = RoundedCornerShape(8.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Password Entropy Meter
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Strength: " + when {
                                    password.isEmpty() -> "Empty"
                                    entropy < 40 -> "Weak"
                                    entropy < 70 -> "Medium"
                                    else -> "Strong (High Entropy)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = when {
                                    entropy < 40 -> SecurityRed
                                    entropy < 70 -> SecurityAmber
                                    else -> SecurityGreen
                                }
                            )
                            Text(
                                text = "$entropy / 100",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { entropy / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = when {
                                entropy < 40 -> SecurityRed
                                entropy < 70 -> SecurityAmber
                                else -> SecurityGreen
                            },
                            trackColor = MaterialTheme.colorScheme.outlineVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        var showFileAdvancedParams by remember { mutableStateOf(false) }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .clickable { showFileAdvancedParams = !showFileAdvancedParams },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Advanced Container Parameters",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "AES-256-GCM",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = if (showFileAdvancedParams) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                if (showFileAdvancedParams) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "• Container Format: VeriCrypt Authenticated Binary (.vcrypt)\n• Cipher: AES-256 Galois/Counter Mode (FIPS SP 800-38D)\n• Key Derivation: PBKDF2-HMAC-SHA256 (100,000 rounds)\n• Salt: 16-byte CSPRNG random\n• Nonce/IV: 12-byte unique per container\n• Tag: 128-bit Polyval authentication tag",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = {
                                if (isPasswordValid) {
                                    createDocLauncher.launch("${encryptState.fileName}.vcrypt")
                                } else if (password != confirmPassword) {
                                    Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Password must be at least 8 characters", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = isPasswordValid && !encryptState.isEncrypting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("start_encrypt_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Encrypt & Save .vcrypt Container", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (encryptState.isEncrypting) {
            item {
                ProgressCard(
                    title = "Encrypting Stream with AES-256-GCM",
                    progress = encryptState.progress,
                    statusText = "Deriving key and writing authenticated container..."
                )
            }
        }

        if (encryptState.result != null) {
            val res = encryptState.result!!
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = SecurityGreen,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ENCRYPTION SUCCESSFUL",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = SecurityGreen
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Authenticated VeriCrypt container saved successfully.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        var showTechnicalDetails by remember { mutableStateOf(false) }
                        OutlinedButton(
                            onClick = { showTechnicalDetails = !showTechnicalDetails },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("toggle_crypto_technical_details"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = if (showTechnicalDetails) Icons.Default.ExpandLess else Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (showTechnicalDetails) "Hide Technical Details" else "View Technical Details (Salt & Nonce)",
                                fontSize = 12.sp
                            )
                        }

                        if (showTechnicalDetails) {
                            Spacer(modifier = Modifier.height(10.dp))
                            MonospaceValueCard(
                                title = "UNIQUE 128-BIT SALT (HEX)",
                                value = res.saltHex,
                                testTag = "salt_hex_card"
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            MonospaceValueCard(
                                title = "UNIQUE 96-BIT GCM NONCE (HEX)",
                                value = res.ivHex,
                                testTag = "iv_hex_card"
                            )
                        }
                    }
                }
            }
        }

        if (encryptState.error != null) {
            item {
                Text(
                    text = encryptState.error ?: "",
                    color = SecurityRed,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun DecryptTab(
    decryptState: DecryptUiState,
    viewModel: CryptoViewModel
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectFileForDecryption(it) }
    }

    val saveRestoredLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { targetUri: Uri? ->
        val srcUri = decryptState.selectedUri
        if (targetUri != null && srcUri != null && password.isNotBlank()) {
            viewModel.decryptToFile(srcUri, targetUri, password)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Button(
                onClick = { filePicker.launch("*/*") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("select_file_for_decrypt_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FileOpen,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (decryptState.fileName.isBlank()) "Select .vcrypt Container" else "Change Selected Container",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (decryptState.fileName.isNotBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = decryptState.fileName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Container Size: ${decryptState.sizeBytes} bytes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Decryption Password",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Enter Container Password") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("decrypt_password_input"),
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                val defaultRestoredName = if (decryptState.fileName.endsWith(".vcrypt", ignoreCase = true)) {
                                    decryptState.fileName.removeSuffix(".vcrypt")
                                } else {
                                    "restored_${decryptState.fileName}"
                                }
                                saveRestoredLauncher.launch(defaultRestoredName)
                            },
                            enabled = password.isNotBlank() && !decryptState.isDecrypting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("start_decrypt_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.LockOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Decrypt & Save Output", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (decryptState.isDecrypting) {
            item {
                ProgressCard(
                    title = "Decrypting & Authenticating AES-GCM",
                    progress = decryptState.progress,
                    statusText = "Validating cryptographic integrity tag..."
                )
            }
        }

        if (decryptState.result != null) {
            val res = decryptState.result!!
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = SecurityGreen,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "DECRYPTION SUCCESSFUL",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = SecurityGreen
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Restored Original File: ${res.restoredFileName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Decrypted ${res.decryptedSizeBytes} bytes. GCM integrity tag verified valid.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (decryptState.isTamperDetected) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SecurityRed.copy(alpha = 0.15f))
                        .border(1.dp, SecurityRed, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                        .testTag("tamper_detected_alert")
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Tamper Alert",
                                tint = SecurityRed,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "TAMPER DETECTED / INCORRECT PASSWORD",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = SecurityRed
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "AES-GCM authentication check failed. Either the password is wrong, or the file contents have been modified, truncated, or corrupted. Decryption was aborted without emitting corrupt output.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        } else if (decryptState.error != null) {
            item {
                Text(
                    text = decryptState.error ?: "",
                    color = SecurityRed,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
