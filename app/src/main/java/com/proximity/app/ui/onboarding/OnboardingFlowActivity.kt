// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.ui.onboarding

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.proximity.app.AppCore
import com.proximity.app.R
import com.proximity.app.data.BankCatalog
import com.proximity.app.data.OperatorCatalog
import com.proximity.app.data.TelecomOperator
import com.proximity.app.theme.AppTheme
import com.proximity.app.theme.ColorTokens
import com.proximity.app.theme.LocalAppVisuals
import com.proximity.app.ui.components.AnimatedBackground
import com.proximity.app.ui.components.GlassCard
import com.proximity.app.ui.components.OperatorLogoBadge
import com.proximity.app.ui.components.pressScale
import com.proximity.app.ui.connectivity.ConnectivityCheckActivity
import com.proximity.app.ui.terms.TermsScreen
import com.proximity.app.utils.PermissionChecker

class OnboardingFlowActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val app = application as AppCore
        val coordinator = OnboardingCoordinator(app.services.preferencesStore)

        setContent {
            val settings by app.services.preferencesStore.settingsFlow.collectAsState()
            AppTheme(settings = settings) {
                OnboardingFlowScreen(
                    initialBankId = settings.selectedBankId,
                    initialPrimarySim = settings.primarySimCarrierId,
                    onOpenTerms = {
                        startActivity(Intent(this, TermsScreen::class.java))
                    },
                    onCompleteSetup = { bankId, primarySim ->
                        coordinator.completeOnboarding(bankId, primarySim)
                        startActivity(Intent(this, ConnectivityCheckActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun OnboardingFlowScreen(
    initialBankId: String,
    initialPrimarySim: String,
    onOpenTerms: () -> Unit,
    onCompleteSetup: (String, String) -> Unit
) {
    val context = LocalContext.current
    val visuals = LocalAppVisuals.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var currentStep by rememberSaveable { mutableIntStateOf(1) }
    var termsAccepted by rememberSaveable { mutableStateOf(false) }
    var selectedBankId by rememberSaveable { mutableStateOf(initialBankId.ifBlank { "" }) }
    var primarySimId by rememberSaveable { mutableStateOf(initialPrimarySim) }
    var permissionsGranted by remember {
        mutableStateOf(PermissionChecker.areCorePermissionsGranted(context))
    }
    val isPhoneAllowed = remember(permissionsGranted) { PermissionChecker.isPhoneGranted(context) }
    val isCameraAllowed = remember(permissionsGranted) { PermissionChecker.isCameraGranted(context) }
    val isSmsAllowed = remember(permissionsGranted) { PermissionChecker.isSmsGranted(context) }
    val isContactsAllowed = remember(permissionsGranted) { PermissionChecker.isContactsGranted(context) }
    val isNotifAllowed = remember(permissionsGranted) { PermissionChecker.isNotificationsGranted(context) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionsGranted = PermissionChecker.areCorePermissionsGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionsGranted = PermissionChecker.areCorePermissionsGranted(context)
    }

    BackHandler(enabled = currentStep > 1) {
        currentStep--
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 420.dp)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (currentStep == 1) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(id = R.string.setup_welcome_to),
                    fontSize = 18.sp,
                    color = visuals.textSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = stringResource(id = R.string.cd_app_logo),
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(id = R.string.app_name),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    color = visuals.textPrimary
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(id = R.string.setup_tagline_1),
                    fontSize = 16.sp,
                    color = visuals.textPrimary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(id = R.string.setup_tagline_2),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ColorTokens.BrandTeal,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(id = R.string.setup_tagline_3),
                    fontSize = 16.sp,
                    color = visuals.textSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.weight(1f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = termsAccepted,
                        onCheckedChange = { termsAccepted = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = ColorTokens.BrandTeal,
                            uncheckedColor = visuals.textSecondary
                        )
                    )
                    Text(
                        text = stringResource(id = R.string.setup_agree_prefix),
                        fontSize = 14.sp,
                        color = visuals.textPrimary
                    )
                    Text(
                        text = stringResource(id = R.string.setup_terms_link),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ColorTokens.BrandTeal,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable { onOpenTerms() }
                    )
                }
                Text(
                    text = stringResource(id = R.string.setup_accept_terms_helper),
                    fontSize = 12.sp,
                    color = visuals.textSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Button(
                    onClick = { currentStep = 2 },
                    enabled = termsAccepted,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ColorTokens.BrandTeal,
                        disabledContainerColor = visuals.outline.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .pressScale(enabled = termsAccepted)
                ) {
                    Text(
                        text = stringResource(id = R.string.action_continue),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            } else {
                val progress = ((currentStep - 1) / 4f).coerceIn(0.25f, 1f)
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = ColorTokens.BrandTeal,
                    trackColor = visuals.outline.copy(alpha = 0.25f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { currentStep-- }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_back_arrow),
                            contentDescription = stringResource(id = R.string.cd_back),
                            tint = visuals.textPrimary
                        )
                    }
                }
                val stepTitleRes = when (currentStep) {
                    2 -> R.string.setup_choose_your_bank
                    3 -> R.string.setup_select_your_sim
                    4 -> R.string.setup_ground_rules_title
                    else -> R.string.setup_permissions_title
                }
                Text(
                    text = stringResource(id = stepTitleRes),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    color = visuals.textPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (currentStep) {
                        2 -> {
                            var dropdownExpanded by remember { mutableStateOf(false) }
                            val currentBank = BankCatalog.findById(selectedBankId)
                            Text(
                                text = stringResource(id = R.string.setup_choose_bank_subtitle),
                                fontSize = 14.sp,
                                color = visuals.textSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(modifier = Modifier.fillMaxWidth()) {
                                GlassCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { dropdownExpanded = true },
                                    cornerRadius = 16.dp
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(18.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = currentBank?.displayName
                                                ?: stringResource(id = R.string.setup_select_bank),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (currentBank != null) visuals.textPrimary else visuals.textSecondary
                                        )
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_forward_arrow),
                                            contentDescription = null,
                                            tint = ColorTokens.BrandTeal,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = dropdownExpanded,
                                    onDismissRequest = { dropdownExpanded = false },
                                    modifier = Modifier
                                        .fillMaxWidth(0.86f)
                                        .background(visuals.highSurface)
                                ) {
                                    BankCatalog.ALL_BANKS.forEach { bank ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = bank.displayName,
                                                    color = visuals.textPrimary,
                                                    fontWeight = if (bank.id == selectedBankId) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            onClick = {
                                                selectedBankId = bank.id
                                                dropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        3 -> {
                            OperatorCatalog.ALL_OPERATORS.forEach { operator ->
                                OperatorSelectionCard(
                                    operator = operator,
                                    isSelected = primarySimId == operator.id,
                                    onSelect = { primarySimId = operator.id }
                                )
                            }
                        }

                        4 -> {
                            Text(
                                text = stringResource(id = R.string.setup_ground_rules_subtitle),
                                fontSize = 14.sp,
                                color = visuals.textSecondary
                            )
                            val rules = listOf(
                                Triple(R.string.setup_rule_1_title, R.string.setup_rule_1_body, null to R.drawable.ic_wallet),
                                Triple(R.string.setup_rule_2_title, R.string.setup_rule_2_body, null to R.drawable.ic_lock),
                                Triple(R.string.setup_rule_3_title, R.string.setup_rule_3_body, null to R.drawable.ic_check_circle)
                            )
                            rules.forEachIndexed { idx, (titleRes, bodyRes, iconPair) ->
                                val (vecIcon, resIcon) = iconPair
                                GlassCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    cornerRadius = 16.dp
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(ColorTokens.BrandTeal.copy(alpha = 0.14f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (vecIcon != null) {
                                                Icon(
                                                    imageVector = vecIcon,
                                                    contentDescription = null,
                                                    tint = ColorTokens.BrandTeal,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            } else if (resIcon != null) {
                                                Icon(
                                                    painter = painterResource(id = resIcon),
                                                    contentDescription = null,
                                                    tint = ColorTokens.BrandTeal,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${idx + 1}. ${stringResource(id = titleRes)}",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = visuals.textPrimary
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = stringResource(id = bodyRes),
                                                fontSize = 14.sp,
                                                lineHeight = 20.sp,
                                                color = visuals.textSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        5 -> {
                            Text(
                                text = stringResource(id = R.string.setup_permissions_subtitle),
                                fontSize = 14.sp,
                                color = visuals.textSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            // 1. Phone & Calls
                            OnboardingPermissionCard(
                                title = stringResource(id = R.string.settings_perm_phone),
                                description = stringResource(id = R.string.setup_perm_phone_desc),
                                iconRes = R.drawable.ic_phone_call,
                                isGranted = isPhoneAllowed,
                                onGrant = {
                                    permLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.CALL_PHONE,
                                            Manifest.permission.READ_PHONE_STATE
                                        )
                                    )
                                }
                            )

                            // 2. Camera Access
                            OnboardingPermissionCard(
                                title = stringResource(id = R.string.settings_perm_camera),
                                description = stringResource(id = R.string.setup_perm_camera_desc),
                                iconRes = R.drawable.ic_camera,
                                isGranted = isCameraAllowed,
                                onGrant = {
                                    permLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                                }
                            )

                            // 3. SMS Verification
                            OnboardingPermissionCard(
                                title = stringResource(id = R.string.settings_perm_sms),
                                description = stringResource(id = R.string.setup_perm_sms_desc),
                                iconRes = R.drawable.ic_wallet,
                                isGranted = isSmsAllowed,
                                onGrant = {
                                    permLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS))
                                }
                            )

                            // 4. Contacts
                            OnboardingPermissionCard(
                                title = stringResource(id = R.string.settings_perm_contacts),
                                description = stringResource(id = R.string.setup_perm_contacts_desc),
                                iconRes = R.drawable.ic_contact,
                                isGranted = isContactsAllowed,
                                onGrant = {
                                    permLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                                }
                            )

                            // 5. Notifications (if Android 13+)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                OnboardingPermissionCard(
                                    title = stringResource(id = R.string.settings_perm_notifications),
                                    description = stringResource(id = R.string.setup_perm_notifications_desc),
                                    iconVector = Icons.Default.Notifications,
                                    isGranted = isNotifAllowed,
                                    onGrant = {
                                        permLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                                    }
                                )
                            }

                            if (!permissionsGranted) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = {
                                        permLauncher.launch(PermissionChecker.requiredCorePermissions())
                                    },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ColorTokens.BrandTeal
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .pressScale()
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.setup_grant_all_permissions),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                val canAdvance = when (currentStep) {
                    2 -> selectedBankId.isNotBlank()
                    3 -> primarySimId.isNotBlank()
                    4 -> true
                    5 -> permissionsGranted
                    else -> false
                }
                val buttonLabelRes = when (currentStep) {
                    4 -> R.string.action_proceed
                    5 -> R.string.setup_complete_button
                    else -> R.string.action_continue
                }

                Button(
                    onClick = {
                        if (currentStep < 5) {
                            currentStep++
                        } else {
                            onCompleteSetup(selectedBankId, primarySimId)
                        }
                    },
                    enabled = canAdvance,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ColorTokens.BrandTeal,
                        disabledContainerColor = visuals.outline.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .pressScale(enabled = canAdvance)
                ) {
                    Text(
                        text = stringResource(id = buttonLabelRes),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        painter = painterResource(id = R.drawable.ic_forward_arrow),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun OperatorSelectionCard(
    operator: TelecomOperator,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val visuals = LocalAppVisuals.current
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, ColorTokens.BrandTeal, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                }
            )
            .pressScale(onClick = onSelect),
        cornerRadius = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OperatorLogoBadge(carrierId = operator.id)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = operator.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = visuals.textPrimary
                )
                Text(
                    text = operator.subtitle,
                    fontSize = 13.sp,
                    color = visuals.textSecondary
                )
            }
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        color = if (isSelected) ColorTokens.BrandTeal else visuals.outline,
                        shape = CircleShape
                    )
                    .background(if (isSelected) ColorTokens.BrandTeal else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_check_circle),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onGrant: () -> Unit,
    iconRes: Int? = null,
    iconVector: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    val visuals = LocalAppVisuals.current
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(ColorTokens.BrandTeal.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                if (iconRes != null) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        tint = visuals.adaptiveTealLabel,
                        modifier = Modifier.size(20.dp)
                    )
                } else if (iconVector != null) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = visuals.adaptiveTealLabel,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = visuals.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = visuals.textSecondary
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            if (isGranted) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_check_circle),
                        contentDescription = null,
                        tint = visuals.adaptiveTealLabel,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(id = R.string.badge_allowed),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = visuals.textSecondary
                    )
                }
            } else {
                Button(
                    onClick = onGrant,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ColorTokens.BrandTeal
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.action_allow),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
