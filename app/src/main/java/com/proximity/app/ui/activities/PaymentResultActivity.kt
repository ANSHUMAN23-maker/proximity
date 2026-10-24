// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.ui.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proximity.app.ProximityApplication
import com.proximity.app.constants.AppConstants
import com.proximity.app.ui.components.*
import com.proximity.app.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PaymentResultActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as? ProximityApplication
        val settingsRepository = app?.settingsRepository ?: com.proximity.app.data.SettingsRepository(applicationContext)

        setContent {
            val savedSettings by settingsRepository.settingsFlow.collectAsState()
            CompositionLocalProvider(LocalProximityAccentTheme provides TealAccentTheme) {
                ProximityTheme(darkTheme = savedSettings.darkModeEnabled) {
                    PaymentResultScreen(
                        intent = intent,
                        onDone = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
fun PaymentResultScreen(intent: Intent, onDone: () -> Unit) {
    BackHandler(onBack = onDone)

    val context = LocalContext.current
    val accent = LocalProximityAccentTheme.current

    val amountStr = intent.getStringExtra("amount") ?: "0"
    val amount = amountStr.toDoubleOrNull() ?: 0.0
    val phoneNumber = intent.getStringExtra("phone_number") ?: ""
    val recipientName = intent.getStringExtra("recipient_name") ?: ""
    val status = intent.getStringExtra("status") ?: "SUCCESS"
    val transactionId = intent.getStringExtra("transaction_id") ?: ""
    val errorMessage = intent.getStringExtra("error_message") ?: ""

    // Resolve Bank
    val sharedPreferences = remember {
        context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
    }
    val intentBankId = intent.getStringExtra("bank_id")
    val intentBankName = intent.getStringExtra("bank_name")

    val bankId = remember(intentBankId) {
        if (!intentBankId.isNullOrBlank()) {
            intentBankId
        } else {
            sharedPreferences.getString(AppConstants.KEY_SELECTED_BANK, "hdfc") ?: "hdfc"
        }
    }

    val bankName = remember(intentBankName, bankId) {
        if (!intentBankName.isNullOrBlank()) {
            intentBankName
        } else {
            getBankDisplayName(bankId)
        }
    }

    val timestampFormatted = remember {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        sdf.format(Date())
    }

    val isSuccess = status.equals("SUCCESS", ignoreCase = true) ||
            status.equals("NEEDS_REVIEW", ignoreCase = true) ||
            status.isBlank()

    var voucherVisible by remember { mutableStateOf(false) }
    var actionsVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(120)
        voucherVisible = true
        delay(250)
        actionsVisible = true
    }

    val onCopyTransactionId = {
        if (transactionId.isNotBlank()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Transaction ID", transactionId))
            Toast.makeText(context, "Transaction ID copied", Toast.LENGTH_SHORT).show()
        }
    }

    val onShareReceipt = {
        val shareText = buildString {
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("      PROXIMITY RECEIPT      ")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("Status: ${if (isSuccess) "Payment Successful" else "Payment Failed"}")
            appendLine("Amount: ₹${String.format(Locale.getDefault(), "%.2f", amount)}")
            if (recipientName.isNotBlank()) {
                appendLine("Paid To: $recipientName (+91 $phoneNumber)")
            } else if (phoneNumber.isNotBlank()) {
                appendLine("Paid To: +91 $phoneNumber")
            }
            appendLine("Bank: $bankName")
            if (transactionId.isNotBlank()) {
                appendLine("Transaction ID: $transactionId")
            }
            appendLine("Date: $timestampFormatted")
            appendLine("Payment Rail: Offline UPI 123")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        }
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share Payment Receipt")
        context.startActivity(shareIntent)
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
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // Perforated Voucher Entrance (NO celebratory confetti explosion)
                AnimatedVisibility(
                    visible = voucherVisible,
                    enter = slideInVertically(
                        initialOffsetY = { it / 3 },
                        animationSpec = tween(durationMillis = 450)
                    ) + fadeIn(animationSpec = tween(durationMillis = 400))
                ) {
                    PerforatedVoucherCard(
                        amount = amount,
                        recipientName = recipientName.takeIf { it.isNotBlank() },
                        phoneNumber = phoneNumber,
                        bankName = bankName,
                        bankId = bankId,
                        transactionId = transactionId,
                        timestampFormatted = timestampFormatted,
                        isSuccess = isSuccess,
                        errorMessage = errorMessage.takeIf { !isSuccess && it.isNotBlank() },
                        onCopyTransactionId = onCopyTransactionId
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Bottom Actions: Share Receipt + Done
                AnimatedVisibility(
                    visible = actionsVisible,
                    enter = fadeIn(animationSpec = tween(350))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onShareReceipt,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .pressScale(),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.5.dp, accent.primary.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = accent.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = accent.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Share",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = accent.primary
                            )
                        }

                        Button(
                            onClick = onDone,
                            modifier = Modifier
                                .weight(1.3f)
                                .height(52.dp)
                                .pressScale(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accent.primary)
                        ) {
                            Text(
                                text = "Done",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getBankDisplayName(bankId: String): String = when (bankId.lowercase(Locale.ROOT)) {
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
    else -> bankId.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() } + " Bank"
}

@Preview(name = "Payment Success - Light", showBackground = true)
@Preview(name = "Payment Success - Dark", showBackground = true, backgroundColor = 0xFF090D14)
@Composable
private fun PaymentResultScreenPreview() {
    ProximityTheme {
        PaymentResultScreen(
            intent = Intent().apply {
                putExtra("amount", "25.00")
                putExtra("phone_number", "9876543210")
                putExtra("recipient_name", "Ramesh Kumar")
                putExtra("transaction_id", "5e6b0f2d-2e60-45cf-905d-c228daec4648")
                putExtra("status", "SUCCESS")
                putExtra("bank_id", "hdfc")
                putExtra("bank_name", "HDFC Bank")
            },
            onDone = {}
        )
    }
}
