package com.example.ui.screens

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.model.AuditAction
import com.example.data.model.AuditEvent
import com.example.data.model.AuditStatus
import com.example.security.ArchiveDeduplicationEngine
import com.example.security.ArchiveScanResult
import com.example.security.FileHashResult
import com.example.security.FolderManifestEntry
import com.example.security.FolderManifestResult
import com.example.security.HashingEngine
import com.example.security.EntropyEngine
import com.example.security.EntropyResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SingleHashState(
    val selectedUri: Uri? = null,
    val fileName: String = "",
    val sizeBytes: Long = 0L,
    val sha256Hex: String = "",
    val sha512Hex: String = "",
    val targetCompareHash: String = "",
    val isHashing: Boolean = false,
    val progress: Float = 0f,
    val compareResult: Boolean? = null,
    val entropyResult: EntropyResult? = null,
    val error: String? = null
)

data class TextHashState(
    val rawText: String = "",
    val lengthChars: Int = 0,
    val sizeBytes: Int = 0,
    val sha256Hex: String = "",
    val sha512Hex: String = "",
    val targetCompareHash: String = "",
    val compareResult: Boolean? = null,
    val entropyResult: EntropyResult? = null
)

data class BatchHashState(
    val results: List<FileHashResult> = emptyList(),
    val isProcessing: Boolean = false,
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 0,
    val duplicates: Map<String, List<FileHashResult>> = emptyMap(),
    val checksumReport: String = "",
    val error: String? = null
)

data class ManifestState(
    val isProcessing: Boolean = false,
    val manifestResult: FolderManifestResult? = null,
    val error: String? = null
)

data class DeduplicationState(
    val selectedZipUri: Uri? = null,
    val isScanning: Boolean = false,
    val scanResult: ArchiveScanResult? = null,
    val isDeduplicating: Boolean = false,
    val cleanedEntriesCount: Int = 0,
    val error: String? = null
)

class HashViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val context = application.applicationContext

    private val _singleState = MutableStateFlow(SingleHashState())
    val singleState: StateFlow<SingleHashState> = _singleState.asStateFlow()

    private val _textState = MutableStateFlow(TextHashState())
    val textState: StateFlow<TextHashState> = _textState.asStateFlow()

    private val _batchState = MutableStateFlow(BatchHashState())
    val batchState: StateFlow<BatchHashState> = _batchState.asStateFlow()

    private val _manifestState = MutableStateFlow(ManifestState())
    val manifestState: StateFlow<ManifestState> = _manifestState.asStateFlow()

    private val _dedupState = MutableStateFlow(DeduplicationState())
    val dedupState: StateFlow<DeduplicationState> = _dedupState.asStateFlow()

    fun selectSingleFile(uri: Uri) {
        val (name, size) = HashingEngine.getUriMetadata(context, uri)
        _singleState.update {
            it.copy(
                selectedUri = uri,
                fileName = name,
                sizeBytes = size,
                sha256Hex = "",
                sha512Hex = "",
                progress = 0f,
                compareResult = null,
                error = null
            )
        }
        computeSingleFileHash(uri)
    }

    fun computeSingleFileHash(uri: Uri) {
        viewModelScope.launch {
            _singleState.update { it.copy(isHashing = true, error = null, progress = 0f) }
            try {
                val (name, size) = HashingEngine.getUriMetadata(context, uri)

                val inStream256 = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Cannot read $name")
                val sha256 = HashingEngine.computeSha256(inStream256, size) { prog ->
                    _singleState.update { it.copy(progress = prog * 0.5f) }
                }

                val inStream512 = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Cannot read $name")
                val sha512 = HashingEngine.computeSha512(inStream512, size) { prog ->
                    _singleState.update { it.copy(progress = 0.5f + prog * 0.5f) }
                }

                // Compute Shannon Entropy from file byte stream
                val entropyStream = context.contentResolver.openInputStream(uri)
                val frequencies = IntArray(256)
                var totalBytesAccum = 0
                val buf = ByteArray(32768)
                entropyStream?.use { stream ->
                    var r: Int
                    while (stream.read(buf).also { r = it } != -1) {
                        for (i in 0 until r) {
                            frequencies[buf[i].toInt() and 0xFF]++
                            totalBytesAccum++
                        }
                    }
                }
                val entropyRes = EntropyEngine.calculateFromFrequencies(frequencies, totalBytesAccum)

                _singleState.update {
                    it.copy(
                        sha256Hex = sha256,
                        sha512Hex = sha512,
                        isHashing = false,
                        progress = 1f,
                        entropyResult = entropyRes,
                        compareResult = if (it.targetCompareHash.isNotBlank()) {
                            HashingEngine.compareHashes(sha256, it.targetCompareHash) ||
                                    HashingEngine.compareHashes(sha512, it.targetCompareHash)
                        } else null
                    )
                }

                db.auditDao().insertEvent(
                    AuditEvent(
                        action = AuditAction.HASH,
                        itemName = name,
                        status = AuditStatus.SUCCESS,
                        details = "SHA-256 & SHA-512 computed. Size: $size B. Entropy: ${String.format(java.util.Locale.US, "%.2f", entropyRes.entropyBitsPerByte)} b/B"
                    )
                )
            } catch (e: Exception) {
                _singleState.update {
                    it.copy(isHashing = false, error = e.localizedMessage ?: "Failed to compute hash")
                }
            }
        }
    }

    fun updateTargetCompareHash(target: String) {
        _singleState.update { current ->
            val match = if (target.isNotBlank()) {
                HashingEngine.compareHashes(current.sha256Hex, target) ||
                        HashingEngine.compareHashes(current.sha512Hex, target)
            } else null

            current.copy(
                targetCompareHash = target,
                compareResult = match
            )
        }
    }

    // Direct Text Hashing
    fun updateRawText(text: String) {
        if (text.isEmpty()) {
            _textState.value = TextHashState()
            return
        }
        val result = HashingEngine.hashTextDirectly(text)
        val entropy = EntropyEngine.calculateTextEntropy(text)
        val match = if (_textState.value.targetCompareHash.isNotBlank()) {
            HashingEngine.compareHashes(result.sha256Hex, _textState.value.targetCompareHash) ||
                    HashingEngine.compareHashes(result.sha512Hex, _textState.value.targetCompareHash)
        } else null

        _textState.update {
            it.copy(
                rawText = text,
                lengthChars = result.lengthChars,
                sizeBytes = result.sizeBytes,
                sha256Hex = result.sha256Hex,
                sha512Hex = result.sha512Hex,
                compareResult = match,
                entropyResult = entropy
            )
        }
    }

    fun updateTextCompareTarget(target: String) {
        _textState.update { current ->
            val match = if (target.isNotBlank()) {
                HashingEngine.compareHashes(current.sha256Hex, target) ||
                        HashingEngine.compareHashes(current.sha512Hex, target)
            } else null
            current.copy(targetCompareHash = target, compareResult = match)
        }
    }

    // Batch File Processing
    fun processBatchUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _batchState.update {
                it.copy(
                    isProcessing = true,
                    totalFiles = uris.size,
                    currentFileIndex = 0,
                    error = null
                )
            }

            try {
                val results = mutableListOf<FileHashResult>()
                uris.forEachIndexed { index, uri ->
                    _batchState.update { it.copy(currentFileIndex = index + 1) }
                    val item = HashingEngine.hashUri(context, uri)
                    results.add(item)
                }

                val duplicates = HashingEngine.findDuplicates(results)
                val report = HashingEngine.generateChecksumReport(results)

                _batchState.update {
                    it.copy(
                        results = results,
                        isProcessing = false,
                        duplicates = duplicates,
                        checksumReport = report
                    )
                }

                db.auditDao().insertEvent(
                    AuditEvent(
                        action = AuditAction.HASH,
                        itemName = "Batch (${results.size} files)",
                        status = AuditStatus.SUCCESS,
                        details = "Processed ${results.size} files, ${duplicates.size} duplicate sets detected."
                    )
                )
            } catch (e: Exception) {
                _batchState.update {
                    it.copy(isProcessing = false, error = e.localizedMessage ?: "Batch hashing failed")
                }
            }
        }
    }

    // Folder Manifest Processing
    fun processFolderManifest(uris: List<Uri>, folderName: String = "Selected_Folder") {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _manifestState.update { it.copy(isProcessing = true, error = null) }
            try {
                val entries = uris.map { uri ->
                    val (name, size) = HashingEngine.getUriMetadata(context, uri)
                    val hash = HashingEngine.hashUri(context, uri).sha256Hex
                    FolderManifestEntry(
                        relativePath = name,
                        sizeBytes = size,
                        sha256Hex = hash
                    )
                }

                val result = HashingEngine.computeFolderManifest(entries, folderName)
                _manifestState.update {
                    it.copy(
                        isProcessing = false,
                        manifestResult = result
                    )
                }

                db.auditDao().insertEvent(
                    AuditEvent(
                        action = AuditAction.HASH,
                        itemName = folderName,
                        status = AuditStatus.SUCCESS,
                        details = "Folder manifest generated: ${result.totalFiles} files, Root: ${result.compositeRootHash.take(12)}..."
                    )
                )
            } catch (e: Exception) {
                _manifestState.update {
                    it.copy(isProcessing = false, error = e.localizedMessage ?: "Manifest generation failed")
                }
            }
        }
    }

    // Archive Deduplication Scanning
    fun scanZipArchive(zipUri: Uri) {
        viewModelScope.launch {
            _dedupState.update {
                it.copy(
                    selectedZipUri = zipUri,
                    isScanning = true,
                    scanResult = null,
                    error = null
                )
            }

            try {
                val result = ArchiveDeduplicationEngine.scanZipArchive(context, zipUri)
                _dedupState.update {
                    it.copy(
                        isScanning = false,
                        scanResult = result
                    )
                }

                db.auditDao().insertEvent(
                    AuditEvent(
                        action = AuditAction.HASH_COMPARE,
                        itemName = result.archiveName,
                        status = AuditStatus.SUCCESS,
                        details = "Scanned ${result.totalEntries} entries. Found ${result.duplicateCount} duplicates (${result.wastedSizeBytes / 1024} KB wasted)."
                    )
                )
            } catch (e: Exception) {
                _dedupState.update {
                    it.copy(isScanning = false, error = e.localizedMessage ?: "ZIP scanning failed")
                }
            }
        }
    }

    fun exportCleanDeduplicatedZip(destinationUri: Uri) {
        val srcUri = _dedupState.value.selectedZipUri ?: return
        viewModelScope.launch {
            _dedupState.update { it.copy(isDeduplicating = true, error = null) }
            try {
                val outputStream = context.contentResolver.openOutputStream(destinationUri)
                    ?: throw IllegalStateException("Cannot open destination for deduplicated ZIP")

                val preservedCount = ArchiveDeduplicationEngine.createDeduplicatedZip(context, srcUri, outputStream)
                _dedupState.update {
                    it.copy(
                        isDeduplicating = false,
                        cleanedEntriesCount = preservedCount
                    )
                }

                db.auditDao().insertEvent(
                    AuditEvent(
                        action = AuditAction.HASH_COMPARE,
                        itemName = _dedupState.value.scanResult?.archiveName ?: "Archive",
                        status = AuditStatus.SUCCESS,
                        details = "Deduplicated ZIP generated successfully with $preservedCount unique files."
                    )
                )
            } catch (e: Exception) {
                _dedupState.update {
                    it.copy(isDeduplicating = false, error = e.localizedMessage ?: "Failed to clean ZIP")
                }
            }
        }
    }
}
