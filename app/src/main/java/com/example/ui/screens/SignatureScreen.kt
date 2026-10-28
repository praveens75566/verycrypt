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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.KeyPairMeta
import com.example.ui.components.MonospaceValueCard
import com.example.ui.theme.SecurityGreen
import com.example.ui.theme.SecurityRed

@Composable
fun SignatureScreen(
    viewModel: SignatureViewModel,
    modifier: Modifier = Modifier
) {
    val savedKeys by viewModel.savedKeys.collectAsStateWithLifecycle()
    val signState by viewModel.signState.collectAsStateWithLifecycle()
    val verifyState by viewModel.verifyState.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Sign File", "Verify Signature", "Key Management")

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("signature_screen")
    ) {
        com.example.ui.components.ModernSegmentedControl(
            items = tabs,
            selectedIndex = selectedTabIndex,
            onItemSelected = { selectedTabIndex = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            testTagPrefix = "signature_tab"
        )

        when (selectedTabIndex) {
            0 -> SignTab(signState = signState, savedKeys = savedKeys, viewModel = viewModel)
            1 -> VerifyTab(verifyState = verifyState, savedKeys = savedKeys, viewModel = viewModel)
            2 -> KeyManagementTab(savedKeys = savedKeys, viewModel = viewModel)
        }
    }
}

@Composable
private fun SignTab(
    signState: SignUiState,
    savedKeys: List<KeyPairMeta>,
    viewModel: SignatureViewModel
) {
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectFileToSign(it) }
    }

    val exportSigLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null && signState.signatureResult != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(signState.signatureResult!!.signatureBytes)
                }
                Toast.makeText(context, "Detached signature saved (.sig)", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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
                    .testTag("select_file_to_sign_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.FileOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (signState.fileName.isBlank()) "Select File to Sign" else "Change Selected File",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (signState.fileName.isNotBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = signState.fileName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Size: ${signState.sizeBytes} bytes",
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
                            text = "Select Signing Key Pair",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "ECDSA with NIST P-256 curve & SHA-256 digest",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (savedKeys.isEmpty()) {
                            Text(
                                text = "No Keystore keys generated yet. Please switch to the 'Key Management' tab to generate a key pair.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SecurityRed
                            )
                        } else {
                            savedKeys.forEach { key ->
                                val isSelected = key.alias == signState.selectedAlias
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { viewModel.setSelectedAlias(key.alias) }
                                        .border(
                                            1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outlineVariant,
                                            RoundedCornerShape(8.dp)
                                        ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Key,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = key.alias,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Fingerprint: ${key.fingerprintSha256.take(17)}...",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { viewModel.signSelectedFile() },
                                enabled = signState.selectedAlias.isNotBlank() && !signState.isSigning,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("execute_sign_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = if (signState.isSigning) "Signing..." else "Generate Detached Signature",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        if (signState.signatureResult != null) {
            val res = signState.signatureResult!!
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
                            Text(
                                text = "DETACHED SIGNATURE GENERATED",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = SecurityGreen
                            )
                            OutlinedButton(
                                onClick = { exportSigLauncher.launch("${signState.fileName}.sig") },
                                modifier = Modifier.testTag("export_sig_file_button")
                            ) {
                                Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export .sig")
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        MonospaceValueCard(
                            title = "SIGNATURE (BASE64)",
                            value = res.signatureBase64,
                            testTag = "signature_base64_card"
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        MonospaceValueCard(
                            title = "SIGNATURE (RAW HEX)",
                            value = res.signatureHex,
                            testTag = "signature_hex_card"
                        )
                    }
                }
            }
        }

        if (signState.error != null) {
            item {
                Text(text = signState.error ?: "", color = SecurityRed)
            }
        }
    }
}

@Composable
private fun VerifyTab(
    verifyState: VerifyUiState,
    savedKeys: List<KeyPairMeta>,
    viewModel: SignatureViewModel
) {
    val context = LocalContext.current
    val targetFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectFileToVerify(it) }
    }

    val sigFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) {
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    viewModel.setVerifySignature(base64)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read .sig file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Button(
                onClick = { targetFilePicker.launch("*/*") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("select_file_to_verify_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.FileOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (verifyState.fileName.isBlank()) "Select Target File to Verify" else "File: ${verifyState.fileName}",
                    fontWeight = FontWeight.Bold
                )
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
                        text = "Detached Signature",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = verifyState.signatureBase64,
                        onValueChange = { viewModel.setVerifySignature(it) },
                        label = { Text("Paste Signature (Base64)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("verify_sig_input"),
                        maxLines = 3,
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { sigFilePicker.launch("*/*") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Load .sig File")
                        }
                        OutlinedButton(
                            onClick = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = cb.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                viewModel.setVerifySignature(clip)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Paste")
                        }
                    }
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
                        text = "Public Key (PEM)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (savedKeys.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Use Saved:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            savedKeys.take(3).forEach { key ->
                                Text(
                                    text = key.alias,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { viewModel.setVerifyPublicKeyPem(key.publicKeyPem) }
                                        .padding(horizontal = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    OutlinedTextField(
                        value = verifyState.publicKeyPem,
                        onValueChange = { viewModel.setVerifyPublicKeyPem(it) },
                        label = { Text("Paste -----BEGIN PUBLIC KEY-----") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("verify_pem_input"),
                        maxLines = 4,
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.verifyFileSignature() },
                        enabled = verifyState.selectedFileUri != null &&
                                verifyState.signatureBase64.isNotBlank() &&
                                verifyState.publicKeyPem.isNotBlank() &&
                                !verifyState.isVerifying,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("execute_verify_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (verifyState.isVerifying) "Verifying..." else "Verify Signature Integrity",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (verifyState.verificationResult != null) {
            val res = verifyState.verificationResult!!
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (res.isValid) SecurityGreen.copy(alpha = 0.15f)
                            else SecurityRed.copy(alpha = 0.15f)
                        )
                        .border(
                            1.dp,
                            if (res.isValid) SecurityGreen else SecurityRed,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (res.isValid) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (res.isValid) SecurityGreen else SecurityRed,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (res.isValid) "SIGNATURE VALID & AUTHENTIC" else "SIGNATURE VERIFICATION FAILED",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (res.isValid) SecurityGreen else SecurityRed
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = res.details,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        if (verifyState.error != null) {
            item {
                Text(text = verifyState.error ?: "", color = SecurityRed)
            }
        }
    }
}

@Composable
private fun KeyManagementTab(
    savedKeys: List<KeyPairMeta>,
    viewModel: SignatureViewModel
) {
    val context = LocalContext.current
    var showGenerateDialog by remember { mutableStateOf(false) }
    var newAliasInput by remember { mutableStateOf("") }

    if (showGenerateDialog) {
        AlertDialog(
            onDismissRequest = { showGenerateDialog = false },
            title = { Text("Generate ECDSA P-256 Key Pair") },
            text = {
                Column {
                    Text(
                        text = "Keys are securely generated and bound inside AndroidKeyStore with hardware-backed protection where available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newAliasInput,
                        onValueChange = { newAliasInput = it },
                        label = { Text("Key Alias / Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("key_alias_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newAliasInput.isNotBlank()) {
                            viewModel.generateNewKeyPair(newAliasInput)
                            showGenerateDialog = false
                            newAliasInput = ""
                        }
                    },
                    modifier = Modifier.testTag("confirm_generate_key_button")
                ) {
                    Text("Generate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGenerateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    var showImportDialog by remember { mutableStateOf(false) }
    var importAliasInput by remember { mutableStateOf("") }
    var importPemInput by remember { mutableStateOf("") }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Public Key (PEM)") },
            text = {
                Column {
                    Text(
                        text = "Import an external ECDSA public key in standard PEM format for signature verification.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = importAliasInput,
                        onValueChange = { importAliasInput = it },
                        label = { Text("Key Alias / Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = importPemInput,
                        onValueChange = { importPemInput = it },
                        label = { Text("Paste -----BEGIN PUBLIC KEY-----") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importAliasInput.isNotBlank() && importPemInput.isNotBlank()) {
                            viewModel.importExternalPublicKey(importAliasInput, importPemInput)
                            showImportDialog = false
                            importAliasInput = ""
                            importPemInput = ""
                        }
                    }
                ) {
                    Text("Import Key")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showGenerateDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("open_generate_key_dialog_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Generate Key", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = { showImportDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("open_import_key_dialog_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import PEM", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        item {
            Text(
                text = "STORED KEY PAIRS (${savedKeys.size})",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (savedKeys.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No key pairs found in Android Keystore",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(savedKeys, key = { it.alias }) { key ->
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
                            Column {
                                Text(
                                    text = key.alias,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${key.algorithm} • ${key.curve}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { viewModel.deleteKey(key.alias) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete key",
                                    tint = SecurityRed,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        MonospaceValueCard(
                            title = "SHA-256 FINGERPRINT",
                            value = key.fingerprintSha256,
                            testTag = "fingerprint_${key.alias}"
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        MonospaceValueCard(
                            title = "PUBLIC KEY (PEM)",
                            value = key.publicKeyPem,
                            testTag = "pem_${key.alias}"
                        )
                    }
                }
            }
        }
    }
}
