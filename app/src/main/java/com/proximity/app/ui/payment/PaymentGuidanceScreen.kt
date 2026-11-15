// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.ui.payment

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.proximity.app.AppCore
import com.proximity.app.R
import com.proximity.app.data.OperatorCatalog
import com.proximity.app.data.PaymentChannel
import com.proximity.app.payment.Upi123InputValidator
import com.proximity.app.telephony.CallAccountRegistrar
import com.proximity.app.telephony.CallDialOutcome
import com.proximity.app.theme.AppTheme
import com.proximity.app.theme.ColorTokens
import com.proximity.app.theme.LocalAppVisuals
import com.proximity.app.ui.components.AnimatedBackground
import com.proximity.app.ui.components.BrandedHeaderLockup
import com.proximity.app.ui.components.CurrencyDisplay
import com.proximity.app.ui.components.GlassCard
import com.proximity.app.ui.components.pressScale
import com.proximity.app.utils.CurrencyFormat
import com.proximity.app.utils.PermissionChecker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class PaymentGuidanceScreen : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val app = application as AppCore

        val recipientName = intent.getStringExtra(EXTRA_RECIPIENT_NAME)
        val phone10 = intent.getStringExtra(EXTRA_PHONE_10).orEmpty()
        val amountRupees = intent.getStringExtra(EXTRA_AMOUNT_RUPEES).orEmpty()

        setContent {
            val settings by app.services.preferencesStore.settingsFlow.collectAsState()
            val scope = rememberCoroutineScope()
            var isLaunchingCall by remember { mutableStateOf(false) }

            fun startUpi123PaymentCall() {
                if (isLaunchingCall) return
                checkVoiceSimMismatchWarning(this@PaymentGuidanceScreen, settings.primarySimCarrierId)

                val validation = Upi123InputValidator.validate(phone10, amountRupees, isJio = settings.isJioPrimarySim)
                if (validation !is Upi123InputValidator.ValidationResult.Valid) {
                    val message = when ((validation as Upi123InputValidator.ValidationResult.Invalid).reason) {
                        Upi123InputValidator.ValidationReason.INVALID_PHONE ->
                            getString(R.string.error_invalid_phone_number)
                        Upi123InputValidator.ValidationReason.DECIMALS_NOT_ALLOWED ->
                            getString(R.string.error_amount_whole_rupees_only)
                        Upi123InputValidator.ValidationReason.BELOW_MINIMUM ->
                            getString(R.string.error_amount_below_minimum, Upi123InputValidator.MIN_AMOUNT.toString())
                        Upi123InputValidator.ValidationReason.ABOVE_MAX_CAP ->
                            getString(R.string.error_amount_above_123pay_cap, Upi123InputValidator.MAX_AMOUNT.toString())
                        Upi123InputValidator.ValidationReason.EMPTY_OR_NON_NUMERIC_AMOUNT ->
                            getString(R.string.error_dial_failed)
                    }
                    Toast.makeText(this@PaymentGuidanceScreen, message, Toast.LENGTH_SHORT).show()
                    return
                }

                isLaunchingCall = true
                scope.launch {
                    try {
                        app.services.paymentLifecycleManager.startNewSession(
                            channel = PaymentChannel.UPI_123PAY,
                            recipientName = recipientName,
                            phoneNumber = validation.cleanPhone,
                            vpa = null,
                            amountRupees = validation.wholeRupees.toString(),
                            configuredBankName = settings.selectedBankDisplayName
                        )

                        val progressIntent = Intent(
                            this@PaymentGuidanceScreen,
                            PaymentProgressScreen::class.java
                        ).apply {
                            putExtra(PaymentProgressScreen.EXTRA_PHONE_10, validation.cleanPhone)
                            putExtra(
                                PaymentProgressScreen.EXTRA_AMOUNT_RUPEES,
                                validation.wholeRupees.toString()
                            )
                        }

                        // Place the call through TelecomManager BEFORE transitioning activities
                        // or finishing this screen so the call request executes while foregrounded.
                        when (CallAccountRegistrar.initiateUpi123Call(
                            this@PaymentGuidanceScreen,
                            validation.dtmfUriString
                        )) {
                            CallDialOutcome.TELECOM_SERVICE -> {
                                startActivity(progressIntent)
                                finish()
                            }
                            CallDialOutcome.CALL_INTENT_FALLBACK -> {
                                startActivity(progressIntent)
                                finish()
                            }
                            CallDialOutcome.FAILED -> {
                                try {
                                    startActivity(progressIntent)
                                    startActivity(
                                        Intent(
                                            Intent.ACTION_DIAL,
                                            Uri.parse(validation.dtmfUriString)
                                        ).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                    finish()
                                } catch (_: Exception) {
                                    app.services.paymentLifecycleManager.cancelCurrentPayment("Dial failed")
                                    Toast.makeText(
                                        this@PaymentGuidanceScreen,
                                        R.string.error_dial_failed,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    isLaunchingCall = false
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        app.services.paymentLifecycleManager.cancelCurrentPayment("Dial failed")
                        Toast.makeText(
                            this@PaymentGuidanceScreen,
                            R.string.error_dial_failed,
                            Toast.LENGTH_SHORT
                        ).show()
                        isLaunchingCall = false
                    }
                }
            }

            val callPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) {
                    startUpi123PaymentCall()
                } else {
                    Toast.makeText(
                        this@PaymentGuidanceScreen,
                        R.string.guidance_call_permission_required,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            AppTheme(settings = settings) {
                PaymentGuidanceView(
                    recipientName = recipientName,
                    phone10 = phone10,
                    amountRupees = amountRupees,
                    configuredBankName = settings.selectedBankDisplayName,
                    isProceedEnabled = !isLaunchingCall,
                    onBack = { finish() },
                    onProceed = {
                        if (isLaunchingCall) return@PaymentGuidanceView
                        if (PermissionChecker.isGranted(this@PaymentGuidanceScreen, Manifest.permission.CALL_PHONE)) {
                            startUpi123PaymentCall()
                        } else {
                            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                        }
                    }
                )
            }
        }
    }

    private fun checkVoiceSimMismatchWarning(context: Context, configuredSimId: String) {
        if (configuredSimId.isBlank()) return
        try {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager ?: return
            val defaultVoiceSubId = SubscriptionManager.getDefaultVoiceSubscriptionId()
            if (defaultVoiceSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                @Suppress("MissingPermission")
                val info = sm.getActiveSubscriptionInfo(defaultVoiceSubId)
                val activeCarrierName = info?.carrierName?.toString().orEmpty()
                val expectedCarrier = OperatorCatalog.findById(configuredSimId)
                if (activeCarrierName.isNotBlank() && expectedCarrier != null &&
                    !activeCarrierName.contains(expectedCarrier.id, ignoreCase = true) &&
                    !activeCarrierName.contains(expectedCarrier.title.split(" ").last(), ignoreCase = true)
                ) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.warn_voice_sim_mismatch, activeCarrierName, expectedCarrier.title),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        const val EXTRA_RECIPIENT_NAME = "extra_recipient_name"
        const val EXTRA_PHONE_10 = "extra_phone_10"
        const val EXTRA_AMOUNT_RUPEES = "extra_amount_rupees"
    }
}

@Composable
fun PaymentGuidanceView(
    recipientName: String?,
    phone10: String,
    amountRupees: String,
    configuredBankName: String,
    onBack: () -> Unit,
    onProceed: () -> Unit,
    isProceedEnabled: Boolean = true
) {
    val visuals = LocalAppVisuals.current

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_back_arrow),
                        contentDescription = stringResource(id = R.string.cd_back),
                        tint = visuals.textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                BrandedHeaderLockup(compact = true)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(id = R.string.guidance_headline),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Serif,
                color = visuals.textPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = stringResource(id = R.string.guidance_transfer_recipient),
                            fontSize = 12.sp,
                            color = visuals.textSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (!recipientName.isNullOrBlank()) {
                            Text(
                                text = recipientName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Serif,
                                color = visuals.textPrimary
                            )
                        }
                        Text(
                            text = CurrencyFormat.formatPhoneForDisplay(phone10),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = visuals.textSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        CurrencyDisplay(
                            amountStr = amountRupees,
                            digitFontSize = 36.sp,
                            color = ColorTokens.BrandTeal
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_wallet),
                                contentDescription = null,
                                tint = ColorTokens.BrandTeal,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = configuredBankName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = visuals.textPrimary
                            )
                        }
                    }
                }

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(id = R.string.guidance_card1_title),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = visuals.textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(id = R.string.guidance_card1_body),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = visuals.textSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(ColorTokens.BrandTeal.copy(alpha = 0.16f))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_phone_call),
                                contentDescription = null,
                                tint = ColorTokens.BrandTeal,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(id = R.string.guidance_card1_callout),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ColorTokens.BrandTeal
                            )
                        }
                    }
                }

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(id = R.string.guidance_card2_title),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = visuals.textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(id = R.string.guidance_card2_body),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = visuals.textSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(id = R.string.guidance_dialpad_sequence),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = visuals.textSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ColorTokens.BrandTeal),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(id = R.string.guidance_key_1),
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            }
                            Icon(
                                painter = painterResource(id = R.drawable.ic_forward_arrow),
                                contentDescription = null,
                                tint = ColorTokens.BrandTeal,
                                modifier = Modifier.size(16.dp)
                            )
                            repeat(6) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(ColorTokens.BrandTeal)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onProceed,
                enabled = isProceedEnabled,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = ColorTokens.BrandTeal),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .pressScale(enabled = isProceedEnabled)
            ) {
                Text(
                    text = stringResource(id = R.string.action_proceed),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
