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
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proximity.app.ProximityApplication
import com.proximity.app.R
import com.proximity.app.ui.components.*
import com.proximity.app.ui.theme.*
import com.proximity.app.utils.CurrencyFormat
import kotlinx.coroutines.delay

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

    val isSuccess = status.equals("SUCCESS", ignoreCase = true) || status.equals("NEEDS_REVIEW", ignoreCase = true) || status.isBlank()

    var step1Visible by remember { mutableStateOf(false) }
    var step2Visible by remember { mutableStateOf(false) }
    var step3Visible by remember { mutableStateOf(false) }
    var step4Visible by remember { mutableStateOf(false) }
    var startCount by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        step1Visible = true
        delay(250)
        step2Visible = true
        delay(250)
        startCount = true
        step3Visible = true
        delay(250)
        step4Visible = true
    }

    val animatedAmount by animateFloatAsState(
        targetValue = if (startCount) amount.toFloat() else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "amountAnimation"
    )

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
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(0.8f))

                // Step 1: Solid Circle White Checkmark + Colour Bomb Explosion
                AnimatedVisibility(
                    visible = step1Visible,
                    enter = scaleIn(initialScale = 0.4f) + fadeIn()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isSuccess) {
                            SmoothCheckmarkAnimation(
                                circleColor = accent.primary,
                                checkmarkColor = Color.White
                            )
                        } else {
                            SmoothErrorAnimation()
                        }
                    }
                }

                AnimatedVisibility(
                    visible = step2Visible,
                    enter = fadeIn(animationSpec = tween(400))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (isSuccess) "Payment Successful" else "Payment Failed",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        if (!isSuccess && errorMessage.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage,
                                fontSize = 14.sp,
                                color = ProximityStatusError,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = step3Visible,
                    enter = scaleIn(initialScale = 0.8f) + fadeIn(animationSpec = tween(400))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(modifier = Modifier.height(16.dp))

                        CurrencyText(
                            amount = animatedAmount.toDouble(),
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSuccess) accent.primary else ProximityStatusError
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                AnimatedVisibility(
                    visible = step4Visible,
                    enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(animationSpec = tween(400))
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        shadowElevation = 0.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Paid To (Number / Recipient)
                            if (phoneNumber.isNotEmpty() || recipientName.isNotEmpty()) {
                                DetailReceiptRow(
                                    label = "Paid To",
                                    value = if (recipientName.isNotBlank()) {
                                        "$recipientName (+91 ${formatPhoneDisplay(phoneNumber)})"
                                    } else {
                                        "+91 ${formatPhoneDisplay(phoneNumber)}"
                                    }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                            }

                            DetailReceiptRow(
                                label = "Amount Paid",
                                valueWidget = {
                                    CurrencyText(
                                        amount = amount,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            )

                            if (transactionId.isNotEmpty()) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Transaction ID",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = transactionId,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Transaction ID", transactionId))
                                            Toast.makeText(context, "Transaction ID copied", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy Transaction ID",
                                            tint = accent.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1.2f))

                Button(
                    onClick = onDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .pressScale(onClick = onDone),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent.primary)
                ) {
                    Text(
                        text = "Done",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun DetailReceiptRow(
    label: String,
    value: String? = null,
    valueWidget: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (valueWidget != null) {
            valueWidget()
        } else if (value != null) {
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatPhoneDisplay(number: String): String {
    val digits = number.filter { it.isDigit() }
    if (digits.length <= 3) return digits
    if (digits.length <= 6) return "${digits.substring(0, 3)} ${digits.substring(3)}"
    return "${digits.substring(0, 3)} ${digits.substring(3, 6)} ${digits.substring(6)}"
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
            },
            onDone = {}
        )
    }
}
