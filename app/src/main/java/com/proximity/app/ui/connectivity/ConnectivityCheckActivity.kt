// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.ui.connectivity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.proximity.app.AppCore
import com.proximity.app.R
import com.proximity.app.accessibility.UssdDialogReaderService
import com.proximity.app.data.UserSettings
import com.proximity.app.telephony.CallMonitor
import com.proximity.app.theme.AppTheme
import com.proximity.app.theme.ColorTokens
import com.proximity.app.theme.LocalAppVisuals
import com.proximity.app.ui.components.AnimatedBackground
import com.proximity.app.ui.components.GlassCard
import com.proximity.app.ui.components.RipplePulseOrb
import com.proximity.app.ui.components.pressScale
import com.proximity.app.ui.onboarding.OnboardingFlowActivity
import com.proximity.app.ui.shell.HomeShellActivity
import kotlinx.coroutines.delay

class ConnectivityCheckActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val app = application as AppCore
        val coordinator = ConnectivityTestCoordinator(app.services.preferencesStore)

        setContent {
            val settings by app.services.preferencesStore.settingsFlow.collectAsState()
            val isInitialSetup = remember { !settings.connectivityTestsCompleted }
            AppTheme(settings = settings) {
                ConnectivityCheckScreen(
                    settings = settings,
                    coordinator = coordinator,
                    onBackToSetup = {
                        if (isInitialSetup) {
                            startActivity(Intent(this, OnboardingFlowActivity::class.java))
                        }
                        finish()
                    },
                    onFinishTests = {
                        coordinator.markAllTestsCompleted()
                        if (isInitialSetup) {
                            startActivity(
                                Intent(this, HomeShellActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                            )
                        }
                        finish()
                    }
                )
            }
        }
    }
}

private enum class ConnectivityModalState {
    NONE,
    USSD_INSTRUCTION,
    USSD_CONSENT,
    USSD_PROGRESS,
    UPI123_INSTRUCTION,
    UPI123_CONSENT,
    UPI123_PROGRESS
}

@Composable
fun ConnectivityCheckScreen(
    settings: UserSettings,
    coordinator: ConnectivityTestCoordinator,
    onBackToSetup: () -> Unit,
    onFinishTests: () -> Unit
) {
    val context = LocalContext.current
    val visuals = LocalAppVisuals.current

    var modalState by rememberSaveable { mutableStateOf(ConnectivityModalState.NONE) }
    var showShortcutsAfter3s by remember { mutableStateOf(false) }
    var askConfirmComplete by remember { mutableStateOf(false) }

    val callMonitor = remember { CallMonitor(context) }

    DisposableEffect(Unit) {
        onDispose {
            callMonitor.stopMonitoring()
            UssdDialogReaderService.ussdPaymentActive = false
            UssdDialogReaderService.ussdCallback = null
        }
    }

    LaunchedEffect(modalState) {
        showShortcutsAfter3s = false
        askConfirmComplete = false
        when (modalState) {
            ConnectivityModalState.USSD_PROGRESS -> {
                UssdDialogReaderService.ussdCallback = {
                    askConfirmComplete = true
                }
                coordinator.dialUssdTest(context)
                delay(3000L)
                showShortcutsAfter3s = true
                delay(22000L)
                askConfirmComplete = true
            }
            ConnectivityModalState.UPI123_PROGRESS -> {
                callMonitor.startMonitoring {
                    askConfirmComplete = true
                }
                coordinator.dialUpi123Test(context)
                delay(3000L)
                showShortcutsAfter3s = true
            }
            else -> {
                callMonitor.stopMonitoring()
                UssdDialogReaderService.ussdPaymentActive = false
                UssdDialogReaderService.ussdCallback = null
            }
        }
    }

    val isUssdWaived = settings.isJioPrimarySim || settings.ussdReportedNotWorking
    val canContinue = settings.upi123TestPassed || settings.ussdTestPassed || isUssdWaived
    val allPassed = settings.upi123TestPassed && (settings.ussdTestPassed || settings.isJioPrimarySim)

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackToSetup) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_back_arrow),
                        contentDescription = stringResource(id = R.string.cd_back),
                        tint = visuals.textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(id = R.string.testcfg_title),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    color = visuals.textPrimary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            val ussdSubtitle = when {
                settings.isJioPrimarySim -> stringResource(id = R.string.testcfg_jio_no_ussd)
                settings.ussdReportedNotWorking -> stringResource(id = R.string.testcfg_ussd_reported_issue)
                else -> stringResource(id = R.string.testcfg_enable_scan_payments)
            }
            ConnectivityCardItem(
                title = stringResource(id = R.string.testcfg_ussd_card_title),
                codeBadge = "*99#",
                subtitle = ussdSubtitle,
                isPassed = settings.ussdTestPassed,
                isWarning = settings.isJioPrimarySim || settings.ussdReportedNotWorking,
                onClick = {
                    if (settings.isJioPrimarySim) {
                        Toast.makeText(context, R.string.testcfg_jio_no_ussd, Toast.LENGTH_SHORT).show()
                    } else {
                        modalState = ConnectivityModalState.USSD_INSTRUCTION
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            ConnectivityCardItem(
                title = stringResource(id = R.string.testcfg_upi123_card_title),
                codeBadge = "UPI123",
                subtitle = stringResource(id = R.string.testcfg_enable_manual_payments),
                isPassed = settings.upi123TestPassed,
                isWarning = false,
                onClick = {
                    modalState = ConnectivityModalState.UPI123_INSTRUCTION
                }
            )

            Spacer(modifier = Modifier.weight(1f))

            val ctaLabel = when {
                allPassed -> stringResource(id = R.string.testcfg_all_passed_continue)
                canContinue -> stringResource(id = R.string.testcfg_partial_continue)
                else -> stringResource(id = R.string.testcfg_complete_to_continue)
            }

            Button(
                onClick = onFinishTests,
                enabled = canContinue,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ColorTokens.BrandTeal,
                    disabledContainerColor = visuals.outline.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .pressScale(enabled = canContinue)
            ) {
                Text(
                    text = ctaLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(id = R.string.testcfg_skip_for_now),
                fontSize = 13.sp,
                color = visuals.textSecondary,
                textDecoration = TextDecoration.Underline,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFinishTests() }
                    .padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    when (modalState) {
        ConnectivityModalState.USSD_INSTRUCTION -> {
            AlertDialog(
                onDismissRequest = { modalState = ConnectivityModalState.NONE },
                containerColor = visuals.highSurface,
                title = {
                    Text(
                        text = stringResource(id = R.string.testcfg_ussd_instr_title),
                        fontWeight = FontWeight.Bold,
                        color = visuals.textPrimary
                    )
                },
                text = {
                    Text(
                        text = stringResource(id = R.string.testcfg_ussd_instr_body),
                        color = visuals.textSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { modalState = ConnectivityModalState.USSD_CONSENT },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorTokens.BrandTeal)
                    ) {
                        Text(text = stringResource(id = R.string.action_proceed), color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            coordinator.markUssdPassed()
                            modalState = ConnectivityModalState.NONE
                        }
                    ) {
                        Text(
                            text = stringResource(id = R.string.testcfg_already_set_up),
                            color = visuals.adaptiveTealLabel
                        )
                    }
                }
            )
        }

        ConnectivityModalState.USSD_CONSENT -> {
            AlertDialog(
                onDismissRequest = { modalState = ConnectivityModalState.NONE },
                containerColor = visuals.highSurface,
                title = {
                    Text(
                        text = stringResource(id = R.string.testcfg_consent_title),
                        fontWeight = FontWeight.Bold,
                        color = visuals.textPrimary
                    )
                },
                text = {
                    Text(
                        text = stringResource(id = R.string.testcfg_consent_body),
                        color = visuals.textSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { modalState = ConnectivityModalState.USSD_PROGRESS },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorTokens.BrandTeal)
                    ) {
                        Text(text = stringResource(id = R.string.action_proceed), color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { modalState = ConnectivityModalState.NONE }) {
                        Text(text = stringResource(id = R.string.action_cancel), color = visuals.textSecondary)
                    }
                }
            )
        }

        ConnectivityModalState.USSD_PROGRESS -> {
            AlertDialog(
                onDismissRequest = { modalState = ConnectivityModalState.NONE },
                containerColor = visuals.highSurface,
                title = {
                    Text(
                        text = if (askConfirmComplete) {
                            stringResource(id = R.string.testcfg_setup_complete_title)
                        } else {
                            stringResource(id = R.string.testcfg_ussd_dialing)
                        },
                        fontWeight = FontWeight.Bold,
                        color = visuals.textPrimary
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        RipplePulseOrb(sizeDp = 80.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(id = R.string.testcfg_ussd_complete_body),
                            color = visuals.textSecondary,
                            textAlign = TextAlign.Center
                        )
                        if (showShortcutsAfter3s && !askConfirmComplete) {
                            Spacer(modifier = Modifier.height(12.dp))
                            TextButton(
                                onClick = {
                                    coordinator.markUssdPassed()
                                    modalState = ConnectivityModalState.NONE
                                }
                            ) {
                                Text(
                                    text = stringResource(id = R.string.testcfg_ussd_already_shortcut),
                                    color = ColorTokens.BrandTeal
                                )
                            }
                            TextButton(
                                onClick = {
                                    coordinator.markUssdReportedIssue()
                                    modalState = ConnectivityModalState.NONE
                                }
                            ) {
                                Text(
                                    text = stringResource(id = R.string.testcfg_ussd_does_not_work),
                                    color = ColorTokens.StatusPendingAmber
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    if (askConfirmComplete) {
                        Button(
                            onClick = {
                                coordinator.markUssdPassed()
                                modalState = ConnectivityModalState.NONE
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ColorTokens.BrandTeal)
                        ) {
                            Text(text = stringResource(id = R.string.action_yes), color = Color.White)
                        }
                    }
                },
                dismissButton = {
                    if (askConfirmComplete) {
                        OutlinedButton(onClick = { modalState = ConnectivityModalState.NONE }) {
                            Text(text = stringResource(id = R.string.action_not_yet), color = visuals.textSecondary)
                        }
                    }
                }
            )
        }

        ConnectivityModalState.UPI123_INSTRUCTION -> {
            AlertDialog(
                onDismissRequest = { modalState = ConnectivityModalState.NONE },
                containerColor = visuals.highSurface,
                title = {
                    Text(
                        text = stringResource(id = R.string.testcfg_upi123_instr_title),
                        fontWeight = FontWeight.Bold,
                        color = visuals.textPrimary
                    )
                },
                text = {
                    Text(
                        text = stringResource(id = R.string.testcfg_upi123_instr_body),
                        color = visuals.textSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { modalState = ConnectivityModalState.UPI123_CONSENT },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorTokens.BrandTeal)
                    ) {
                        Text(text = stringResource(id = R.string.action_proceed), color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            coordinator.markUpi123Passed()
                            modalState = ConnectivityModalState.NONE
                        }
                    ) {
                        Text(
                            text = stringResource(id = R.string.testcfg_already_set_up),
                            color = visuals.adaptiveTealLabel
                        )
                    }
                }
            )
        }

        ConnectivityModalState.UPI123_CONSENT -> {
            AlertDialog(
                onDismissRequest = { modalState = ConnectivityModalState.NONE },
                containerColor = visuals.highSurface,
                title = {
                    Text(
                        text = stringResource(id = R.string.testcfg_consent_title),
                        fontWeight = FontWeight.Bold,
                        color = visuals.textPrimary
                    )
                },
                text = {
                    Text(
                        text = stringResource(id = R.string.testcfg_consent_body),
                        color = visuals.textSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { modalState = ConnectivityModalState.UPI123_PROGRESS },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorTokens.BrandTeal)
                    ) {
                        Text(text = stringResource(id = R.string.action_proceed), color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { modalState = ConnectivityModalState.NONE }) {
                        Text(text = stringResource(id = R.string.action_cancel), color = visuals.textSecondary)
                    }
                }
            )
        }

        ConnectivityModalState.UPI123_PROGRESS -> {
            AlertDialog(
                onDismissRequest = { modalState = ConnectivityModalState.NONE },
                containerColor = visuals.highSurface,
                title = {
                    Text(
                        text = stringResource(id = R.string.testcfg_setup_complete_title),
                        fontWeight = FontWeight.Bold,
                        color = visuals.textPrimary
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        RipplePulseOrb(sizeDp = 80.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(id = R.string.testcfg_upi123_complete_body),
                            color = visuals.textSecondary,
                            textAlign = TextAlign.Center
                        )
                        if (showShortcutsAfter3s && !askConfirmComplete) {
                            Spacer(modifier = Modifier.height(10.dp))
                            TextButton(
                                onClick = {
                                    coordinator.markUpi123Passed()
                                    modalState = ConnectivityModalState.NONE
                                }
                            ) {
                                Text(
                                    text = stringResource(id = R.string.testcfg_upi123_already_shortcut),
                                    color = ColorTokens.BrandTeal
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            coordinator.markUpi123Passed()
                            modalState = ConnectivityModalState.NONE
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorTokens.BrandTeal)
                    ) {
                        Text(text = stringResource(id = R.string.action_yes), color = Color.White)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { modalState = ConnectivityModalState.NONE }) {
                        Text(text = stringResource(id = R.string.action_not_yet), color = visuals.textSecondary)
                    }
                }
            )
        }

        ConnectivityModalState.NONE -> {}
    }
}

@Composable
private fun ConnectivityCardItem(
    title: String,
    codeBadge: String,
    subtitle: String,
    isPassed: Boolean,
    isWarning: Boolean,
    onClick: () -> Unit
) {
    val visuals = LocalAppVisuals.current
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(onClick = onClick),
        cornerRadius = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = visuals.textPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ColorTokens.BrandTeal.copy(alpha = 0.16f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = codeBadge,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = visuals.adaptiveTealLabel
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = if (isWarning) visuals.statusPendingText else visuals.textSecondary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isPassed -> ColorTokens.StatusSuccessGreen.copy(alpha = 0.18f)
                            isWarning -> ColorTokens.StatusPendingAmber.copy(alpha = 0.20f)
                            else -> ColorTokens.BrandTeal.copy(alpha = 0.16f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isPassed -> Icon(
                        painter = painterResource(id = R.drawable.ic_check_circle),
                        contentDescription = null,
                        tint = visuals.statusSuccessText,
                        modifier = Modifier.size(20.dp)
                    )
                    isWarning -> Text(
                        text = "!",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = visuals.statusPendingText
                    )
                    else -> Icon(
                        painter = painterResource(id = R.drawable.ic_forward_arrow),
                        contentDescription = null,
                        tint = visuals.adaptiveTealLabel,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
