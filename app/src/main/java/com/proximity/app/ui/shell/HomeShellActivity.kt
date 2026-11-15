// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.ui.shell

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.proximity.app.AppCore
import com.proximity.app.R
import com.proximity.app.data.UserSettings
import com.proximity.app.theme.AppTheme
import com.proximity.app.theme.ColorTokens
import com.proximity.app.theme.LocalAppVisuals
import com.proximity.app.ui.components.ActionButton
import com.proximity.app.ui.components.AnimatedBackground
import com.proximity.app.ui.components.BottomNavBar
import com.proximity.app.ui.components.BrandedHeaderLockup
import com.proximity.app.ui.components.GlassCard
import com.proximity.app.ui.components.PermissionDialog
import com.proximity.app.ui.components.ShellNavTab
import com.proximity.app.ui.components.pressScale
import com.proximity.app.ui.history.TransactionHistoryContent
import com.proximity.app.ui.settings.SettingsContent
import com.proximity.app.utils.PermissionChecker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeShellActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val app = application as AppCore
        val screenModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HomeScreenModel(
                        preferencesStore = app.services.preferencesStore,
                        greetingIndex = app.services.processGreetingIndex
                    ) as T
                }
            }
        )[HomeScreenModel::class.java]

        setContent {
            val settings by screenModel.settingsFlow.collectAsState()
            val activeTab by screenModel.selectedTab.collectAsState()
            val stateHolder = rememberSaveableStateHolder()

            BackHandler(enabled = activeTab != ShellNavTab.HOME) {
                screenModel.selectTab(ShellNavTab.HOME)
            }

            AppTheme(settings = settings) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AnimatedBackground(modifier = Modifier.fillMaxSize())

                    AnimatedContent(
                        targetState = activeTab,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220)) togetherWith
                                fadeOut(animationSpec = tween(180))
                        },
                        label = "ShellTabHost"
                    ) { tab ->
                        stateHolder.SaveableStateProvider(key = tab.name) {
                            when (tab) {
                                ShellNavTab.HOME -> HomeDashboardContent(
                                    settings = settings,
                                    greetingRes = screenModel.resolveGreetingStringRes(),
                                    onMarkNotificationsPrompted = {
                                        screenModel.markNotificationsPromptedOnce()
                                    }
                                )
                                ShellNavTab.HISTORY -> TransactionHistoryContent()
                                ShellNavTab.SETTINGS -> SettingsContent()
                            }
                        }
                    }

                    BottomNavBar(
                        activeTab = activeTab,
                        onSelectTab = { selected ->
                            screenModel.selectTab(selected)
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }
}

@Composable
fun HomeDashboardContent(
    settings: UserSettings,
    greetingRes: Int,
    onMarkNotificationsPrompted: () -> Unit
) {
    val context = LocalContext.current
    val visuals = LocalAppVisuals.current
    val scope = rememberCoroutineScope()

    var isOpeningScanner by remember { mutableStateOf(false) }
    var pendingActionAfterSms by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showSmsRationaleDialog by remember { mutableStateOf(false) }

    val smsPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingActionAfterSms?.invoke()
        } else {
            Toast.makeText(context, R.string.sms_rationale_body, Toast.LENGTH_SHORT).show()
        }
        pendingActionAfterSms = null
    }

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    fun promptBestEffortNotificationsOnce() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !settings.promptedNotificationPermissionOnce &&
            !PermissionChecker.isNotificationsGranted(context)
        ) {
            onMarkNotificationsPrompted()
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun ensureReceiveSmsThenRun(action: () -> Unit) {
        promptBestEffortNotificationsOnce()
        if (PermissionChecker.isSmsGranted(context)) {
            action()
        } else {
            pendingActionAfterSms = action
            showSmsRationaleDialog = true
        }
    }

    val scanPulseScale by animateFloatAsState(
        targetValue = if (isOpeningScanner) 1.08f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "ScanTilePulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandedHeaderLockup()

            GlassCard(
                modifier = Modifier
                    .size(48.dp)
                    .pressScale { TransferOrchestrator.launchFaq(context) },
                shape = CircleShape,
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_question_circle),
                    contentDescription = stringResource(id = R.string.cd_help),
                    tint = visuals.adaptiveTealLabel,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            ColorTokens.BrandTeal,
                            ColorTokens.PressedDarkTeal
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.36f),
                            ColorTokens.SpecularLightTeal.copy(alpha = 0.22f),
                            Color.White.copy(alpha = 0.10f)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 36.dp, y = (-36).dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_wallet),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.90f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = settings.selectedBankDisplayName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.92f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = stringResource(id = greetingRes),
                    fontSize = 21.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val isScanUnlocked = settings.isScanQrUnlocked
            val scanLabel = when {
                isOpeningScanner -> stringResource(id = R.string.home_scan_opening)
                isScanUnlocked -> stringResource(id = R.string.home_scan_qr)
                else -> stringResource(id = R.string.home_setup_ussd)
            }
            ActionButton(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        scaleX = scanPulseScale
                        scaleY = scanPulseScale
                    },
                iconRes = if (isScanUnlocked) R.drawable.ic_qr_code else R.drawable.ic_lock,
                label = scanLabel,
                locked = !isScanUnlocked,
                onClick = {
                    TransferOrchestrator.openQrScannerOrSetup(context, settings) {
                        ensureReceiveSmsThenRun {
                            scope.launch {
                                isOpeningScanner = true
                                delay(180L)
                                TransferOrchestrator.launchQrScanner(context)
                                delay(300L)
                                isOpeningScanner = false
                            }
                        }
                    }
                }
            )

            val isPayUnlocked = settings.isPayContactUnlocked
            ActionButton(
                modifier = Modifier.weight(1f),
                iconRes = if (isPayUnlocked) R.drawable.ic_contact else R.drawable.ic_lock,
                label = if (isPayUnlocked) {
                    stringResource(id = R.string.home_pay_contact)
                } else {
                    stringResource(id = R.string.home_setup_upi123)
                },
                locked = !isPayUnlocked,
                onClick = {
                    TransferOrchestrator.openPayContactOrSetup(context, settings) {
                        ensureReceiveSmsThenRun {
                            TransferOrchestrator.launchPayContact(context)
                        }
                    }
                }
            )

            ActionButton(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.ic_recharge,
                label = stringResource(id = R.string.home_recharge),
                locked = false,
                onClick = { TransferOrchestrator.launchRechargePortal(context) }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .pressScale {
                    Toast.makeText(
                        context,
                        context.getString(R.string.home_rewards_toast),
                        Toast.LENGTH_SHORT
                    ).show()
                },
            cornerRadius = 20.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(ColorTokens.BrandTeal.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_gift),
                        contentDescription = null,
                        tint = visuals.adaptiveTealLabel,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.home_rewards_title),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = visuals.textPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(id = R.string.home_rewards_subtitle),
                        fontSize = 12.sp,
                        color = visuals.textSecondary
                    )
                }
                Icon(
                    painter = painterResource(id = R.drawable.ic_forward_arrow),
                    contentDescription = null,
                    tint = visuals.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.navigationBarsPadding().height(104.dp))
    }

    if (showSmsRationaleDialog) {
        PermissionDialog(
            title = stringResource(id = R.string.sms_rationale_title),
            body = stringResource(id = R.string.sms_rationale_body),
            onConfirm = {
                showSmsRationaleDialog = false
                smsPermLauncher.launch(Manifest.permission.RECEIVE_SMS)
            },
            onDismiss = {
                showSmsRationaleDialog = false
                pendingActionAfterSms = null
            }
        )
    }
}
