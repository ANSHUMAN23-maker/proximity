// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

// SettingsActivity.kt
package com.proximity.app.ui.activities
import androidx.compose.foundation.shape.RoundedCornerShape

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items


import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.proximity.app.BuildConfig
import com.proximity.app.ProximityApplication
import com.proximity.app.R
import com.proximity.app.ui.components.SimOperatorLogoBadge
import com.proximity.app.ui.components.pressScale
import com.proximity.app.SetupActivity
import com.proximity.app.data.SettingsRepository
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.isSystemInDarkTheme
import com.proximity.app.data.ThemeMode
import com.proximity.app.ui.components.liquidGlass

import com.proximity.app.repository.TransactionRepository
import com.proximity.app.ui.components.AnimatedContourBackground
import com.proximity.app.ui.components.FloatingBottomNavBar
import com.proximity.app.ui.components.NavigationTab

import com.proximity.app.ui.theme.TealAccentTheme
import com.proximity.app.ui.theme.ProximityAccentGreenBright
import com.proximity.app.ui.theme.ProximityStatusError
import com.proximity.app.ui.theme.LocalProximityAccentTheme
import com.proximity.app.ui.theme.ProximityTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : ComponentActivity() {

    private var refreshTrigger = mutableIntStateOf(0)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> refreshTrigger.intValue++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        val app = application as? ProximityApplication
        val settingsRepository = app?.settingsRepository ?: SettingsRepository(applicationContext)
        setTheme(R.style.Theme_Proximity)
        setContent {
            val savedSettings by settingsRepository.settingsFlow.collectAsState()
            CompositionLocalProvider(LocalProximityAccentTheme provides TealAccentTheme) {
                ProximityTheme(
                    themeMode = savedSettings.themeMode,
                    pureBlack = savedSettings.pureBlackEnabled,
                    liquidGlass = savedSettings.liquidGlassEnabled
                ) {
                    SettingsScreen(
                        settingsRepository = settingsRepository,
                        onRequestPermissions = { permissions ->
                            permissionLauncher.launch(permissions)
                        },
                        refreshTrigger = refreshTrigger
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshTrigger.intValue++
    }
}

// Data Classes
data class Bank(
    val id: String,
    val name: String
)

data class SettingsState(
    val selectedBank: Bank = banks.first(),
    val ussdTimeout: Int = 30,
    val smsDetectionEnabled: Boolean = true,
    val overlayEnabled: Boolean = true,
    val debugMode: Boolean = false,
    val setupCompleted: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pureBlackEnabled: Boolean = false,
    val liquidGlassEnabled: Boolean = true,
    val ussdAccessibilityEnabled: Boolean = false,
    val permissions: Map<String, Boolean> = emptyMap()
) {
    val darkModeEnabled: Boolean
        get() = themeMode == ThemeMode.DARK
}

// Bank Data
val banks = listOf(
    Bank("hdfc", "HDFC Bank"),
    Bank("sbi", "State Bank of India"),
    Bank("icici", "ICICI Bank"),
    Bank("axis", "Axis Bank"),
    Bank("kotak", "Kotak Mahindra Bank"),
    Bank("pnb", "Punjab National Bank"),
    Bank("bob", "Bank of Baroda"),
    Bank("yes", "Yes Bank"),
    Bank("idbi", "IDBI Bank"),
    Bank("canara", "Canara Bank"),
    Bank("union", "Union Bank of India")
)

// ViewModel
class SettingsViewModel : androidx.lifecycle.ViewModel() {
    var state by mutableStateOf(SettingsState())
        private set

    fun updateBank(bank: Bank) {
        state = state.copy(selectedBank = bank)
    }

    fun toggleSmsDetection() {
        state = state.copy(smsDetectionEnabled = !state.smsDetectionEnabled)
    }

    fun toggleOverlay() {
        state = state.copy(overlayEnabled = !state.overlayEnabled)
    }

    fun setThemeMode(mode: ThemeMode) {
        state = state.copy(themeMode = mode)
    }

    fun togglePureBlack() {
        state = state.copy(pureBlackEnabled = !state.pureBlackEnabled)
    }

    fun toggleLiquidGlass() {
        state = state.copy(liquidGlassEnabled = !state.liquidGlassEnabled)
    }

    fun toggleUssdAccessibility() {
        state = state.copy(ussdAccessibilityEnabled = !state.ussdAccessibilityEnabled)
    }

    fun toggleDarkMode() {
        val nextMode = if (state.themeMode == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK
        state = state.copy(themeMode = nextMode)
    }

    fun refreshPermissions(context: Context) {
        val perms = mapOf(
            "phone" to (
                ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
                    PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
                    PackageManager.PERMISSION_GRANTED
                ),
            "camera" to (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED),
            "sms" to (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
                PackageManager.PERMISSION_GRANTED),
            "contacts" to (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED),
            "notifications" to (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                true
            }),
            "overlay" to (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            })
        )
        state = state.copy(permissions = perms)
    }

    fun loadFromRepository(settingsRepository: SettingsRepository) {
        val saved = settingsRepository.settingsFlow.value
        val bank = banks.find { it.id == saved.bankId } ?: banks.first()
        state = state.copy(
            selectedBank = bank,
            ussdTimeout = saved.ussdTimeout,
            smsDetectionEnabled = saved.smsDetectionEnabled,
            overlayEnabled = saved.overlayEnabled,
            debugMode = saved.debugMode,
            setupCompleted = saved.setupCompleted,
            themeMode = saved.themeMode,
            pureBlackEnabled = saved.pureBlackEnabled,
            liquidGlassEnabled = saved.liquidGlassEnabled,
            ussdAccessibilityEnabled = saved.ussdAccessibilityEnabled
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onBackPressed: () -> Unit = {},
    settingsRepository: SettingsRepository? = null,
    onRequestPermissions: (Array<String>) -> Unit = {},
    refreshTrigger: MutableIntState = mutableIntStateOf(0)
) {
    val context = LocalContext.current
    val accent = LocalProximityAccentTheme.current
    val state = viewModel.state

    // Get primary SIM name from settings repository (in-memory, avoids StrictMode disk read)
    var primarySimId by remember {
        val op = settingsRepository?.settingsFlow?.value?.selectedOperator
        mutableStateOf(if (op != null && op != "unknown") op else "")
    }

    // Load settings from repository on first composition
    LaunchedEffect(Unit) {
        settingsRepository?.let { repo ->
            viewModel.loadFromRepository(repo)
            val op = repo.settingsFlow.value.selectedOperator
            if (op.isNotBlank() && op != "unknown") {
                primarySimId = op
            }
        }
        viewModel.refreshPermissions(context)
    }

    // Refresh permissions when trigger changes
    val trigger by refreshTrigger
    LaunchedEffect(trigger) {
        viewModel.refreshPermissions(context)
    }
    val primarySim = when (primarySimId) {
        "jio" -> "Jio"
        "airtel" -> "Airtel"
        "vodafone" -> "Vodafone"
        "bsnl" -> "BSNL"
        else -> stringResource(R.string.settings_not_set)
    }

    // Dialog states
    var showBankPicker by remember { mutableStateOf(false) }
    var showSimPicker by remember { mutableStateOf(false) }
    var showClearDataConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            FloatingBottomNavBar(currentTab = NavigationTab.SETTINGS)
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedContourBackground(modifier = Modifier.fillMaxSize())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // Top Bar
                TopAppBar(
                    title = {
                        com.proximity.app.ui.components.ProximityBrandedHeader(
                            fontSize = 20.sp,
                            logoSize = 28.dp
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )

                // Settings Content
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = innerPadding.calculateBottomPadding() + 24.dp
                    )
                ) {
                item { SectionHeader("CONFIGURATION") }
                item {
                    GroupCard {
                        SettingsRow(
                            icon = Icons.Default.AccountBalance,
                            title = "Bank",
                            value = state.selectedBank.name,
                            onClick = null
                        )
                        GroupDivider()
                        SettingsRow(
                            icon = Icons.Default.SimCard,
                            title = "Primary SIM",
                            value = primarySim,
                            onClick = { showSimPicker = true }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }
                item { SectionHeader("APPEARANCE") }
                item {
                    GroupCard {
                        // 3-Segment Theme Selector
                        Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(accent.primary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_appearance),
                                        contentDescription = null,
                                        tint = accent.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Theme Mode",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = when (state.themeMode) {
                                            ThemeMode.SYSTEM -> "Follows device system settings"
                                            ThemeMode.LIGHT -> "Always light"
                                            ThemeMode.DARK -> "Always dark"
                                        },
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            ThemeModeSelector(
                                currentMode = state.themeMode,
                                onModeSelected = { newMode ->
                                    viewModel.setThemeMode(newMode)
                                    settingsRepository?.let { repo ->
                                        repo.saveSettings(repo.settingsFlow.value.copy(themeMode = newMode))
                                    }
                                }
                            )
                        }

                        // OLED Pure Black (visible when dark mode is effectively active)
                        val isSystemDark = isSystemInDarkTheme()
                        val isDarkEffective = state.themeMode == ThemeMode.DARK ||
                            (state.themeMode == ThemeMode.SYSTEM && isSystemDark)

                        AnimatedVisibility(
                            visible = isDarkEffective,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                GroupDivider()
                                ToggleRow(
                                    icon = Icons.Default.Brightness2,
                                    title = "OLED Pure Black",
                                    subtitle = "Pitch black surfaces for OLED battery saving",
                                    checked = state.pureBlackEnabled,
                                    onCheckedChange = { checked ->
                                        viewModel.togglePureBlack()
                                        settingsRepository?.let { repo ->
                                            repo.saveSettings(repo.settingsFlow.value.copy(pureBlackEnabled = checked))
                                        }
                                    }
                                )
                            }
                        }

                        GroupDivider()

                        // Liquid Glass Toggle
                        ToggleRow(
                            icon = Icons.Default.BlurOn,
                            title = "Liquid Glass",
                            subtitle = "Frosted specular glass surfaces & blur",
                            checked = state.liquidGlassEnabled,
                            onCheckedChange = { checked ->
                                viewModel.toggleLiquidGlass()
                                settingsRepository?.let { repo ->
                                    repo.saveSettings(repo.settingsFlow.value.copy(liquidGlassEnabled = checked))
                                }
                            }
                        )

                        GroupDivider()

                        // USSD Accessibility Service Toggle
                        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
                        val services = manager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                        val accessibilityEnabled = services.any { it.id == "${context.packageName}/.accessibility.UssdAccessibilityService" }
                        ToggleRow(
                            icon = Icons.Default.Accessibility,
                            title = stringResource(R.string.settings_ussd_accessibility_title),
                            subtitle = if (accessibilityEnabled) {
                                stringResource(R.string.settings_ussd_accessibility_subtitle)
                            } else {
                                stringResource(R.string.settings_ussd_accessibility_not_enabled)
                            },
                            checked = state.ussdAccessibilityEnabled,
                            onCheckedChange = { checked ->
                                // Always save the user's preference first
                                viewModel.toggleUssdAccessibility()
                                settingsRepository?.let { repo ->
                                    repo.saveSettings(repo.settingsFlow.value.copy(ussdAccessibilityEnabled = checked))
                                }
                                if (checked && !accessibilityEnabled) {
                                    // Open system accessibility settings if enabling but service not yet enabled
                                    val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                }
                            }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }
                item { SectionHeader("PERMISSIONS") }
                item {
                    GroupCard {
                        PermissionRow(
                            iconRes = R.drawable.ic_phone_call,
                            title = "Phone",
                            subtitle = "Calls & phone state",
                            granted = state.permissions["phone"] ?: false,
                            onRequest = {
                                onRequestPermissions(
                                    arrayOf(
                                        Manifest.permission.CALL_PHONE,
                                        Manifest.permission.READ_PHONE_STATE
                                    )
                                )
                            }
                        )
                        GroupDivider()
                        PermissionRow(
                            iconRes = R.drawable.ic_camera,
                            title = "Camera",
                            subtitle = "QR code scanning",
                            granted = state.permissions["camera"] ?: false,
                            onRequest = {
                                onRequestPermissions(arrayOf(Manifest.permission.CAMERA))
                            }
                        )
                        GroupDivider()
                        PermissionRow(
                            icon = Icons.Default.Sms,
                            title = "SMS",
                            subtitle = "Bank payment confirmations",
                            granted = state.permissions["sms"] ?: false,
                            onRequest = {
                                onRequestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS))
                            }
                        )
                        GroupDivider()
                        PermissionRow(
                            icon = Icons.Default.Contacts,
                            title = "Contacts",
                            subtitle = "Pay by contact",
                            granted = state.permissions["contacts"] ?: false,
                            onRequest = {
                                onRequestPermissions(arrayOf(Manifest.permission.READ_CONTACTS))
                            }
                        )
                        GroupDivider()
                        PermissionRow(
                            icon = Icons.Default.Notifications,
                            title = "Notifications",
                            subtitle = "Payment result notifications",
                            granted = state.permissions["notifications"] ?: false,
                            onRequest = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    onRequestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                                }
                            }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
                item { SectionHeader("ABOUT") }
                item {
                    GroupCard {
                        SettingsRow(
                            icon = Icons.Default.Info,
                            title = "Version",
                            value = BuildConfig.VERSION_NAME
                        )
                        GroupDivider()
                        SettingsRow(
                            icon = Icons.Default.PhoneAndroid,
                            title = "Android",
                            value = Build.VERSION.RELEASE
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
    }

    // Bank Picker Dialog
    if (showBankPicker) {
        BankPickerDialog(
            selectedBank = state.selectedBank,
            onBankSelected = { bank ->
                viewModel.updateBank(bank)
                settingsRepository?.saveSettings(
                    settingsRepository.settingsFlow.value.copy(bankId = bank.id)
                )
                // Also sync to ProximityPrefs so the main screen picks it up
                context.getSharedPreferences("ProximityPrefs", Context.MODE_PRIVATE)
                    .edit().putString("selected_bank", bank.id).apply()
                showBankPicker = false
            },
            onDismiss = { showBankPicker = false }
        )
    }

    // SIM Picker Dialog
    if (showSimPicker) {
        SimPickerDialog(
            selectedSimId = primarySimId,
            onSimSelected = { simId ->
                primarySimId = simId
                settingsRepository?.let { repo ->
                    repo.saveSettings(repo.settingsFlow.value.copy(selectedOperator = simId))
                }
                showSimPicker = false
            },
            onDismiss = { showSimPicker = false }
        )
    }

    // Clear Data Confirmation Dialog
    if (showClearDataConfirm) {
        AlertDialog(
            onDismissRequest = { showClearDataConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = {
                Text(
                    stringResource(R.string.settings_clear_data_title),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    "This permanently deletes your entire transaction history and " +
                        "resets all settings, then returns you to the setup screen. " +
                        "This cannot be undone.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearDataConfirm = false
                    // Wipe the encrypted transaction DB before anything else, on
                    // the process scope so the delete survives the CLEAR_TASK
                    // relaunch below; only then reset prefs and restart into Setup.
                    val app = ProximityApplication.from(context)
                    val relaunch = {
                        settingsRepository?.clearAllData()
                        context.getSharedPreferences("ProximityPrefs", Context.MODE_PRIVATE)
                            .edit().clear().apply()
                        context.startActivity(
                            Intent(context, SetupActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                        )
                    }
                    if (app != null) {
                        app.appScope.launch {
                            runCatching {
                                TransactionRepository.getInstance(context).deleteAllTransactions()
                            }
                            withContext(Dispatchers.Main) { relaunch() }
                        }
                    } else {
                        relaunch()
                    }
                }) {
                    Text(
                        stringResource(R.string.action_clear),
                        color = ProximityStatusError,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataConfirm = false }) {
                    Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun ThemeModeSelector(
    currentMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit
) {
    val modes = listOf(
        ThemeMode.SYSTEM to "System",
        ThemeMode.LIGHT to "Light",
        ThemeMode.DARK to "Dark"
    )
    val accent = LocalProximityAccentTheme.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                RoundedCornerShape(12.dp)
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        modes.forEach { (mode, label) ->
            val isSelected = currentMode == mode
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) accent.primary else Color.Transparent,
                label = "theme_tab_bg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "theme_tab_text"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor)
                    .clickable { onModeSelected(mode) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = textColor
                )
            }
        }
    }
}

@Composable
private fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(shape = RoundedCornerShape(16.dp))
    ) {
        Column(content = content)
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 56.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    value: String,
    destructive: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val accent = LocalProximityAccentTheme.current
    val iconColor = if (destructive) ProximityStatusError else accent.primary
    val iconBg = if (destructive) ProximityStatusError.copy(alpha = 0.12f) else accent.primary.copy(alpha = 0.12f)
    val titleColor = if (destructive) ProximityStatusError else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .then(if (onClick != null) Modifier.semantics(mergeDescendants = true) { } else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = titleColor,
            modifier = Modifier.weight(1f)
        )

        // Value or chevron
        if (onClick != null) {
            Text(
                text = value,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                text = value,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector? = null,
    iconRes: Int? = null,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val accent = LocalProximityAccentTheme.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(accent.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = accent.primary,
                    modifier = Modifier.size(16.dp)
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accent.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector? = null,
    iconRes: Int? = null,
    title: String,
    subtitle: String,
    granted: Boolean,
    onRequest: () -> Unit
) {
    val accent = LocalProximityAccentTheme.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(accent.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = accent.primary,
                    modifier = Modifier.size(16.dp)
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title + subtitle
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Status pill
        if (granted) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = ProximityAccentGreenBright.copy(alpha = 0.12f)
            ) {
                Text(
                    text = "Granted",
                    color = ProximityAccentGreenBright,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        } else {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = accent.primary.copy(alpha = 0.12f),
                modifier = Modifier.clickable(onClick = onRequest)
            ) {
                Text(
                    text = "Grant",
                    color = accent.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun BankPickerDialog(
    selectedBank: Bank,
    onBankSelected: (Bank) -> Unit,
    onDismiss: () -> Unit
) {
    val accent = LocalProximityAccentTheme.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(vertical = 20.dp)
            ) {
                Text(
                    text = "Select Bank",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(banks) { bank ->
                        val isSelected = bank.id == selectedBank.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onBankSelected(bank) }
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = bank.name,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) accent.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = accent.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        if (bank != banks.last()) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SimPickerDialog(
    selectedSimId: String,
    onSimSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val accent = LocalProximityAccentTheme.current
    val sims = listOf(
        "jio" to "Reliance Jio",
        "airtel" to "Airtel",
        "vodafone" to "Vodafone Idea",
        "bsnl" to "BSNL"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(vertical = 20.dp)) {
                Text(
                    text = "Select Primary SIM",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                sims.forEachIndexed { index, (id, name) ->
                    val isSelected = id == selectedSimId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressScale(onClick = { onSimSelected(id) })
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SimOperatorLogoBadge(operatorKey = id, size = 36.dp)

                        Spacer(modifier = Modifier.width(14.dp))

                        Text(
                            text = name,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) accent.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = accent.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (index < sims.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}


