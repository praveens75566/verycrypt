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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.ui.components.EntropyVisualizer
import com.example.ui.components.MonospaceValueCard
import com.example.ui.components.ProgressCard
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SecurityAmber
import com.example.ui.theme.SecurityBlue
import com.example.ui.theme.SecurityGreen
import com.example.ui.theme.SecurityRed

@Composable
fun HashScreen(
    viewModel: HashViewModel,
    modifier: Modifier = Modifier
) {
    val singleState by viewModel.singleState.collectAsStateWithLifecycle()
    val textState by viewModel.textState.collectAsStateWithLifecycle()
    val batchState by viewModel.batchState.collectAsStateWithLifecycle()
    val manifestState by viewModel.manifestState.collectAsStateWithLifecycle()
    val dedupState by viewModel.dedupState.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Single File", "Direct Text", "ZIP Deduplication", "Batch Files", "Manifest", "CSPRNG Generator")

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("hash_screen")
    ) {
        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 12.dp
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.testTag("hash_tab_$index")
                )
            }
        }

        when (selectedTabIndex) {
            0 -> SingleFileHashTab(singleState = singleState, viewModel = viewModel)
            1 -> DirectTextHashTab(textState = textState, viewModel = viewModel)
            2 -> ArchiveDeduplicationTab(dedupState = dedupState, viewModel = viewModel)
            3 -> BatchHashTab(batchState = batchState, viewModel = viewModel)
            4 -> FolderManifestTab(manifestState = manifestState, viewModel = viewModel)
            5 -> {
                val tokenViewModel: CsprngTokenViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                CsprngTokenScreen(viewModel = tokenViewModel)
            }
        }
    }
}

@Composable
private fun DirectTextHashTab(
    textState: TextHashState,
    viewModel: HashViewModel
) {
    val context = LocalContext.current

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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Direct Raw Text Hashing",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${textState.lengthChars} chars • ${textState.sizeBytes} bytes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = textState.rawText,
                        onValueChange = { viewModel.updateRawText(it) },
                        label = { Text("Type or paste raw text snippet to hash") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .testTag("direct_text_input"),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val item = cb.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                viewModel.updateRawText(item)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Paste Text")
                        }
                        OutlinedButton(
                            onClick = { viewModel.updateRawText("") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear")
                        }
                    }
                }
            }
        }

        if (textState.sha256Hex.isNotBlank()) {
            item {
                MonospaceValueCard(
                    title = "SHA-256 (256-BIT CHECKSUM)",
                    value = textState.sha256Hex,
                    subtitle = "Algorithm: SHA-256 (64 hex characters)",
                    testTag = "text_sha256_result"
                )
            }

            item {
                MonospaceValueCard(
                    title = "SHA-512 (512-BIT CHECKSUM)",
                    value = textState.sha512Hex,
                    subtitle = "Algorithm: SHA-512 (128 hex characters)",
                    testTag = "text_sha512_result"
                )
            }

            textState.entropyResult?.let { entropy ->
                item {
                    EntropyVisualizer(
                        result = entropy,
                        title = "Text Shannon Entropy & Randomness"
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
                            text = "Verify Against Expected Hash",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = textState.targetCompareHash,
                            onValueChange = { viewModel.updateTextCompareTarget(it) },
                            label = { Text("Paste SHA-256 or SHA-512 target hash") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )

                        if (textState.compareResult != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            val isMatch = textState.compareResult == true
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isMatch) SecurityGreen.copy(alpha = 0.15f)
                                        else SecurityRed.copy(alpha = 0.15f)
                                    )
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isMatch) Icons.Default.CheckCircle else Icons.Default.Error,
                                        contentDescription = null,
                                        tint = if (isMatch) SecurityGreen else SecurityRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isMatch) "MATCH: Raw text matches target checksum." else "MISMATCH: Checksum does not match.",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isMatch) SecurityGreen else SecurityRed
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveDeduplicationTab(
    dedupState: DeduplicationState,
    viewModel: HashViewModel
) {
    val context = LocalContext.current

    val zipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.scanZipArchive(it) }
    }

    val exportCleanZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { targetUri: Uri? ->
        targetUri?.let {
            viewModel.exportCleanDeduplicatedZip(it)
            Toast.makeText(context, "Exporting deduplicated archive...", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Button(
                onClick = { zipPicker.launch("application/zip") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("select_zip_to_scan_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.FolderZip, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (dedupState.scanResult == null) "Upload ZIP Archive or Folder" else "Choose Different Archive",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (dedupState.isScanning) {
            item {
                ProgressCard(
                    title = "Unpacking & Streaming Hashes",
                    progress = 0.5f,
                    statusText = "Computing cryptographic SHA-256 for all individual archive items..."
                )
            }
        }

        if (dedupState.scanResult != null) {
            val res = dedupState.scanResult!!

            // Statistics Dashboard Grid
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "ARCHIVE DEDUPLICATION DASHBOARD",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatBox(label = "Total Entries", value = "${res.totalEntries}", modifier = Modifier.weight(1f))
                            StatBox(label = "Unique Files", value = "${res.uniqueCount}", modifier = Modifier.weight(1f), isSuccess = true)
                            StatBox(label = "Duplicates", value = "${res.duplicateCount}", modifier = Modifier.weight(1f), isAlert = res.duplicateCount > 0)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Reclaimable Redundant Space", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = formatFileSize(res.wastedSizeBytes),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (res.wastedSizeBytes > 0) SecurityAmber else SecurityGreen
                                    )
                                }
                                Text("Total: ${formatFileSize(res.totalSizeBytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        if (res.duplicateCount > 0) {
                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    val cleanName = res.archiveName.removeSuffix(".zip") + "_deduplicated.zip"
                                    exportCleanZipLauncher.launch(cleanName)
                                },
                                enabled = !dedupState.isDeduplicating,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("one_click_dedup_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = SecurityGreen),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.CleaningServices, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("One-Click Remove Duplicates & Save ZIP", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                }
            }

            if (dedupState.cleanedEntriesCount > 0) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SecurityGreen.copy(alpha = 0.15f))
                            .border(1.dp, SecurityGreen, RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = SecurityGreen, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Archive Cleaned Successfully", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = SecurityGreen)
                                Text("Saved clean ZIP with ${dedupState.cleanedEntriesCount} unique files. All duplicate payloads removed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }

            if (res.duplicateClusters.isNotEmpty()) {
                item {
                    Text(
                        text = "DUPLICATE CLUSTERS DETECTED (${res.duplicateClusters.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                items(res.duplicateClusters) { cluster ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Original: ${cluster.originalName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = formatFileSize(cluster.sizeBytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "SHA-256: ${cluster.sha256Hex.take(16)}...",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Identical Duplicate Copies (${cluster.duplicateNames.size}):",
                                style = MaterialTheme.typography.labelSmall,
                                color = SecurityAmber,
                                fontWeight = FontWeight.Bold
                            )
                            cluster.duplicateNames.forEach { dup ->
                                Text(" • $dup", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isSuccess: Boolean = false,
    isAlert: Boolean = false
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = JetBrainsMonoFamily,
                color = when {
                    isAlert -> SecurityAmber
                    isSuccess -> SecurityGreen
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
private fun SingleFileHashTab(
    singleState: SingleHashState,
    viewModel: HashViewModel
) {
    val context = LocalContext.current
    val singleFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectSingleFile(it) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Button(
                onClick = { singleFileLauncher.launch("*/*") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("select_file_to_hash_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FileOpen,
                    contentDescription = "Pick file",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (singleState.fileName.isBlank()) "Select File to Hash" else "Choose Different File",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (singleState.fileName.isNotBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = singleState.fileName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Size: ${formatFileSize(singleState.sizeBytes)} (${singleState.sizeBytes} bytes)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (singleState.isHashing) {
            item {
                ProgressCard(
                    title = "Computing SHA-256 & SHA-512 Digests",
                    progress = singleState.progress,
                    statusText = "Streaming file chunks through Java Cryptography Architecture..."
                )
            }
        }

        if (singleState.sha256Hex.isNotBlank()) {
            item {
                MonospaceValueCard(
                    title = "COMPUTED SHA-256 CHECKSUM",
                    value = singleState.sha256Hex,
                    subtitle = "Algorithm: SHA-256 (256-bit hash)",
                    isMatch = singleState.compareResult,
                    testTag = "single_sha256_result"
                )
            }

            if (singleState.sha512Hex.isNotBlank()) {
                item {
                    MonospaceValueCard(
                        title = "COMPUTED SHA-512 CHECKSUM",
                        value = singleState.sha512Hex,
                        subtitle = "Algorithm: SHA-512 (512-bit standard hash)",
                        testTag = "single_sha512_result"
                    )
                }
            }

            singleState.entropyResult?.let { entropy ->
                item {
                    EntropyVisualizer(
                        result = entropy,
                        title = "File Shannon Entropy & Randomness"
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
                            text = "Integrity Verification",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Compare against expected hash or vendor checksum",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = singleState.targetCompareHash,
                            onValueChange = { viewModel.updateTargetCompareHash(it) },
                            label = { Text("Paste Expected Hash (SHA-256 or SHA-512)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("compare_hash_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val item = clipboard.primaryClip?.getItemAt(0)
                                    val pasteText = item?.text?.toString() ?: ""
                                    viewModel.updateTargetCompareHash(pasteText.trim())
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("paste_clipboard_hash_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Paste Clipboard")
                            }
                        }

                        if (singleState.compareResult != null) {
                            Spacer(modifier = Modifier.height(14.dp))
                            val isMatch = singleState.compareResult == true
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isMatch) SecurityGreen.copy(alpha = 0.12f)
                                        else SecurityRed.copy(alpha = 0.12f)
                                    )
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isMatch) Icons.Default.CheckCircle else Icons.Default.Error,
                                        contentDescription = null,
                                        tint = if (isMatch) SecurityGreen else SecurityRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isMatch) {
                                            "AUTHENTIC: File contents match expected checksum exactly."
                                        } else {
                                            "MISMATCH DETECTED: File contents differ or have been tampered with."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isMatch) SecurityGreen else SecurityRed
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (singleState.error != null) {
            item {
                Text(
                    text = singleState.error ?: "",
                    color = SecurityRed,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun BatchHashTab(
    batchState: BatchHashState,
    viewModel: HashViewModel
) {
    val context = LocalContext.current
    val batchFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.processBatchUris(uris)
        }
    }

    val exportReportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null && batchState.checksumReport.isNotBlank()) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(batchState.checksumReport.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Checksum report saved successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to save report: ${e.message}", Toast.LENGTH_SHORT).show()
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
                onClick = { batchFileLauncher.launch("*/*") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("select_batch_files_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Pick files",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "Select Multiple Files to Hash", fontWeight = FontWeight.Bold)
            }
        }

        if (batchState.isProcessing) {
            item {
                ProgressCard(
                    title = "Processing Batch Files",
                    progress = if (batchState.totalFiles > 0) batchState.currentFileIndex.toFloat() / batchState.totalFiles else 0f,
                    statusText = "Hashing file ${batchState.currentFileIndex} of ${batchState.totalFiles}..."
                )
            }
        }

        if (batchState.duplicates.isNotEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SecurityAmber.copy(alpha = 0.15f))
                        .border(1.dp, SecurityAmber.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Duplicate warning",
                            tint = SecurityAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${batchState.duplicates.size} duplicate file set(s) detected with identical content hashes.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = SecurityAmber
                        )
                    }
                }
            }
        }

        if (batchState.results.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BATCH RESULTS (${batchState.results.size} FILES)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedButton(
                        onClick = { exportReportLauncher.launch("checksums.sha256") },
                        modifier = Modifier.testTag("export_checksum_report_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export .sha256")
                    }
                }
            }

            items(batchState.results) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = item.fileName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formatFileSize(item.sizeBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = item.sha256Hex,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderManifestTab(
    manifestState: ManifestState,
    viewModel: HashViewModel
) {
    val context = LocalContext.current
    val manifestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.processFolderManifest(uris)
        }
    }

    val exportManifestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null && manifestState.manifestResult != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(manifestState.manifestResult!!.manifestText.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Manifest file saved successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to save manifest: ${e.message}", Toast.LENGTH_SHORT).show()
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
                onClick = { manifestLauncher.launch("*/*") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("select_folder_manifest_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tag,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Select Files to Build Manifest", fontWeight = FontWeight.Bold)
            }
        }

        if (manifestState.manifestResult != null) {
            val manifest = manifestState.manifestResult!!
            item {
                MonospaceValueCard(
                    title = "DETERMINISTIC COMPOSITE ROOT HASH",
                    value = manifest.compositeRootHash,
                    subtitle = "Computed over sorted manifest of ${manifest.totalFiles} files (${formatFileSize(manifest.totalSizeBytes)})",
                    testTag = "manifest_root_hash"
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MANIFEST DETAILS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedButton(
                        onClick = { exportManifestLauncher.launch("folder_manifest.sha256") },
                        modifier = Modifier.testTag("export_manifest_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Manifest")
                    }
                }
            }

            items(manifest.entries) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.relativePath,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = entry.sha256Hex,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = formatFileSize(entry.sizeBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(java.util.Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
