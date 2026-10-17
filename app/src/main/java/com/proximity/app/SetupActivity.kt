// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proximity.app.helpers.SetupHelper
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.proximity.app.managers.PermissionManager
import com.proximity.app.constants.PermissionConstants
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Layers

import com.proximity.app.ui.components.AnimatedContourBackground
import com.proximity.app.ui.components.SimOperatorLogoBadge
import com.proximity.app.ui.components.pressScale
import com.proximity.app.ui.theme.StatusGreen
import com.proximity.app.ui.theme.TealAccentTheme
import com.proximity.app.ui.theme.ProximityDarkGray
import com.proximity.app.ui.theme.ProximityDisabledGray
import com.proximity.app.ui.theme.ProximityLightGray
import com.proximity.app.ui.theme.ProximityMediumGray
import com.proximity.app.ui.theme.ProximitySurfaceDim
import com.proximity.app.ui.theme.ProximityTextLightGray
import com.proximity.app.ui.theme.ProximityTheme
import com.proximity.app.ui.theme.LocalProximityAccentTheme
import com.proximity.app.ui.theme.ProximityBlack
import com.proximity.app.ui.theme.AbyssalCharcoal

enum class SetupStep { WELCOME, BANK, SIM, GROUND_RULES, PERMISSIONS }

class SetupActivity : ComponentActivity() {
    private lateinit var setupHelper: SetupHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize setup helper
        setupHelper = SetupHelper(
            this,
            object : SetupHelper.UICallback {
                override fun showToast(message: String) {
                    runOnUiThread { Toast.makeText(this@SetupActivity, message, Toast.LENGTH_LONG).show() }
                }

                override fun navigateToTestConfiguration() {
                    val intent = Intent(this@SetupActivity, TestConfigurationActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
        )

        setTheme(R.style.Theme_Proximity)
        // Edge-to-edge: Compose insets are the single source of padding (see MainActivity).
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            CompositionLocalProvider(LocalProximityAccentTheme provides TealAccentTheme) {
                ProximityTheme {
                    SetupScreen(setupHelper = setupHelper)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(setupHelper: SetupHelper) {
    val context = LocalContext.current
    // Step tracker
    var currentStep by remember { mutableStateOf(SetupStep.WELCOME) }

    // Form data states
    var selectedBank by remember { mutableStateOf("") }
    var selectedPrimarySim by remember { mutableStateOf("") }
    var selectedSecondarySim by remember { mutableStateOf("") }
    var isDualSimEnabled by remember { mutableStateOf(false) }
    var isAllPermissionsGranted by remember { mutableStateOf(false) }
    var termsAccepted by remember { mutableStateOf(false) }

    val banks = setupHelper.getBanks()
    val simCarriers = setupHelper.getSimCarriers()
    val secondarySimOptions = setupHelper.getSecondarySimOptions(selectedPrimarySim)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedContourBackground(modifier = Modifier.fillMaxSize())
        if (currentStep == SetupStep.WELCOME) {
            WelcomeSection(
                termsAccepted = termsAccepted,
                onTermsAcceptedChange = { termsAccepted = it },
                onTermsClick = {
                    context.startActivity(Intent(context, com.proximity.app.ui.activities.TermsActivity::class.java))
                },
                onContinue = { currentStep = SetupStep.BANK }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 420.dp)
                    .align(Alignment.Center)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // Hero Typography & Progress
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    // Progress Bar
                    LinearProgressIndicator(
                        progress = {
                            when (currentStep) {
                                SetupStep.WELCOME -> 0f
                                SetupStep.BANK -> 0.25f
                                SetupStep.SIM -> 0.50f
                                SetupStep.GROUND_RULES -> 0.75f
                                SetupStep.PERMISSIONS -> 1.0f
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = LocalProximityAccentTheme.current.accent,
                        trackColor = ProximityDarkGray
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Back Button & Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = {
                                currentStep = when (currentStep) {
                                    SetupStep.BANK -> SetupStep.WELCOME
                                    SetupStep.SIM -> SetupStep.BANK
                                    SetupStep.GROUND_RULES -> SetupStep.SIM
                                    SetupStep.PERMISSIONS -> SetupStep.GROUND_RULES
                                    else -> SetupStep.WELCOME
                                }
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        Text(
                            text = when (currentStep) {
                                SetupStep.WELCOME -> ""
                                SetupStep.BANK -> "Choose your bank"
                                SetupStep.SIM -> "Select your SIM"
                                SetupStep.GROUND_RULES -> "A few ground rules"
                                SetupStep.PERMISSIONS -> "App Permissions"
                            },
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            lineHeight = 40.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Scrollable content area
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (currentStep) {
                        SetupStep.WELCOME -> {}
                        SetupStep.BANK -> BankSelectionSection(
                            banks = banks,
                            selectedBank = selectedBank,
                            onBankSelected = { selectedBank = it }
                        )
                        SetupStep.SIM -> SimCardSelectionSection(
                            simCarriers = simCarriers,
                            selectedPrimarySim = selectedPrimarySim,
                            selectedSecondarySim = selectedSecondarySim,
                            isDualSimEnabled = isDualSimEnabled,
                            onPrimarySimSelected = { selectedPrimarySim = it },
                            onSecondarySimSelected = { selectedSecondarySim = it },
                            onDualSimToggled = { isDualSimEnabled = it },
                            secondarySimOptions = secondarySimOptions
                        )
                        SetupStep.GROUND_RULES -> GroundRulesSection()
                        SetupStep.PERMISSIONS -> PermissionsSection(
                            onAllPermissionsGranted = { isAllPermissionsGranted = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Button enable logic per step
                val isNextEnabled = when (currentStep) {
                    SetupStep.WELCOME -> false
                    SetupStep.BANK -> selectedBank.isNotBlank()
                    SetupStep.SIM -> selectedPrimarySim.isNotBlank() &&
                            (!isDualSimEnabled || selectedSecondarySim.isNotBlank())
                    SetupStep.GROUND_RULES -> true
                    SetupStep.PERMISSIONS -> isAllPermissionsGranted
                }
                val nextButtonText = when (currentStep) {
                    SetupStep.PERMISSIONS -> "Complete Setup"
                    SetupStep.GROUND_RULES -> "Proceed"
                    else -> "Continue"
                }

                // Full width continue button
                CompleteSetupButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (isNextEnabled) 1f else 0.3f), // Fade out when disabled
                    text = nextButtonText,
                    showArrow = currentStep != SetupStep.PERMISSIONS,
                    enabled = isNextEnabled,
                    onCompleteSetup = {
                        when (currentStep) {
                            SetupStep.WELCOME -> {}
                            SetupStep.BANK -> currentStep = SetupStep.SIM
                            SetupStep.SIM -> currentStep = SetupStep.GROUND_RULES
                            SetupStep.GROUND_RULES -> currentStep = SetupStep.PERMISSIONS
                            SetupStep.PERMISSIONS -> {
                                val setupData = SetupHelper.SetupData(
                                    selectedBank = selectedBank,
                                    selectedPrimarySim = selectedPrimarySim,
                                    isDualSimEnabled = isDualSimEnabled,
                                    selectedSecondarySim = selectedSecondarySim,
                                    disclaimerAccepted = true
                                )
                                setupHelper.completeSetup(setupData)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SetupFieldLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = ProximityTextLightGray,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankSelectionSection(
    banks: List<Pair<String, String>>,
    selectedBank: String,
    onBankSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        SetupFieldLabel(stringResource(R.string.setup_select_bank))

        var expanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            TextField(
                value = banks.find { it.first == selectedBank }?.second
                    ?: stringResource(R.string.setup_choose_your_bank),
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedTrailingIconColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(12.dp),
                textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                banks.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                label,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp
                            )
                        },
                        onClick = {
                            onBankSelected(value)
                            expanded = false
                        },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }
        }
    }
}

@Composable
fun SimCardSelectionSection(
    simCarriers: List<Pair<String, String>>,
    selectedPrimarySim: String,
    selectedSecondarySim: String,
    isDualSimEnabled: Boolean,
    onPrimarySimSelected: (String) -> Unit,
    onSecondarySimSelected: (String) -> Unit,
    onDualSimToggled: (Boolean) -> Unit,
    secondarySimOptions: List<Pair<String, String>>
) {
    val accent = LocalProximityAccentTheme.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        SetupFieldLabel(stringResource(R.string.setup_primary_sim))

        Spacer(modifier = Modifier.height(8.dp))

        // Present SIM carriers as a tappable list
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            simCarriers.forEach { (value, _) ->
                val isSelected = selectedPrimarySim == value
                val itemBgColor = if (isSelected) accent.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                val itemBorderColor = if (isSelected) accent.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

                val (displayTitle, subtitle) = when (value.lowercase()) {
                    "jio" -> "Reliance Jio" to "Jio 4G / 5G"
                    "airtel" -> "Airtel" to "Airtel India"
                    "vodafone", "vi" -> "Vodafone Idea" to "Vi India"
                    "bsnl" -> "BSNL" to "Bharat Sanchar Nigam Limited"
                    else -> value.replaceFirstChar { it.uppercase() } to "Telecom Operator"
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressScale(onClick = { onPrimarySimSelected(value) }),
                    shape = RoundedCornerShape(16.dp),
                    color = itemBgColor,
                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, itemBorderColor),
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SimOperatorLogoBadge(operatorKey = value)

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayTitle,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = subtitle,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .border(
                                    width = if (isSelected) 0.dp else 2.dp,
                                    color = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    shape = CircleShape
                                )
                                .background(if (isSelected) accent.primary else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Dual SIM Checkbox
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDualSimToggled(!isDualSimEnabled) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .border(
                        width = 2.dp,
                        color = if (isDualSimEnabled) accent.accent else ProximityDisabledGray,
                        shape = CircleShape
                    )
                    .background(
                        color = if (isDualSimEnabled) accent.accent else Color.Transparent,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isDualSimEnabled) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color.White, CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = stringResource(R.string.enable_dual_sim),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Secondary SIM Section
        if (isDualSimEnabled) {
            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                thickness = 0.5.dp
            )

            Spacer(modifier = Modifier.height(16.dp))

            SetupFieldLabel(stringResource(R.string.secondary_sim))

            Spacer(modifier = Modifier.height(8.dp))

            // Present secondary SIM carriers as a tappable list
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                secondarySimOptions.forEach { (value, label) ->
                    val isSelected = selectedSecondarySim == value
                    val itemBgColor = if (isSelected) accent.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    val itemBorderColor = if (isSelected) accent.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    val textColor = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(itemBgColor)
                            .border(
                                width = 1.dp,
                                color = itemBorderColor,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSecondarySimSelected(value) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SimCard,
                            contentDescription = null,
                            tint = if (isSelected) accent.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = label,
                            fontSize = 16.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = textColor,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Selected",
                                tint = accent.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DisclaimerSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {

        // Full disclaimer text shown directly â€” no expand/collapse needed
        Text(
            text = stringResource(R.string.disclaimer_text),
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.85f),
            lineHeight = 20.sp
        )
    }
}



@Composable
fun CompleteSetupButton(
    modifier: Modifier = Modifier,
    text: String,
    showArrow: Boolean = true,
    enabled: Boolean,
    onCompleteSetup: () -> Unit
) {
    val accent = LocalProximityAccentTheme.current
    val buttonShape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .height(56.dp)
            .background(
                color = if (enabled) accent.accent else ProximityMediumGray,
                shape = buttonShape
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = if (enabled) 0.15f else 0.05f),
                shape = buttonShape
            )
            .clip(buttonShape)
            .clickable(enabled = enabled) { onCompleteSetup() }
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp
            )
            if (showArrow) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}


@Composable
fun PermissionsSection(
    onAllPermissionsGranted: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val permissionManager = remember { activity?.let { PermissionManager(it) } }

    var coreGranted by remember { mutableStateOf(permissionManager?.checkAllPermissions() ?: false) }
    var overlayGranted by remember { mutableStateOf(permissionManager?.hasOverlayPermission() ?: false) }

    // Check total status
    LaunchedEffect(coreGranted, overlayGranted) {
        onAllPermissionsGranted(coreGranted && overlayGranted)
    }

    // Lifecycle observer for returning from Settings
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coreGranted = permissionManager?.checkAllPermissions() ?: false
                overlayGranted = permissionManager?.hasOverlayPermission() ?: false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        coreGranted = permissionManager?.checkAllPermissions() ?: false
    }

    val intentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        overlayGranted = permissionManager?.hasOverlayPermission() ?: false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Proximity needs these permissions to automatically and securely make USSD payments for you.",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 15.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Core Permissions Card
        PermissionCard(
            title = "Core App Permissions",
            description = "Allows Proximity to open the dialer with USSD codes pre-filled (you confirm the call), send payment notifications, verify bank SMS, and let you pick contacts.",
            icon = Icons.Filled.Phone,
            isGranted = coreGranted,
            onClick = {
                val permsToRequest = PermissionConstants.REQUIRED_PERMISSIONS.toMutableList()
                permsToRequest.addAll(PermissionConstants.PHONE_PERMISSIONS)
                permsToRequest.add(android.Manifest.permission.RECEIVE_SMS)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
                }
                multiplePermissionsLauncher.launch(permsToRequest.distinct().toTypedArray())
            }
        )

        // Overlay Permission Card
        PermissionCard(
            title = "Display Over Apps",
            description = "Allows Proximity to show the secure payment overlay while the " +
                "call is happening in the background.",
            icon = Icons.Filled.Layers,
            isGranted = overlayGranted,
            onClick = {
                permissionManager?.overlayPermissionSettingsIntent()?.let { intent ->
                    intentLauncher.launch(intent)
                }
            }
        )

    }
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    val accentColor = LocalProximityAccentTheme.current.accent
    val statusGreen = StatusGreen
    val bgColor = if (isGranted) statusGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
    val borderColor = if (isGranted) statusGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(enabled = !isGranted, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isGranted) statusGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Filled.CheckCircle else icon,
                contentDescription = null,
                tint = if (isGranted) statusGreen else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }

        if (isGranted) {
            Text(
                text = "Granted",
                color = statusGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        } else {
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Allow", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun WelcomeSection(
    termsAccepted: Boolean,
    onTermsAcceptedChange: (Boolean) -> Unit,
    onTermsClick: () -> Unit,
    onContinue: () -> Unit
) {
    // Dynamic Theme Colors
    val textPrimary = MaterialTheme.colorScheme.onBackground
    val textMuted = MaterialTheme.colorScheme.onSurfaceVariant
    val tealBrand = MaterialTheme.colorScheme.primary
    val disabledBg = MaterialTheme.colorScheme.surfaceVariant
    val disabledText = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Main Content Section ---
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Welcome to",
                fontSize = 13.sp,
                color = textMuted,
                fontWeight = FontWeight.Normal,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "Proximity Logo",
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(16.dp))
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Proximity",
                fontSize = 32.sp,
                color = textPrimary,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Pay anyone, anywhere.",
                    fontSize = 13.5.sp,
                    color = textMuted,
                    lineHeight = 20.sp
                )
                Text(
                    text = "Without internet.",
                    fontSize = 13.5.sp,
                    color = textMuted,
                    lineHeight = 20.sp
                )
                Text(
                    text = "Simple payments, closer to you.",
                    fontSize = 13.5.sp,
                    color = textMuted,
                    lineHeight = 20.sp
                )
            }
        }

        // --- Footer Action Section ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onTermsAcceptedChange(!termsAccepted) }
            ) {
                Checkbox(
                    checked = termsAccepted,
                    onCheckedChange = { onTermsAcceptedChange(it) },
                    colors = androidx.compose.material3.CheckboxDefaults.colors(
                        checkedColor = tealBrand,
                        uncheckedColor = MaterialTheme.colorScheme.outline,
                        checkmarkColor = MaterialTheme.colorScheme.onPrimary
                    )
                )

                Row(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = "I agree to the ",
                        color = textMuted,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Terms & Conditions",
                        color = tealBrand,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                        modifier = Modifier.clickable { onTermsClick() }
                    )
                }
            }

            Text(
                text = "Accept the terms to continue.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )

            Button(
                onClick = onContinue,
                enabled = termsAccepted,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(52.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = tealBrand,
                    disabledContainerColor = disabledBg
                )
            ) {
                Text(
                    text = "Continue",
                    color = if (termsAccepted) MaterialTheme.colorScheme.onPrimary else disabledText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Continue",
                    tint = if (termsAccepted) MaterialTheme.colorScheme.onPrimary else disabledText,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun GroundRulesSection() {
    val accent = LocalProximityAccentTheme.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Keep these in mind for a safer experience with Proximity.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // Card 1: Just two details for setup
        GroundRuleCard(
            icon = Icons.Default.AccountBalance,
            title = "Just two details for setup",
            description = "We only ask for your Bank Name and SIM Operator. We do not ask your Bank IFSC code, any OTP, or UPI access.",
            accentColor = accent.primary
        )

        // Card 2: Keep sensitive details private
        GroundRuleCard(
            icon = Icons.Default.Lock,
            title = "Keep sensitive details private",
            description = "Any kind of banking password or UPI pin is being entered on official government portals. Proximity never read or stores such information.",
            accentColor = accent.primary
        )

        // Card 3: Check before you pay
        GroundRuleCard(
            icon = Icons.Default.CheckCircle,
            title = "Check before you pay",
            description = "Confirm the recipient's name and mobile number or QR details before sending a payment.",
            accentColor = accent.primary
        )
    }
}

@Composable
private fun GroundRuleCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    accentColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

