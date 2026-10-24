package com.proximity.app.ui.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.RingVolume
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proximity.app.ProximityApplication
import com.proximity.app.helpers.TransactionDetector
import com.proximity.app.managers.CallManager
import com.proximity.app.ui.components.*
import com.proximity.app.ui.theme.LocalProximityAccentTheme
import com.proximity.app.ui.theme.ProximityTheme

class PaymentGuidanceActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PHONE_NUMBER = "phone_number"
        const val EXTRA_AMOUNT = "amount"
        private const val TAG = "PaymentGuidanceActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: ""
        val amount = intent.getStringExtra(EXTRA_AMOUNT) ?: ""

        setContent {
            val app = application as? ProximityApplication
            val settingsRepository = app?.settingsRepository ?: com.proximity.app.data.SettingsRepository(applicationContext)
            val savedSettings by settingsRepository.settingsFlow.collectAsState()

            val bankId = savedSettings.bankId.takeIf { it.isNotBlank() } ?: "hdfc"
            val bankName = when (bankId.lowercase()) {
                "sbi" -> "State Bank of India"
                "hdfc" -> "HDFC Bank"
                "icici" -> "ICICI Bank"
                "axis" -> "Axis Bank"
                "kotak" -> "Kotak Mahindra Bank"
                "pnb" -> "Punjab National Bank"
                "bob" -> "Bank of Baroda"
                "yes" -> "Yes Bank"
                "idbi" -> "IDBI Bank"
                "canara" -> "Canara Bank"
                "union" -> "Union Bank of India"
                else -> bankId.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } + " Bank"
            }

            CompositionLocalProvider(com.proximity.app.ui.theme.LocalProximityAccentTheme provides com.proximity.app.ui.theme.TealAccentTheme) {
                ProximityTheme(darkTheme = savedSettings.darkModeEnabled) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        PaymentGuidanceScreen(
                            phoneNumber = phoneNumber,
                            amount = amount,
                            bankId = bankId,
                            bankName = bankName,
                            onBack = { finish() },
                            onProceed = {
                                initiatePayment(phoneNumber, amount)
                            }
                        )
                    }
                }
            }
        }
    }

    private fun initiatePayment(phoneNumber: String, amount: String) {
        val sessionManager = ProximityApplication.from(this)?.paymentSessionManager
        if (sessionManager == null) {
            Log.e(TAG, "PaymentSessionManager unavailable")
            finish()
            return
        }

        val transactionId = sessionManager.begin(phoneNumber, amount)

        TransactionDetector.getInstance(this).startOperation(
            operationType = "UPI_123",
            expectedAmount = amount,
            phoneNumber = phoneNumber,
            sessionTxnId = transactionId
        )

        val callManager = CallManager(this)
        val success = callManager.initiateUPI123Call(phoneNumber, amount)

        if (success) {
            val progressIntent = Intent(this, com.proximity.app.ui.activities.PaymentProgressActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(com.proximity.app.ui.activities.PaymentProgressActivity.EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra(com.proximity.app.ui.activities.PaymentProgressActivity.EXTRA_AMOUNT, amount)
            }
            // Delay slightly to allow the telecom call to bind
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                startActivity(progressIntent)
                finish()
            }, 1000)
        } else {
            Log.e(TAG, "Failed to initiate UPI123 call")
            sessionManager.onDialFailed("Could not start the payment call")
            TransactionDetector.getInstance(this).stopOperation()
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentGuidanceScreen(
    phoneNumber: String,
    amount: String,
    bankId: String = "hdfc",
    bankName: String = "HDFC Bank",
    onBack: () -> Unit,
    onProceed: () -> Unit
) {
    val accent = LocalProximityAccentTheme.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    ProximityBrandedHeader(
                        fontSize = 20.sp,
                        logoSize = 28.dp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(24.dp)
            ) {
                Button(
                    onClick = onProceed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .pressScale(),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = accent.primary)
                ) {
                    Text(
                        text = "Proceed",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Proceed",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedContourBackground(modifier = Modifier.fillMaxSize())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Authorize via Call",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = (-0.3).sp
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Payment Details Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Transfer recipient",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (phoneNumber.isBlank()) "-" else formatPhoneDisplay(phoneNumber),
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        CurrencyText(
                            amount = if (amount.isBlank()) "25" else amount,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = accent.primary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            BankLogoBadge(bankKey = bankId, size = 24.dp)
                            Text(
                                text = bankName,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Unified vertical stepper with numbered pulse rings
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Step 1: Receive Call
                    PaymentGuidanceStep(
                        stepNumber = 1,
                        stepTitle = "Receive Automated Bank Call",
                        stepDescription = "Your phone will receive an automated verification call from your bank's telecom switch.",
                        icon = Icons.Default.RingVolume,
                        iconColor = accent.primary,
                        isActive = true
                    )

                    // Step divider
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(56.dp)
                            .align(Alignment.CenterHorizontally)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    )

                    // Step 2: Enter UPI PIN
                    PaymentGuidanceStep(
                        stepNumber = 2,
                        stepTitle = "Answer Call & Enter UPI PIN",
                        stepDescription = "Open your keypad dialer, press 1 to confirm payment, then enter your 4 or 6 digit bank UPI PIN.",
                        icon = Icons.Default.Dialpad,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        isActive = false
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

private fun formatPhoneDisplay(number: String): String {
    val digits = number.filter { it.isDigit() }
    if (digits.length <= 3) return digits
    if (digits.length <= 6) return "${digits.substring(0, 3)} ${digits.substring(3)}"
    return "${digits.substring(0, 3)} ${digits.substring(3, 6)} ${digits.substring(6)}"
}

@Composable
fun PaymentGuidanceStep(
    stepNumber: Int,
    stepTitle: String,
    stepDescription: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    isActive: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Step number circle
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) iconColor.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber.toString(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) iconColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stepTitle,
                style = MaterialTheme.typography.labelLarge.copy(
                    color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Text(
                text = stepDescription,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                lineHeight = 18.sp
            )
        }

        if (isActive) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Preview(name = "Payment Guidance - Light", showBackground = true)
@Preview(name = "Payment Guidance - Dark", showBackground = true, backgroundColor = 0xFF090D14)
@Composable
private fun PaymentGuidanceScreenPreview() {
    ProximityTheme {
        PaymentGuidanceScreen(
            phoneNumber = "9876543210",
            amount = "25",
            bankId = "hdfc",
            bankName = "HDFC Bank",
            onBack = {},
            onProceed = {}
        )
    }
}
