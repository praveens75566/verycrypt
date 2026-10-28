package com.example.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.UserPreferencesRepository
import com.example.data.model.AuditEvent
import com.example.security.AppLockManager
import com.example.security.AuditEngine
import com.example.security.AuditRating
import com.example.security.AuditReport
import com.example.security.BenchmarkEngine
import com.example.security.BenchmarkSuiteResult
import com.example.security.CsprngTokenEngine
import com.example.security.GeneratedTokenResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val recentAudits: List<AuditEvent> = emptyList(),
    val filteredAudits: List<AuditEvent> = emptyList(),
    val totalAuditsCount: Int = 0,
    val totalVaultItems: Int = 0,
    val totalVaultSizeBytes: Long = 0L,
    val totalKeyPairs: Int = 0,
    val isBiometricsAvailable: Boolean = false,
    val isAppLockConfigured: Boolean = false,
    val securityScore: Int = 0,
    val securityRating: AuditRating = AuditRating.ADEQUATE,
    val searchQuery: String = "",
    val activeAuditReport: AuditReport? = null,
    val isBenchmarking: Boolean = false,
    val isBenchmarkDialogOpen: Boolean = false,
    val benchmarkResult: BenchmarkSuiteResult? = null,
    val isWelcomeBannerVisible: Boolean = true,
    val isQuickPasswordDialogOpen: Boolean = false,
    val quickPasswordResult: GeneratedTokenResult? = null,
    val quickPasswordLength: Int = 16,
    val quickPasswordIncludeSymbols: Boolean = true
)

private data class VaultStats(val count: Int, val bytes: Long)
private data class QuickPassState(
    val isWelcomeVisible: Boolean,
    val isDialogOpen: Boolean,
    val result: GeneratedTokenResult?,
    val length: Int,
    val symbols: Boolean
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val prefs = UserPreferencesRepository(application)
    private val context = application.applicationContext

    private val searchQueryFlow = MutableStateFlow("")
    private val activeReportFlow = MutableStateFlow<AuditReport?>(null)
    private val isBenchmarkingFlow = MutableStateFlow(false)
    private val isBenchmarkDialogOpenFlow = MutableStateFlow(false)
    private val benchmarkResultFlow = MutableStateFlow<BenchmarkSuiteResult?>(null)

    private val welcomeBannerVisibleFlow = MutableStateFlow(true)
    private val quickPasswordDialogOpenFlow = MutableStateFlow(false)
    private val quickPasswordLengthFlow = MutableStateFlow(16)
    private val quickPasswordIncludeSymbolsFlow = MutableStateFlow(true)
    private val quickPasswordResultFlow = MutableStateFlow<GeneratedTokenResult?>(null)

    private val quickActionFlow = combine(
        welcomeBannerVisibleFlow,
        quickPasswordDialogOpenFlow,
        quickPasswordResultFlow,
        quickPasswordLengthFlow,
        quickPasswordIncludeSymbolsFlow
    ) { welcome, open, result, len, sym ->
        QuickPassState(welcome, open, result, len, sym)
    }

    private val vaultStatsFlow = combine(
        db.vaultDao().getVaultItemCount(),
        db.vaultDao().getTotalVaultSizeBytes()
    ) { count, bytes ->
        VaultStats(count, bytes ?: 0L)
    }

    private val auditStatsFlow = combine(
        db.auditDao().getRecentAuditEvents(),
        db.auditDao().getAuditEventCount()
    ) { audits, count -> Pair(audits, count) }

    private val systemStatsFlow = combine(
        db.keyPairDao().getKeyPairCount(),
        prefs.userSettings
    ) { keyCount, settings -> Pair(keyCount, settings) }

    private val benchmarkStateFlow = combine(
        isBenchmarkingFlow,
        isBenchmarkDialogOpenFlow,
        benchmarkResultFlow
    ) { b1, b2, b3 -> Triple(b1, b2, b3) }

    private val filterAndQuickFlow = combine(
        searchQueryFlow,
        activeReportFlow,
        quickActionFlow
    ) { query, report, quickPass ->
        Triple(query, report, quickPass)
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        auditStatsFlow,
        vaultStatsFlow,
        systemStatsFlow,
        benchmarkStateFlow,
        filterAndQuickFlow
    ) { auditStats, vaultStats, sysStats, benchTriple, filterAndQuick ->
        val audits = auditStats.first
        val auditCount = auditStats.second
        val keyCount = sysStats.first
        val settings = sysStats.second
        val query = filterAndQuick.first
        val report = filterAndQuick.second
        val quickPass = filterAndQuick.third

        val isBio = AppLockManager.isBiometricHardwareAvailable(context)
        val isPin = settings.hasPinConfigured

        // Calculate initial baseline security score
        var score = 40
        if (isPin) score += 25
        if (isBio) score += 20
        if (vaultStats.count > 0) score += 10
        if (keyCount > 0) score += 5
        val clampedScore = score.coerceIn(0, 100)
        val rating = when {
            clampedScore >= 90 -> AuditRating.EXCELLENT
            clampedScore >= 70 -> AuditRating.GOOD
            clampedScore >= 50 -> AuditRating.ADEQUATE
            else -> AuditRating.VULNERABLE
        }

        val filtered = if (query.isBlank()) {
            audits.take(8)
        } else {
            audits.filter {
                it.action.name.contains(query, ignoreCase = true) ||
                    it.itemName.contains(query, ignoreCase = true) ||
                    it.details.contains(query, ignoreCase = true)
            }
        }

        DashboardUiState(
            recentAudits = audits.take(8),
            filteredAudits = filtered,
            totalAuditsCount = auditCount,
            totalVaultItems = vaultStats.count,
            totalVaultSizeBytes = vaultStats.bytes,
            totalKeyPairs = keyCount,
            isBiometricsAvailable = isBio,
            isAppLockConfigured = isPin,
            securityScore = clampedScore,
            securityRating = rating,
            searchQuery = query,
            activeAuditReport = report,
            isBenchmarking = benchTriple.first,
            isBenchmarkDialogOpen = benchTriple.second,
            benchmarkResult = benchTriple.third,
            isWelcomeBannerVisible = quickPass.isWelcomeVisible,
            isQuickPasswordDialogOpen = quickPass.isDialogOpen,
            quickPasswordResult = quickPass.result,
            quickPasswordLength = quickPass.length,
            quickPasswordIncludeSymbols = quickPass.symbols
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    fun onSearchQueryChange(query: String) {
        searchQueryFlow.value = query
    }

    fun generateSecurityAudit() {
        val currentState = uiState.value
        val report = AuditEngine.generateSecurityAudit(
            context = context,
            isAppLockConfigured = currentState.isAppLockConfigured
        )
        activeReportFlow.value = report
    }

    fun dismissAuditReport() {
        activeReportFlow.value = null
    }

    fun openBenchmarkDialog() {
        isBenchmarkDialogOpenFlow.value = true
    }

    fun dismissBenchmarkDialog() {
        isBenchmarkDialogOpenFlow.value = false
    }

    fun runBenchmark() {
        viewModelScope.launch {
            isBenchmarkingFlow.value = true
            try {
                val result = BenchmarkEngine.runBenchmark(iterations = 8, payloadSizeKb = 256)
                benchmarkResultFlow.value = result
            } catch (_: Exception) {
                // Keep existing result if failed
            } finally {
                isBenchmarkingFlow.value = false
            }
        }
    }

    fun dismissWelcomeBanner() {
        welcomeBannerVisibleFlow.value = false
    }

    fun openQuickPasswordDialog() {
        regenerateQuickPassword()
        quickPasswordDialogOpenFlow.value = true
    }

    fun dismissQuickPasswordDialog() {
        quickPasswordDialogOpenFlow.value = false
    }

    fun updateQuickPasswordLength(length: Int) {
        quickPasswordLengthFlow.value = length.coerceIn(8, 32)
        regenerateQuickPassword()
    }

    fun toggleQuickPasswordSymbols(include: Boolean) {
        quickPasswordIncludeSymbolsFlow.value = include
        regenerateQuickPassword()
    }

    fun regenerateQuickPassword() {
        val length = quickPasswordLengthFlow.value
        val symbols = quickPasswordIncludeSymbolsFlow.value
        quickPasswordResultFlow.value = CsprngTokenEngine.generatePassword(
            length = length,
            includeUpper = true,
            includeLower = true,
            includeDigits = true,
            includeSymbols = symbols
        )
    }
}
