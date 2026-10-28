package com.example

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppDatabase
import com.example.data.ThemeMode
import com.example.data.UserPreferencesRepository
import com.example.security.AppLockManager
import com.example.ui.components.PinKeypadDialog
import com.example.ui.screens.CryptoScreen
import com.example.ui.screens.CryptoViewModel
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DashboardViewModel
import com.example.ui.screens.HashScreen
import com.example.ui.screens.HashViewModel
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SignatureScreen
import com.example.ui.screens.SignatureViewModel
import com.example.ui.screens.VaultScreen
import com.example.ui.screens.VaultViewModel
import com.example.ui.theme.SecurityGreen
import com.example.ui.theme.VeriCryptTheme

import com.example.ui.screens.CsprngTokenViewModel
import com.example.ui.screens.SecurityHubScreen
import com.example.ui.screens.ToolsScreen

enum class AppScreen(val label: String, val icon: ImageVector) {
    VAULT("Vault", Icons.Default.Folder),
    PROTECT("Protect", Icons.Default.Lock),
    TOOLS("Tools", Icons.Default.Key),
    SECURITY("Security", Icons.Default.Shield)
}

class MainActivity : FragmentActivity() {

    private val dashboardViewModel: DashboardViewModel by viewModels()
    private val hashViewModel: HashViewModel by viewModels()
    private val cryptoViewModel: CryptoViewModel by viewModels()
    private val signatureViewModel: SignatureViewModel by viewModels()
    private val vaultViewModel: VaultViewModel by viewModels()
    private val tokenViewModel: CsprngTokenViewModel by viewModels()

    private var backgroundTimestamp: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Explicitly clear FLAG_SECURE on launch to prevent emulator and browser streaming displays from going black
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val db = AppDatabase.getDatabase(applicationContext)
        val prefs = UserPreferencesRepository(applicationContext)

        setContent {
            val settings by prefs.userSettings.collectAsStateWithLifecycle(initialValue = null)

            // Dynamically synchronize FLAG_SECURE on physical devices only (disabled on emulators to prevent black screen)
            val isScreenProtection = settings?.isScreenProtectionEnabled == true && !isRunningOnEmulator()
            androidx.compose.runtime.LaunchedEffect(isScreenProtection) {
                if (isScreenProtection) {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            val isDarkTheme = when (settings?.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                else -> isSystemInDarkTheme()
            }

            var currentScreen by remember { mutableStateOf(AppScreen.VAULT) }
            var isAppLocked by remember { mutableStateOf(false) }
            var hasEvaluatedColdBootLock by remember { mutableStateOf(false) }
            var lockPinError by remember { mutableStateOf<String?>(null) }

            // Enforce immediate app lock on cold boot if configured
            androidx.compose.runtime.LaunchedEffect(settings) {
                val s = settings
                if (s != null && !hasEvaluatedColdBootLock) {
                    hasEvaluatedColdBootLock = true
                    if (s.isAppLockEnabled && s.hasPinConfigured) {
                        isAppLocked = true
                    }
                }
            }

            // Lifecycle Observer for Auto-Lock
            DisposableEffect(this) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_STOP -> {
                            backgroundTimestamp = System.currentTimeMillis()
                        }
                        Lifecycle.Event.ON_START -> {
                            val userSettings = settings
                            if (userSettings != null && userSettings.isAppLockEnabled && userSettings.hasPinConfigured) {
                                if (backgroundTimestamp != 0L) {
                                    val elapsedSec = (System.currentTimeMillis() - backgroundTimestamp) / 1000
                                    if (elapsedSec >= userSettings.autoLockTimeoutSeconds) {
                                        isAppLocked = true
                                    }
                                }
                            }
                        }
                        else -> {}
                    }
                }
                lifecycle.addObserver(observer)
                onDispose {
                    lifecycle.removeObserver(observer)
                }
            }

            VeriCryptTheme(themeMode = settings?.themeMode ?: ThemeMode.SYSTEM) {
                if (isAppLocked && settings?.hasPinConfigured == true) {
                    // App Lock Curtain
                    PinKeypadDialog(
                        title = "VeriCrypt Locked",
                        subtitle = "Enter 6-digit master security PIN to resume",
                        errorMessage = lockPinError,
                        isBiometricAvailable = AppLockManager.isBiometricHardwareAvailable(this@MainActivity) && settings?.isBiometricsEnabled == true,
                        onBiometricClick = {
                            AppLockManager.showBiometricPrompt(
                                activity = this@MainActivity,
                                title = "VeriCrypt Security",
                                onSuccess = {
                                    isAppLocked = false
                                    lockPinError = null
                                },
                                onError = { lockPinError = it }
                            )
                        },
                        onPinSubmitted = { pin ->
                            val currentSettings = settings
                            val isValid = if (currentSettings != null && currentSettings.hasPinConfigured) {
                                AppLockManager.hashPin(pin, currentSettings.pinSalt).equals(currentSettings.pinHash, ignoreCase = true)
                            } else true

                            if (isValid) {
                                isAppLocked = false
                                lockPinError = null
                            } else {
                                lockPinError = "Incorrect PIN. Please try again."
                            }
                        }
                    )
                }

                VeriCryptAppScaffold(
                    currentScreen = currentScreen,
                    onScreenSelected = { currentScreen = it },
                    onLockAppNow = {
                        if (settings?.hasPinConfigured == true) {
                            isAppLocked = true
                        }
                        vaultViewModel.lockVault()
                    },
                    dashboardViewModel = dashboardViewModel,
                    hashViewModel = hashViewModel,
                    cryptoViewModel = cryptoViewModel,
                    signatureViewModel = signatureViewModel,
                    vaultViewModel = vaultViewModel,
                    tokenViewModel = tokenViewModel,
                    prefs = prefs,
                    db = db
                )
            }
        }
    }

    private fun isRunningOnEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.BOARD.contains("goldfish")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("emulator")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VeriCryptAppScaffold(
    currentScreen: AppScreen,
    onScreenSelected: (AppScreen) -> Unit,
    onLockAppNow: () -> Unit,
    dashboardViewModel: DashboardViewModel,
    hashViewModel: HashViewModel,
    cryptoViewModel: CryptoViewModel,
    signatureViewModel: SignatureViewModel,
    vaultViewModel: VaultViewModel,
    tokenViewModel: CsprngTokenViewModel,
    prefs: UserPreferencesRepository,
    db: AppDatabase
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column {
                            Text(
                                text = when (currentScreen) {
                                    AppScreen.VAULT -> "Secure Vault"
                                    AppScreen.PROTECT -> "Protect & Encrypt"
                                    AppScreen.TOOLS -> "Cryptographic Tools"
                                    AppScreen.SECURITY -> "Security Center"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Zero-Knowledge Suite",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 10.sp
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onLockAppNow,
                        modifier = Modifier.testTag("appbar_lock_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock Application",
                            tint = SecurityGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { onScreenSelected(AppScreen.SECURITY) },
                        modifier = Modifier.testTag("appbar_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = if (currentScreen == AppScreen.SECURITY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        RectangleShape
                    )
                    .testTag("bottom_navigation_bar")
            ) {
                val navItems = listOf(
                    AppScreen.VAULT,
                    AppScreen.PROTECT,
                    AppScreen.TOOLS,
                    AppScreen.SECURITY
                )

                navItems.forEach { screen ->
                    val selected = currentScreen == screen
                    NavigationBarItem(
                        selected = selected,
                        onClick = { onScreenSelected(screen) },
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.label,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = {
                            Text(
                                text = screen.label,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        ),
                        modifier = Modifier.testTag("nav_item_${screen.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                AppScreen.VAULT -> VaultScreen(viewModel = vaultViewModel, prefs = prefs)
                AppScreen.PROTECT -> CryptoScreen(viewModel = cryptoViewModel)
                AppScreen.TOOLS -> ToolsScreen(
                    hashViewModel = hashViewModel,
                    signatureViewModel = signatureViewModel,
                    tokenViewModel = tokenViewModel
                )
                AppScreen.SECURITY -> SecurityHubScreen(
                    dashboardViewModel = dashboardViewModel,
                    prefs = prefs,
                    db = db,
                    onNavigateToProtect = { onScreenSelected(AppScreen.PROTECT) },
                    onNavigateToTools = { onScreenSelected(AppScreen.TOOLS) },
                    onNavigateToVault = { onScreenSelected(AppScreen.VAULT) },
                    onLockAppNow = onLockAppNow
                )
            }
        }
    }
}
