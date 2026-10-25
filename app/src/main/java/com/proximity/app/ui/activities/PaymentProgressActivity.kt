package com.proximity.app.ui.activities

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.proximity.app.ui.components.*
import com.proximity.app.ProximityApplication
import com.proximity.app.R
import com.proximity.app.payment.PaymentSessionManager
import com.proximity.app.states.PaymentState
import com.proximity.app.telephony.CallStateCoordinator
import com.proximity.app.telephony.DeviceCallState
import com.proximity.app.ui.theme.*
import com.proximity.app.utils.CurrencyFormat
import com.proximity.app.ui.theme.AbyssalCharcoal
import com.proximity.app.ui.theme.TextPrimary
import com.proximity.app.ui.theme.TextSecondary
import com.proximity.app.ui.theme.TextDisabled
import com.proximity.app.ui.theme.ProximityTextWhite
import kotlinx.coroutines.launch

class PaymentProgressActivity : ComponentActivity() {

    companion object {
        private const val TAG = "PaymentProgressActivity"
        const val EXTRA_PHONE_NUMBER = "phone_number"
        const val EXTRA_AMOUNT = "amount"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: ""
        val amount = intent.getStringExtra(EXTRA_AMOUNT) ?: ""

        val app = ProximityApplication.from(this)
        val sessionManager = app?.paymentSessionManager
        val coordinator = app?.callStateCoordinator

        onBackPressedDispatcher.addCallback(this) {
            sessionManager?.onUserCancelled()
            finish()
        }

        val settingsRepository = app?.settingsRepository ?: com.proximity.app.data.SettingsRepository(applicationContext)

        setContent {
            val savedSettings by settingsRepository.settingsFlow.collectAsState()
            CompositionLocalProvider(LocalProximityAccentTheme provides TealAccentTheme) {
                ProximityTheme(darkTheme = savedSettings.darkModeEnabled) {
                    PaymentProgressScreen(
                        phoneNumber = phoneNumber,
                        amount = amount,
                        sessionManager = sessionManager,
                        coordinator = coordinator,
                        onFinish = { finish() },
                        onNavigateToResult = { finish() }
                    )
                }
            }
        }
    }
}

private enum class ProgressPhase {
    CONNECTING,
    CALL_ACTIVE,
    WAITING_FOR_SMS,
    ENDING
}

@Composable
private fun PaymentProgressScreen(
    phoneNumber: String,
    amount: String,
    sessionManager: PaymentSessionManager?,
    coordinator: CallStateCoordinator?,
    onFinish: () -> Unit,
    onNavigateToResult: () -> Unit
) {
    var phase by remember { mutableStateOf(ProgressPhase.CONNECTING) }
    var statusLine by remember { mutableStateOf("Connecting to UPI service…") }
    var outgoingCallSeen by remember { mutableStateOf(false) }

    LaunchedEffect(sessionManager) {
        sessionManager?.paymentState?.collect { state ->
            Log.d("PaymentProgressActivity", "PaymentState: $state")
            when (state) {
                is PaymentState.Initiating -> {
                    phase = ProgressPhase.CONNECTING
                    statusLine = "Connecting to UPI service…"
                }
                is PaymentState.InProgress -> {
                    phase = ProgressPhase.CALL_ACTIVE
                    statusLine = "Call active — do not leave the screen"
                }
                is PaymentState.WaitingForVerification -> {
                    phase = ProgressPhase.WAITING_FOR_SMS
                    statusLine = "Request sent. Waiting for bank confirmation…"
                }
                is PaymentState.Success, is PaymentState.NeedsReview -> {
                    onNavigateToResult()
                }
                is PaymentState.Failed -> {
                    phase = ProgressPhase.ENDING
                    statusLine = "Payment failed."
                    kotlinx.coroutines.delay(1800)
                    onFinish()
                }
                is PaymentState.Cancelled -> {
                    phase = ProgressPhase.ENDING
                    statusLine = "Payment cancelled."
                    kotlinx.coroutines.delay(1200)
                    onFinish()
                }
                is PaymentState.Timeout -> onFinish()
                else -> Unit
            }
        }
    }
    LaunchedEffect(coordinator) {
        coordinator?.callState?.collect { state ->
            when (state) {
                is DeviceCallState.OffHook -> {
                    if (!outgoingCallSeen) {
                        outgoingCallSeen = true
                        phase = ProgressPhase.CALL_ACTIVE
                        statusLine = "Call active — do not leave the screen"
                    }
                }
                is DeviceCallState.Idle -> {
                    if (outgoingCallSeen &&
                        phase != ProgressPhase.WAITING_FOR_SMS &&
                        phase != ProgressPhase.ENDING
                    ) {
                        phase = ProgressPhase.WAITING_FOR_SMS
                        statusLine = "Request sent. Waiting for bank confirmation…"
                    }
                }
                else -> Unit
            }
        }
    }

    LaunchedEffect(coordinator, outgoingCallSeen) {
        if (!outgoingCallSeen) {
            var retries = 0
            while (retries < 3) {
                kotlinx.coroutines.delay(40_000L)
                if (outgoingCallSeen) break
                val currentState = coordinator?.callState?.value
                if (currentState !is DeviceCallState.Idle) {
                    retries++
                    kotlinx.coroutines.delay(10_000L)
                } else {
                    Log.d("PaymentProgressActivity", "Timeout reached - no call detected")
                    sessionManager?.onCallNeverStarted()
                    break
                }
            }
            if (!outgoingCallSeen && retries >= 3) {
                Log.d("PaymentProgressActivity", "Max timeout retries reached")
                sessionManager?.onCallNeverStarted()
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContourBackground(modifier = Modifier.fillMaxSize())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                                val displayPhase = when (phase) {
                    ProgressPhase.CONNECTING -> PaymentDisplayPhase.CONNECTING
                    ProgressPhase.CALL_ACTIVE -> PaymentDisplayPhase.CALL_ACTIVE
                    ProgressPhase.WAITING_FOR_SMS -> PaymentDisplayPhase.WAITING_FOR_SMS
                    ProgressPhase.ENDING -> PaymentDisplayPhase.ENDING
                }

                AnimatedContent(
                    targetState = displayPhase,
                    transitionSpec = {
                        (fadeIn(tween(350)) + scaleIn(initialScale = 0.88f))
                            .togetherWith(fadeOut(tween(250)) + scaleOut(targetScale = 1.08f))
                    },
                    label = "paymentPhaseTransition"
                ) { currentDisplayPhase ->
                    PaymentPhaseVisualizer(
                        phase = currentDisplayPhase,
                        modifier = Modifier.size(160.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "₹${CurrencyFormat.inr(amount)}",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (phoneNumber.isNotBlank()) {
                    Text(
                        text = "To $phoneNumber",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                AnimatedContent(
                    targetState = statusLine,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "status_line"
                ) { line ->
                    Text(
                        text = line,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))

                if (phase != ProgressPhase.ENDING) {
                    OutlinedButton(
                        onClick = {
                            sessionManager?.onUserCancelled()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.overlay_terminate),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

