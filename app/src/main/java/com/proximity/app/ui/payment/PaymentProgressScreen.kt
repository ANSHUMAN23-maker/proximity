// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.ui.payment

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.proximity.app.AppCore
import com.proximity.app.R
import com.proximity.app.data.PaymentChannel
import com.proximity.app.data.PaymentRecord
import com.proximity.app.payment.PaymentState
import com.proximity.app.payment.Upi123ForegroundNotification
import com.proximity.app.telephony.CallMonitor
import com.proximity.app.telephony.MonitoredCallState
import com.proximity.app.theme.AppTheme
import com.proximity.app.theme.ColorTokens
import com.proximity.app.theme.LocalAppVisuals
import com.proximity.app.ui.components.AnimatedBackground
import com.proximity.app.ui.components.CurrencyDisplay
import com.proximity.app.ui.components.PaymentStageVisualizer
import com.proximity.app.ui.components.PaymentVisualizerStage
import com.proximity.app.ui.components.PulsingCircularProgress
import com.proximity.app.utils.CurrencyFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PaymentProgressScreen : ComponentActivity() {

    private var phone10State by mutableStateOf("")
    private var amountRupeesState by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val app = application as AppCore

        phone10State = intent.getStringExtra(EXTRA_PHONE_10).orEmpty()
        amountRupeesState = intent.getStringExtra(EXTRA_AMOUNT_RUPEES).orEmpty()

        setContent {
            val settings by app.services.preferencesStore.settingsFlow.collectAsState()
            val paymentState by app.services.paymentLifecycleManager.state.collectAsState()

            AppTheme(settings = settings) {
                InAppPaymentProgressView(
                    phone10 = phone10State,
                    amountRupees = amountRupeesState,
                    paymentState = paymentState,
                    onCallBecameOffHook = {
                        app.services.appScope.launch {
                            app.services.paymentLifecycleManager.markCallOrUssdInProgress()
                        }
                    },
                    onCallEndedWithDuration = { durationMs ->
                        app.services.paymentLifecycleManager.onUpi123CallEndedAsync(durationMs)
                    },
                    onCancelPayment = { reason ->
                        app.services.paymentLifecycleManager.cancelCurrentPaymentAsync(reason)
                    },
                    onFinishCancelled = {
                        app.services.paymentLifecycleManager.resetTerminalState()
                        finish()
                    },
                    onNavigateToResult = { record ->
                        startActivity(
                            Intent(this@PaymentProgressScreen, PaymentResultScreen::class.java).apply {
                                putExtra(PaymentResultScreen.EXTRA_PAYMENT_RECORD, record)
                            }
                        )
                        finish()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        phone10State = intent.getStringExtra(EXTRA_PHONE_10).orEmpty()
        amountRupeesState = intent.getStringExtra(EXTRA_AMOUNT_RUPEES).orEmpty()
    }

    companion object {
        const val EXTRA_PHONE_10 = "extra_phone_10"
        const val EXTRA_AMOUNT_RUPEES = "extra_amount_rupees"
    }
}

@Composable
fun InAppPaymentProgressView(
    phone10: String,
    amountRupees: String,
    paymentState: PaymentState,
    onCallBecameOffHook: () -> Unit,
    onCallEndedWithDuration: (Long) -> Unit,
    onCancelPayment: (String) -> Unit,
    onFinishCancelled: () -> Unit,
    onNavigateToResult: (PaymentRecord) -> Unit
) {
    val context = LocalContext.current
    val visuals = LocalAppVisuals.current
    val callMonitor = remember { CallMonitor(context) }
    val monitoredCallState by callMonitor.callState.collectAsState()

    val screenStartMillis = rememberSaveable { System.currentTimeMillis() }
    var sawOffHook by rememberSaveable { mutableStateOf(false) }
    var isCancelling by rememberSaveable { mutableStateOf(false) }

    fun triggerCancelOnce(reason: String) {
        if (isCancelling) return
        isCancelling = true
        callMonitor.terminateActiveCall()
        onCancelPayment(reason)
    }

    DisposableEffect(Unit) {
        callMonitor.startMonitoring { durationMs ->
            onCallEndedWithDuration(durationMs)
        }
        onDispose { callMonitor.stopMonitoring() }
    }

    LaunchedEffect(monitoredCallState) {
        if (monitoredCallState == MonitoredCallState.OFF_HOOK) {
            sawOffHook = true
            onCallBecameOffHook()
        }
    }

    LaunchedEffect(Unit) {
        repeat(3) {
            delay(13_333L)
            if (sawOffHook || isCancelling) return@LaunchedEffect
        }
        if (!sawOffHook && monitoredCallState != MonitoredCallState.IDLE) {
            delay(10_000L)
        }
        if (!sawOffHook) {
            triggerCancelOnce("Call never started within 40s")
        }
    }

    LaunchedEffect(paymentState) {
        val isRecentUpi123 = { rec: PaymentRecord ->
            rec.channel == PaymentChannel.UPI_123PAY &&
                rec.timestampMillis >= screenStartMillis - 15_000L
        }
        when (paymentState) {
            is PaymentState.Success -> {
                Upi123ForegroundNotification.cancel(context)
                if (isRecentUpi123(paymentState.transaction)) onNavigateToResult(paymentState.transaction)
            }
            is PaymentState.NeedsReview -> {
                Upi123ForegroundNotification.cancel(context)
                if (isRecentUpi123(paymentState.transaction)) onNavigateToResult(paymentState.transaction)
            }
            is PaymentState.Failed -> if (isRecentUpi123(paymentState.transaction)) {
                delay(800L)
                Upi123ForegroundNotification.cancel(context)
                onNavigateToResult(paymentState.transaction)
            }
            is PaymentState.Unverified -> {
                Upi123ForegroundNotification.cancel(context)
                if (isRecentUpi123(paymentState.transaction)) onNavigateToResult(paymentState.transaction)
            }
            is PaymentState.Timeout -> {
                Upi123ForegroundNotification.cancel(context)
                if (isRecentUpi123(paymentState.transaction)) onNavigateToResult(paymentState.transaction)
            }
            is PaymentState.Cancelled -> if (isRecentUpi123(paymentState.transaction)) {
                delay(900L)
                Upi123ForegroundNotification.cancel(context)
                onFinishCancelled()
            }
            is PaymentState.InProgress, is PaymentState.Initiating -> {
                Upi123ForegroundNotification.show(context, phone10, amountRupees)
            }
            else -> {}
        }
    }

    BackHandler {
        triggerCancelOnce("Cancelled via system back")
    }

    val visualizerStage = when {
        paymentState.isTerminal -> PaymentVisualizerStage.ENDING
        paymentState is PaymentState.WaitingForVerification -> PaymentVisualizerStage.WAITING_FOR_SMS
        monitoredCallState == MonitoredCallState.OFF_HOOK || paymentState is PaymentState.InProgress ->
            PaymentVisualizerStage.CALL_ACTIVE
        else -> PaymentVisualizerStage.CONNECTING
    }

    val statusTextRes = when {
        paymentState is PaymentState.Failed -> R.string.progress_status_failed
        paymentState is PaymentState.Cancelled -> R.string.progress_status_cancelled
        visualizerStage == PaymentVisualizerStage.WAITING_FOR_SMS -> R.string.progress_status_waiting_sms
        visualizerStage == PaymentVisualizerStage.CALL_ACTIVE -> R.string.progress_status_call_active
        else -> R.string.progress_status_connecting
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            PulsingCircularProgress(
                sizeDp = 156.dp
            )

            Spacer(modifier = Modifier.height(28.dp))

            CurrencyDisplay(
                amountStr = amountRupees,
                digitFontSize = 36.sp,
                color = visuals.textPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(
                    id = R.string.progress_to_recipient,
                    CurrencyFormat.formatPhoneForDisplay(phone10)
                ),
                fontSize = 14.sp,
                color = visuals.textSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            AnimatedContent(
                targetState = statusTextRes,
                transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(180)) },
                label = "ProgressStatusFade"
            ) { resId ->
                Text(
                    text = stringResource(id = resId),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = visuals.textPrimary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (visualizerStage != PaymentVisualizerStage.ENDING) {
                OutlinedButton(
                    onClick = {
                        triggerCancelOnce("User tapped Cancel payment")
                    },
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.progress_cancel_payment),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ColorTokens.StatusErrorRed
                    )
                }
            }
        }
    }
}
