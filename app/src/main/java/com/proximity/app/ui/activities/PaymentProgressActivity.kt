package com.proximity.app.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.accessibilityservice.AccessibilityServiceInfo
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
import com.proximity.app.accessibility.UssdAccessibilityResult
import com.proximity.app.accessibility.UssdAccessibilityService
import com.proximity.app.accessibility.UssdStatus
import com.proximity.app.ui.components.*
import com.proximity.app.ui.dialogs.UssdAccessibilityConfirmDialog
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

    /** Whether the USSD accessibility confirmation dialog is currently visible. */
    private var showUssdConfirmDialog by mutableStateOf(false)
    /** The accessibility-detected USSD result, if any. */
    private var ussdAccessibilityResult by mutableStateOf<UssdAccessibilityResult?>(null)
    /** Whether accessibility has detected a successful payment and we are awaiting user confirmation. */
    private var ussdAccessibilityDetected by mutableStateOf(false)
    /** Whether the accessibility service enablement prompt is visible. */
    private var showAccessibilityPrompt by mutableStateOf(false)
    /** Settings repository for reading USSD accessibility setting. */
    private lateinit var settingsRepository: com.proximity.app.data.SettingsRepository

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

        settingsRepository = app?.settingsRepository ?: com.proximity.app.data.SettingsRepository(applicationContext)

        // Check if USSD accessibility setting is enabled in app settings
        val ussdAccessibilitySetting = settingsRepository.settingsFlow.value.ussdAccessibilityEnabled
        Log.d(TAG, "USSD accessibility setting: $ussdAccessibilitySetting")

        // Always ensure USSD payment active is enabled for the current payment session
        UssdAccessibilityService.ussdPaymentActive = true

        // Check if USSD accessibility setting is enabled in app settings and system settings
        val accessibilityServiceEnabled = UssdAccessibilityService.isAccessibilityEnabled(this)
        if (ussdAccessibilitySetting && !accessibilityServiceEnabled) {
            showAccessibilityPrompt = true
            Log.d(TAG, "USSD Accessibility Service not enabled in Android Settings - showing prompt")
        } else {
            Log.d(TAG, "USSD Accessibility Service enabled - activating payment detection mode")
        }

        // Wire up USSD accessibility callback for payment detection
        UssdAccessibilityService.ussdCallback = { result ->
            runOnUiThread {
                if (result.status == UssdStatus.SUCCESS) {
                    Log.d(TAG, "Accessibility detected USSD payment success: amount=${result.amount}, ref=${result.reference}, recipient=${result.recipientName}")
                    ussdAccessibilityResult = result
                    ussdAccessibilityDetected = true
                    showUssdConfirmDialog = true

                    // Mark payment session as SUCCESS in database & session state immediately so grace period / timeout won't open UserAttestationActivity
                    sessionManager?.onUssdAccessibilityResult(
                        amount = result.amount,
                        reference = result.reference,
                        recipientName = result.recipientName
                    )
                }
            }
        }

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
                        onNavigateToResult = {
                            val currentState = sessionManager?.paymentState?.value
                            if (currentState is PaymentState.Success) {
                                navigateToResult(
                                    phoneNumber = currentState.phoneNumber.ifBlank { phoneNumber },
                                    amount = amount,
                                    confirmedAmount = currentState.amount.ifBlank { amount },
                                    recipientName = currentState.recipientName,
                                    reference = currentState.bankReference ?: currentState.transactionId
                                )
                            } else if (currentState is PaymentState.NeedsReview) {
                                navigateToResult(
                                    phoneNumber = currentState.phoneNumber.ifBlank { phoneNumber },
                                    amount = amount,
                                    confirmedAmount = currentState.amount.ifBlank { amount },
                                    recipientName = null,
                                    reference = currentState.transactionId
                                )
                            } else {
                                finish()
                            }
                        },
                        showUssdConfirmDialog = { showUssdConfirmDialog },
                        ussdAccessibilityResult = { ussdAccessibilityResult },
                        ussdAccessibilityDetected = { ussdAccessibilityDetected },
                        showAccessibilityPrompt = { showAccessibilityPrompt },
                        onOpenAccessibilitySettings = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(intent)
                        },
                        onUssdConfirmed = {
                            val detectedAmount = ussdAccessibilityResult?.amount
                            val detectedRef = ussdAccessibilityResult?.reference
                            val detectedRecipient = ussdAccessibilityResult?.recipientName
                            sessionManager?.onUserConfirmedSuccess(detectedAmount, detectedRef, detectedRecipient)
                            showUssdConfirmDialog = false
                            ussdAccessibilityDetected = false
                            ussdAccessibilityResult = null
                            navigateToResult(phoneNumber, amount, detectedAmount ?: amount, detectedRecipient, detectedRef)
                        },
                        onUssdCancelled = {
                            // Per product requirements: if accessibility detected SUCCESS,
                            // clicking Cancel still treats the payment as successful.
                            if (ussdAccessibilityDetected) {
                                val detectedAmount = ussdAccessibilityResult?.amount
                                val detectedRef = ussdAccessibilityResult?.reference
                                val detectedRecipient = ussdAccessibilityResult?.recipientName
                                sessionManager?.onUserConfirmedSuccess(detectedAmount, detectedRef, detectedRecipient)
                                showUssdConfirmDialog = false
                                ussdAccessibilityDetected = false
                                ussdAccessibilityResult = null
                                navigateToResult(phoneNumber, amount, detectedAmount ?: amount, detectedRecipient, detectedRef)
                            } else {
                                sessionManager?.onUserCancelled()
                                showUssdConfirmDialog = false
                                ussdAccessibilityDetected = false
                                ussdAccessibilityResult = null
                                finish()
                            }
                        },
                        onUssdDismiss = {
                            showUssdConfirmDialog = false
                            ussdAccessibilityDetected = false
                            ussdAccessibilityResult = null
                            finish()
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Always maintain ussdPaymentActive = true while in PaymentProgressActivity
        UssdAccessibilityService.ussdPaymentActive = true
    }

    override fun onDestroy() {
        super.onDestroy()
        // Deactivate payment detection mode when activity ends
        UssdAccessibilityService.ussdPaymentActive = false
        UssdAccessibilityService.ussdCallback = null
        Log.d(TAG, "USSD payment detection deactivated on activity destroy")
    }

    private fun navigateToResult(
        phoneNumber: String,
        amount: String,
        confirmedAmount: String,
        recipientName: String?,
        reference: String?
    ) {
        val intent = Intent(this, PaymentResultActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("amount", confirmedAmount)
            putExtra("phone_number", phoneNumber)
            putExtra("recipient_name", recipientName ?: "")
            putExtra("transaction_id", reference ?: "")
            putExtra("status", "SUCCESS")
        }
        startActivity(intent)
        finish()
    }
}

private enum class ProgressPhase {
    CONNECTING,
    CALL_ACTIVE,
    WAITING_FOR_SMS,
    ENDING
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentProgressScreen(
    phoneNumber: String,
    amount: String,
    sessionManager: PaymentSessionManager?,
    coordinator: CallStateCoordinator?,
    onFinish: () -> Unit,
    onNavigateToResult: () -> Unit,
    showUssdConfirmDialog: () -> Boolean,
    ussdAccessibilityResult: () -> UssdAccessibilityResult?,
    ussdAccessibilityDetected: () -> Boolean,
    showAccessibilityPrompt: () -> Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onUssdConfirmed: () -> Unit,
    onUssdCancelled: () -> Unit,
    onUssdDismiss: () -> Unit
) {
    var phase by remember { mutableStateOf(ProgressPhase.CONNECTING) }
    var statusLine by remember { mutableStateOf("Connecting to UPI service…") }
    var outgoingCallSeen by remember { mutableStateOf(false) }

    // Watch for accessibility-detected payment result
    var showUssdDialog by remember { mutableStateOf(showUssdConfirmDialog()) }
    var ussdResult by remember { mutableStateOf(ussdAccessibilityResult()) }

    LaunchedEffect(ussdAccessibilityDetected()) {
        if (ussdAccessibilityDetected()) {
            showUssdDialog = true
            ussdResult = ussdAccessibilityResult()
        }
    }

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
                is PaymentState.Success -> {
                    onNavigateToResult()
                }
                is PaymentState.NeedsReview -> {
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

                // Show USSD accessibility confirmation dialog when accessibility detected a successful payment
                if (showUssdDialog) {
                    val result = ussdResult
                    if (result != null) {
                        UssdAccessibilityConfirmDialog(
                            isVisible = true,
                            result = result,
                            onConfirmed = onUssdConfirmed,
                            onCancelled = onUssdCancelled,
                            onDismiss = onUssdDismiss
                        )
                    }
                }

                // Show accessibility enablement prompt when service is not enabled
                if (showAccessibilityPrompt()) {
                    AlertDialog(
                        onDismissRequest = { showAccessibilityPrompt() }
                    ) {
                        Text(
                            text = stringResource(R.string.ussd_accessibility_not_enabled),
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
