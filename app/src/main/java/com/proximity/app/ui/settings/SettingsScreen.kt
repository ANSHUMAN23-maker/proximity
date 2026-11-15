// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.proximity.app.AppCore
import com.proximity.app.BuildConfig
import com.proximity.app.R
import com.proximity.app.accessibility.UssdDialogReaderService
import com.proximity.app.data.BankCatalog
import com.proximity.app.data.OperatorCatalog
import com.proximity.app.theme.AppTheme
import com.proximity.app.theme.ColorTokens
import com.proximity.app.theme.LocalAppVisuals
import com.proximity.app.theme.ThemeMode
import com.proximity.app.ui.components.AnimatedBackground
import com.proximity.app.ui.components.BrandedHeaderLockup
import com.proximity.app.ui.components.GlassCard
import com.proximity.app.ui.components.OperatorLogoBadge
import com.proximity.app.utils.PermissionChecker

class SettingsScreen : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val app = application as AppCore

        setContent {
            val settings by app.services.preferencesStore.settingsFlow.collectAsState()
            AppTheme(settings = settings) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AnimatedBackground(modifier = Modifier.fillMaxSize())
                    SettingsContent()
                }
            }
        }
    }
}

@Composable
fun SettingsContent() {
    val context = LocalContext.current
    val visuals = LocalAppVisuals.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val app = context.applicationContext as AppCore
    val settings by app.services.preferencesStore.settingsFlow.collectAsState()

    var showBankPicker by remember { mutableStateOf(false) }
    var showSimPicker by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshTrigger++
    }

    val systemDark = isSystemInDarkTheme()
    val isDarkEffective = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val phoneGranted = remember(refreshTrigger) { PermissionChecker.isPhoneGranted(context) }
    val cameraGranted = remember(refreshTrigger) { PermissionChecker.isCameraGranted(context) }
    val smsGranted = remember(refreshTrigger) { PermissionChecker.isSmsGranted(context) }
    val contactsGranted = remember(refreshTrigger) { PermissionChecker.isContactsGranted(context) }
    val notifGranted = remember(refreshTrigger) { PermissionChecker.isNotificationsGranted(context) }
    val accessibilityActive = remember(refreshTrigger) {
        UssdDialogReaderService.isAccessibilityEnabled(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        BrandedHeaderLockup(compact = true)
        Spacer(modifier = Modifier.height(22.dp))

        SettingsGroupTitle(text = stringResource(id = R.string.settings_section_configuration))
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingsActionRow(
                    iconRes = R.drawable.ic_wallet,
                    title = stringResource(id = R.string.settings_bank),
                    value = settings.selectedBankDisplayName,
                    onClick = { showBankPicker = true }
                )
                HorizontalDivider(color = visuals.outline.copy(alpha = 0.25f))
                val carrierTitle = OperatorCatalog.findById(settings.primarySimCarrierId)?.title
                    ?: stringResource(id = R.string.settings_not_set)
                SettingsActionRow(
                    iconRes = R.drawable.ic_phone_call,
                    title = stringResource(id = R.string.settings_primary_sim),
                    value = carrierTitle,
                    onClick = { showSimPicker = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        SettingsGroupTitle(text = stringResource(id = R.string.settings_section_appearance))
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsIconBadge(iconRes = R.drawable.ic_appearance)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(id = R.string.settings_theme_mode),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = visuals.textPrimary
                        )
                        val subRes = when (settings.themeMode) {
                            ThemeMode.SYSTEM -> R.string.settings_theme_sub_system
                            ThemeMode.LIGHT -> R.string.settings_theme_sub_light
                            ThemeMode.DARK -> R.string.settings_theme_sub_dark
                        }
                        Text(
                            text = stringResource(id = subRes),
                            fontSize = 12.sp,
                            color = visuals.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(visuals.inputSurface)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val modes = listOf(
                        ThemeMode.SYSTEM to R.string.settings_theme_system,
                        ThemeMode.LIGHT to R.string.settings_theme_light,
                        ThemeMode.DARK to R.string.settings_theme_dark
                    )
                    modes.forEach { (mode, labelRes) ->
                        val selected = settings.themeMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) ColorTokens.BrandTeal else Color.Transparent
                                )
                                .selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = {
                                        app.services.preferencesStore.update { it.copy(themeMode = mode) }
                                    }
                                )
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(id = labelRes),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) Color.White else visuals.textSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = isDarkEffective) {
                    Column {
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = visuals.outline.copy(alpha = 0.25f))
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsSwitchRow(
                            iconRes = R.drawable.ic_appearance,
                            title = stringResource(id = R.string.settings_oled_black),
                            subtitle = stringResource(id = R.string.settings_oled_black_sub),
                            checked = settings.pureBlackEnabled,
                            onCheckedChange = { enabled ->
                                app.services.preferencesStore.update { it.copy(pureBlackEnabled = enabled) }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = visuals.outline.copy(alpha = 0.25f))
                Spacer(modifier = Modifier.height(10.dp))

                SettingsSwitchRow(
                    iconRes = R.drawable.ic_appearance,
                    title = stringResource(id = R.string.settings_liquid_glass),
                    subtitle = stringResource(id = R.string.settings_liquid_glass_sub),
                    checked = settings.liquidGlassEnabled,
                    onCheckedChange = { enabled ->
                        app.services.preferencesStore.update { it.copy(liquidGlassEnabled = enabled) }
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = visuals.outline.copy(alpha = 0.25f))
                Spacer(modifier = Modifier.height(10.dp))

                SettingsSwitchRow(
                    iconRes = R.drawable.ic_settings,
                    title = stringResource(id = R.string.settings_ussd_accessibility),
                    subtitle = stringResource(id = R.string.settings_ussd_accessibility_sub),
                    checked = accessibilityActive,
                    onCheckedChange = { enabled ->
                        app.services.preferencesStore.update { it.copy(ussdAccessibilityPrefEnabled = enabled) }
                        if (enabled && !UssdDialogReaderService.isAccessibilityEnabled(context)) {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        } else if (!enabled) {
                            UssdDialogReaderService.disableService()
                            refreshTrigger++
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        SettingsGroupTitle(text = stringResource(id = R.string.settings_section_permissions))
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                PermissionRowItem(
                    iconRes = R.drawable.ic_phone_call,
                    title = stringResource(id = R.string.settings_perm_phone),
                    granted = phoneGranted,
                    onGrantClick = {
                        permLauncher.launch(
                            arrayOf(
                                Manifest.permission.CALL_PHONE,
                                Manifest.permission.READ_PHONE_STATE
                            )
                        )
                    }
                )
                HorizontalDivider(color = visuals.outline.copy(alpha = 0.25f))
                PermissionRowItem(
                    iconRes = R.drawable.ic_camera,
                    title = stringResource(id = R.string.settings_perm_camera),
                    granted = cameraGranted,
                    onGrantClick = {
                        permLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                    }
                )
                HorizontalDivider(color = visuals.outline.copy(alpha = 0.25f))
                PermissionRowItem(
                    iconRes = R.drawable.ic_question_circle,
                    title = stringResource(id = R.string.settings_perm_sms),
                    granted = smsGranted,
                    onGrantClick = {
                        permLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS))
                    }
                )
                HorizontalDivider(color = visuals.outline.copy(alpha = 0.25f))
                PermissionRowItem(
                    iconRes = R.drawable.ic_contact,
                    title = stringResource(id = R.string.settings_perm_contacts),
                    granted = contactsGranted,
                    onGrantClick = {
                        permLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                    }
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    HorizontalDivider(color = visuals.outline.copy(alpha = 0.25f))
                    PermissionRowItem(
                        icon = Icons.Default.Notifications,
                        title = stringResource(id = R.string.settings_perm_notifications),
                        granted = notifGranted,
                        onGrantClick = {
                            permLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        SettingsGroupTitle(text = stringResource(id = R.string.settings_section_about))
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingsActionRow(
                    icon = Icons.Default.Info,
                    title = stringResource(id = R.string.settings_about_version),
                    value = BuildConfig.VERSION_NAME,
                    onClick = null
                )
                HorizontalDivider(color = visuals.outline.copy(alpha = 0.25f))
                SettingsActionRow(
                    iconRes = R.drawable.ic_phone_call,
                    title = stringResource(id = R.string.settings_about_android),
                    value = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                    onClick = null
                )
            }
        }

        Spacer(modifier = Modifier.navigationBarsPadding().height(112.dp))
    }

    if (showBankPicker) {
        Dialog(onDismissRequest = { showBankPicker = false }) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                elevatedOverlay = true
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = stringResource(id = R.string.setup_choose_your_bank),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = visuals.textPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    BankCatalog.ALL_BANKS.forEach { bank ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    app.services.preferencesStore.update { it.copy(selectedBankId = bank.id) }
                                    showBankPicker = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = bank.displayName,
                                fontSize = 15.sp,
                                fontWeight = if (bank.id == settings.selectedBankId) FontWeight.Bold else FontWeight.Normal,
                                color = if (bank.id == settings.selectedBankId) ColorTokens.BrandTeal else visuals.textPrimary
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSimPicker) {
        Dialog(onDismissRequest = { showSimPicker = false }) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                elevatedOverlay = true
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.setup_select_your_sim),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = visuals.textPrimary
                    )
                    OperatorCatalog.ALL_OPERATORS.forEach { operator ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    app.services.preferencesStore.update {
                                        it.copy(
                                            primarySimCarrierId = operator.id,
                                            selectedOperator = operator.id,
                                            ussdTestPassed = if (operator.id == "jio") false else it.ussdTestPassed
                                        )
                                    }
                                    showSimPicker = false
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OperatorLogoBadge(carrierId = operator.id, compact = true)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = operator.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = visuals.textPrimary
                                )
                                Text(
                                    text = operator.subtitle,
                                    fontSize = 12.sp,
                                    color = visuals.textSecondary
                                )
                            }
                            if (settings.primarySimCarrierId == operator.id) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_check_circle),
                                    contentDescription = null,
                                    tint = ColorTokens.BrandTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGroupTitle(text: String) {
    val visuals = LocalAppVisuals.current
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.1.sp,
        color = visuals.adaptiveTealLabel,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsIconBadge(
    icon: ImageVector? = null,
    iconRes: Int? = null,
    tint: Color = ColorTokens.BrandTeal
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        when {
            iconRes != null -> Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            icon != null -> Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector? = null,
    iconRes: Int? = null,
    title: String,
    value: String,
    onClick: (() -> Unit)?
) {
    val visuals = LocalAppVisuals.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null || iconRes != null) {
                SettingsIconBadge(icon = icon, iconRes = iconRes)
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = visuals.textPrimary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (onClick != null) visuals.adaptiveTealLabel else visuals.textSecondary
            )
            if (onClick != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    painter = painterResource(id = R.drawable.ic_forward_arrow),
                    contentDescription = null,
                    tint = visuals.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector? = null,
    iconRes: Int? = null,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val visuals = LocalAppVisuals.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Switch
            )
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null || iconRes != null) {
            SettingsIconBadge(icon = icon, iconRes = iconRes)
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = visuals.textPrimary
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = visuals.textSecondary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ColorTokens.BrandTeal
            )
        )
    }
}

@Composable
private fun PermissionRowItem(
    icon: ImageVector? = null,
    iconRes: Int? = null,
    title: String,
    granted: Boolean,
    onGrantClick: () -> Unit
) {
    val visuals = LocalAppVisuals.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null || iconRes != null) {
                SettingsIconBadge(icon = icon, iconRes = iconRes, tint = visuals.adaptiveTealLabel)
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = visuals.textPrimary
            )
        }
        if (granted) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_check_circle),
                    contentDescription = null,
                    tint = visuals.adaptiveTealLabel,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(id = R.string.badge_allowed),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = visuals.textSecondary
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(CircleShape)
                    .background(ColorTokens.BrandTeal.copy(alpha = 0.16f))
                    .border(1.dp, ColorTokens.BrandTeal.copy(alpha = 0.35f), CircleShape)
                    .clickable(role = Role.Button, onClick = onGrantClick)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.action_allow),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = visuals.adaptiveTealLabel
                )
            }
        }
    }
}
