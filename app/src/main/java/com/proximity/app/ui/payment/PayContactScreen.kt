// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.ui.payment

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.proximity.app.AppCore
import com.proximity.app.R
import com.proximity.app.payment.Upi123InputValidator
import com.proximity.app.theme.AppTheme
import com.proximity.app.theme.ColorTokens
import com.proximity.app.theme.LocalAppVisuals
import com.proximity.app.ui.components.AnimatedBackground
import com.proximity.app.ui.components.CurrencyDisplay
import com.proximity.app.ui.components.GlassCard
import com.proximity.app.ui.components.PermissionDialog
import com.proximity.app.ui.components.pressScale
import com.proximity.app.utils.CurrencyFormat
import com.proximity.app.utils.PermissionChecker

class PayContactScreen : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val app = application as AppCore

        setContent {
            val settings by app.services.preferencesStore.settingsFlow.collectAsState()
            AppTheme(settings = settings) {
                PayContactTwoStepFlow(
                    onBack = { finish() },
                    onReviewPayment = { recipientName, phone10, amountWholeRupees ->
                        startActivity(
                            Intent(this, PaymentGuidanceScreen::class.java).apply {
                                putExtra(PaymentGuidanceScreen.EXTRA_RECIPIENT_NAME, recipientName)
                                putExtra(PaymentGuidanceScreen.EXTRA_PHONE_10, phone10)
                                putExtra(PaymentGuidanceScreen.EXTRA_AMOUNT_RUPEES, amountWholeRupees.toString())
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun PayContactTwoStepFlow(
    onBack: () -> Unit,
    onReviewPayment: (String?, String, Int) -> Unit
) {
    val context = LocalContext.current
    val visuals = LocalAppVisuals.current

    var currentStep by rememberSaveable { mutableIntStateOf(1) }
    var phoneDigits by rememberSaveable { mutableStateOf("") }
    var selectedContactName by rememberSaveable { mutableStateOf<String?>(null) }
    var amountInputDigits by rememberSaveable { mutableStateOf("") }

    var showContactsRationale by remember { mutableStateOf(false) }
    var showContactPicker by remember { mutableStateOf(false) }

    val contactsPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showContactPicker = true
        }
    }

    BackHandler(enabled = currentStep == 2) {
        currentStep = 1
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedBackground(modifier = Modifier.fillMaxSize())

        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                } else {
                    slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                }
            },
            label = "PayContactTwoStep"
        ) { step ->
            if (step == 1) {
                val isValid10Digits = Upi123InputValidator.isValidIndianMobile(phoneDigits)
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_back_arrow),
                                contentDescription = stringResource(id = R.string.cd_back),
                                tint = visuals.textPrimary
                            )
                        }
                        Text(
                            text = stringResource(id = R.string.pay_contact_step1_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = visuals.textPrimary
                        )
                        IconButton(
                            onClick = {
                                Toast.makeText(
                                    context,
                                    R.string.pay_contact_step1_help_toast,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_question_circle),
                                contentDescription = stringResource(id = R.string.cd_help),
                                tint = visuals.textPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = stringResource(id = R.string.pay_contact_who_headline),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Serif,
                        color = visuals.textPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(id = R.string.pay_contact_who_subtitle),
                        fontSize = 14.sp,
                        color = visuals.textSecondary
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (phoneDigits.length == 10) {
                                    Modifier.border(
                                        2.dp,
                                        ColorTokens.BrandTeal,
                                        RoundedCornerShape(16.dp)
                                    )
                                } else {
                                    Modifier
                                }
                            ),
                        cornerRadius = 16.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ColorTokens.BrandTeal.copy(alpha = 0.16f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = stringResource(id = R.string.pay_contact_prefix_in),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = visuals.adaptiveTealLabel
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(24.dp)
                                    .background(visuals.outline)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            val phoneFieldDesc = stringResource(id = R.string.pay_contact_phone_placeholder)
                            BasicTextField(
                                value = phoneDigits,
                                onValueChange = { input ->
                                    phoneDigits = input.filter { it.isDigit() }.take(10)
                                    selectedContactName = null
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = visuals.textPrimary,
                                    letterSpacing = 1.sp
                                ),
                                cursorBrush = SolidColor(ColorTokens.BrandTeal),
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { contentDescription = phoneFieldDesc },
                                decorationBox = { innerTextField ->
                                    if (phoneDigits.isEmpty()) {
                                        Text(
                                            text = stringResource(id = R.string.pay_contact_phone_placeholder),
                                            fontSize = 18.sp,
                                            color = visuals.textDisabled
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = {
                            if (PermissionChecker.isContactsGranted(context)) {
                                showContactPicker = true
                            } else {
                                showContactsRationale = true
                            }
                        },
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_contact),
                            contentDescription = null,
                            tint = ColorTokens.BrandTeal,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.pay_contact_choose_contacts),
                            fontSize = 14.sp,
                            color = ColorTokens.BrandTeal,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = { currentStep = 2 },
                        enabled = isValid10Digits,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ColorTokens.BrandTeal,
                            disabledContainerColor = visuals.outline.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .pressScale(enabled = isValid10Digits)
                    ) {
                        Text(
                            text = stringResource(id = R.string.action_continue),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            } else {
                val amountFocusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    runCatching { amountFocusRequester.requestFocus() }
                }

                val parsedInt = amountInputDigits.toIntOrNull() ?: 0
                val isAboveCap = parsedInt > Upi123InputValidator.MAX_AMOUNT
                val isValidAmount = parsedInt in Upi123InputValidator.MIN_AMOUNT..Upi123InputValidator.MAX_AMOUNT

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { currentStep = 1 }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_back_arrow),
                                contentDescription = stringResource(id = R.string.cd_back),
                                tint = visuals.textPrimary
                            )
                        }
                        Text(
                            text = stringResource(id = R.string.pay_contact_step2_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = visuals.textPrimary
                        )
                        IconButton(
                            onClick = {
                                Toast.makeText(
                                    context,
                                    R.string.pay_contact_step2_help_toast,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_question_circle),
                                contentDescription = stringResource(id = R.string.cd_help),
                                tint = visuals.textPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 16.dp
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(id = R.string.pay_contact_recipient_summary_label),
                                fontSize = 12.sp,
                                color = visuals.textSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            if (!selectedContactName.isNullOrBlank()) {
                                Text(
                                    text = selectedContactName!!,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Serif,
                                    color = visuals.textPrimary
                                )
                            }
                            Text(
                                text = CurrencyFormat.formatPhoneForDisplay(phoneDigits),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ColorTokens.BrandTeal
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(36.dp))

                    Text(
                        text = stringResource(id = R.string.pay_contact_enter_amount_label),
                        fontSize = 14.sp,
                        color = visuals.textSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val amountFieldDesc = stringResource(id = R.string.pay_contact_enter_amount_label)
                    Box(contentAlignment = Alignment.Center) {
                        CurrencyDisplay(
                            amountStr = amountInputDigits.ifEmpty { "0" },
                            digitFontSize = 42.sp,
                            color = if (isAboveCap) visuals.statusErrorText else visuals.textPrimary
                        )
                        BasicTextField(
                            value = amountInputDigits,
                            onValueChange = { raw ->
                                amountInputDigits = raw.filter { it.isDigit() }.trimStart('0').take(6)
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .alpha(0.01f)
                                .semantics { contentDescription = amountFieldDesc }
                                .focusRequester(amountFocusRequester)
                        )
                    }

                    if (isAboveCap) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(id = R.string.pay_contact_max_cap_error),
                            fontSize = 12.sp,
                            color = visuals.statusErrorText,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = {
                            onReviewPayment(selectedContactName, phoneDigits, parsedInt)
                        },
                        enabled = isValidAmount,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ColorTokens.BrandTeal,
                            disabledContainerColor = visuals.outline.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .pressScale(enabled = isValidAmount)
                    ) {
                        Text(
                            text = stringResource(id = R.string.pay_contact_review_button),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }

    if (showContactsRationale) {
        PermissionDialog(
            title = stringResource(id = R.string.contacts_rationale_title),
            body = stringResource(id = R.string.contacts_rationale_body),
            onConfirm = {
                showContactsRationale = false
                contactsPermLauncher.launch(Manifest.permission.READ_CONTACTS)
            },
            onDismiss = { showContactsRationale = false }
        )
    }

    if (showContactPicker) {
        PayContactDialog(
            onDismiss = { showContactPicker = false },
            onContactSelected = { item ->
                showContactPicker = false
                phoneDigits = item.phone10Digits
                selectedContactName = item.name
                if (item.phone10Digits.length == 10) {
                    currentStep = 2
                }
            }
        )
    }
}
