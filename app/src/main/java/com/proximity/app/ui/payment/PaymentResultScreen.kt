// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.ui.payment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.proximity.app.ui.shell.HomeShellActivity
import com.proximity.app.AppCore
import com.proximity.app.R
import com.proximity.app.accessibility.UssdDialogReaderService
import com.proximity.app.data.PaymentRecord
import com.proximity.app.data.PaymentRecordStatus
import com.proximity.app.theme.AppTheme
import com.proximity.app.theme.ColorTokens
import com.proximity.app.theme.LocalAppVisuals
import com.proximity.app.ui.components.AnimatedBackground
import com.proximity.app.ui.components.CheckmarkConfettiBurst
import com.proximity.app.ui.components.CurrencyDisplay
import com.proximity.app.ui.components.ErrorCrossGraphic
import com.proximity.app.ui.components.GlassCard
import com.proximity.app.ui.components.PendingClockGraphic
import com.proximity.app.ui.components.pressScale
import com.proximity.app.utils.CurrencyFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

class PaymentResultScreen : ComponentActivity() {

    private var activeRecord by mutableStateOf<PaymentRecord?>(null)

    private fun extractRecord(srcIntent: Intent?): PaymentRecord? {
        val target = srcIntent ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            target.getParcelableExtra(EXTRA_PAYMENT_RECORD, PaymentRecord::class.java)
        } else {
            @Suppress("DEPRECATION")
            target.getParcelableExtra(EXTRA_PAYMENT_RECORD)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val app = application as AppCore
        activeRecord = extractRecord(intent)

        setContent {
            val settings by app.services.preferencesStore.settingsFlow.collectAsState()
            val record = activeRecord
            val context = LocalContext.current
            AppTheme(settings = settings) {
                if (record != null) {
                    PaymentResultContent(
                        record = record,
                        onDone = {
                            UssdDialogReaderService.disableService()
                            app.services.paymentLifecycleManager.resetTerminalState()
                            val intent = Intent(context, HomeShellActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                            finish()
                        }
                    )
                } else {
                    LaunchedEffect(Unit) { finish() }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractRecord(intent)?.let { updated ->
            activeRecord = updated
        }
    }

    companion object {
        const val EXTRA_PAYMENT_RECORD = "extra_payment_record"
    }
}

@Composable
fun PaymentResultContent(
    record: PaymentRecord,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val visuals = LocalAppVisuals.current
    val reducedMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }

    var showStep1Icon by remember(record.id) { mutableStateOf(reducedMotion) }
    var showStep2Title by remember(record.id) { mutableStateOf(reducedMotion) }
    var showStep3Amount by remember(record.id) { mutableStateOf(reducedMotion) }
    var showStep4Receipt by remember(record.id) { mutableStateOf(reducedMotion) }

    val targetAmount = remember(record.amountRupees) {
        record.amountRupees.replace(",", "").replace("\u20B9", "").toBigDecimalOrNull() ?: BigDecimal.ZERO
    }
    val targetScale = remember(targetAmount) { targetAmount.scale().coerceAtLeast(2) }
    val countProgressAnim = remember(record.id) { Animatable(if (reducedMotion) 1f else 0f) }

    LaunchedEffect(record.id, reducedMotion) {
        if (reducedMotion) {
            showStep1Icon = true
            showStep2Title = true
            showStep3Amount = true
            showStep4Receipt = true
            countProgressAnim.snapTo(1f)
        } else {
            countProgressAnim.snapTo(0f)
            delay(100L)
            showStep1Icon = true
            delay(400L)
            showStep2Title = true
            delay(200L)
            showStep3Amount = true
            launch {
                countProgressAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
                )
            }
            delay(200L)
            showStep4Receipt = true
        }
    }

    BackHandler { onDone() }

    val isSuccessLike = record.status == PaymentRecordStatus.SUCCESS ||
        record.status == PaymentRecordStatus.USER_CONFIRMED ||
        record.status == PaymentRecordStatus.NEEDS_REVIEW
    val isPendingLike = record.status == PaymentRecordStatus.PENDING ||
        record.status == PaymentRecordStatus.UNVERIFIED

    val amountColor = when {
        isSuccessLike -> visuals.adaptiveTealLabel
        isPendingLike -> visuals.statusPendingText
        else -> visuals.statusErrorText
    }

    val (titleRes, subtitleRes) = when {
        isSuccessLike -> R.string.result_title_success to R.string.result_subtitle_success
        isPendingLike -> R.string.result_title_pending to R.string.result_subtitle_pending
        else -> R.string.result_title_failed to R.string.result_subtitle_failed
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier.height(170.dp),
                contentAlignment = Alignment.Center
            ) {
                if (showStep1Icon) {
                    when {
                        isSuccessLike -> CheckmarkConfettiBurst(sizeDp = 170.dp)
                        isPendingLike -> PendingClockGraphic(sizeDp = 100.dp)
                        else -> ErrorCrossGraphic(sizeDp = 100.dp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            AnimatedVisibility(
                visible = showStep2Title,
                enter = slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    initialOffsetY = { it / 2 }
                ) + fadeIn()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(id = titleRes),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Serif,
                        color = visuals.textPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(id = subtitleRes),
                        fontSize = 14.sp,
                        color = visuals.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            AnimatedVisibility(visible = showStep3Amount, enter = fadeIn()) {
                val animatedAmount = if (countProgressAnim.value >= 0.999f) {
                    targetAmount
                } else {
                    targetAmount
                        .multiply(BigDecimal.valueOf(countProgressAnim.value.toDouble()))
                        .setScale(targetScale, RoundingMode.HALF_UP)
                }
                CurrencyDisplay(
                    amount = animatedAmount,
                    digitFontSize = 40.sp,
                    color = amountColor
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            AnimatedVisibility(
                visible = showStep4Receipt,
                enter = slideInVertically(initialOffsetY = { it / 3 }) + fadeIn()
            ) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!record.recipientName.isNullOrBlank()) {
                            ReceiptLine(
                                label = stringResource(id = R.string.result_label_paid_to),
                                value = record.recipientName
                            )
                        }
                        if (!record.vpa.isNullOrBlank()) {
                            ReceiptLine(
                                label = stringResource(id = R.string.result_label_upi_id),
                                value = record.vpa
                            )
                        } else if (!record.phoneNumber.isNullOrBlank()) {
                            ReceiptLine(
                                label = stringResource(id = R.string.result_label_phone_number),
                                value = CurrencyFormat.formatPhoneForDisplay(record.phoneNumber)
                            )
                        }
                        ReceiptLine(
                            label = stringResource(id = R.string.result_label_amount_paid),
                            value = CurrencyFormat.formatWithSymbol(record.amountRupees)
                        )
                        val localizedStatus = when (record.status) {
                            PaymentRecordStatus.SUCCESS -> stringResource(id = R.string.status_label_success)
                            PaymentRecordStatus.USER_CONFIRMED -> stringResource(id = R.string.status_label_user_confirmed)
                            PaymentRecordStatus.PENDING -> stringResource(id = R.string.status_label_pending)
                            PaymentRecordStatus.UNVERIFIED -> stringResource(id = R.string.status_label_unverified)
                            PaymentRecordStatus.NEEDS_REVIEW -> stringResource(id = R.string.status_label_needs_review)
                            PaymentRecordStatus.CANCELLED -> stringResource(id = R.string.status_label_cancelled)
                            PaymentRecordStatus.FAILED -> stringResource(id = R.string.status_label_failed)
                        }
                        ReceiptLine(
                            label = stringResource(id = R.string.result_label_status),
                            value = if (isPendingLike) {
                                stringResource(id = R.string.result_status_pending_confirmation)
                            } else {
                                localizedStatus
                            }
                        )
                        HorizontalDivider(color = visuals.outline.copy(alpha = 0.25f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(id = R.string.result_label_transaction_id),
                                    fontSize = 12.sp,
                                    color = visuals.textSecondary
                                )
                                Text(
                                    text = record.bankReferenceId ?: record.id,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = visuals.textPrimary
                                )
                            }
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(
                                        ClipData.newPlainText("Transaction ID", record.bankReferenceId ?: record.id)
                                    )
                                    Toast.makeText(context, R.string.toast_txn_id_copied, Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_copy),
                                    contentDescription = stringResource(id = R.string.cd_copy),
                                    tint = ColorTokens.BrandTeal,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onDone,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = ColorTokens.BrandTeal),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .pressScale()
            ) {
                Text(
                    text = stringResource(id = R.string.action_done),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun ReceiptLine(label: String, value: String) {
    val visuals = LocalAppVisuals.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = visuals.textSecondary
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = visuals.textPrimary
        )
    }
}
