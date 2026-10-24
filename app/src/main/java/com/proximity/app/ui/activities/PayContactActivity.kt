package com.proximity.app.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PermContactCalendar
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proximity.app.ProximityApplication
import com.proximity.app.R
import com.proximity.app.constants.AppConstants
import com.proximity.app.managers.PermissionManager
import com.proximity.app.payment.Upi123CallStringBuilder
import com.proximity.app.payment.messageFor
import com.proximity.app.ui.components.*
import com.proximity.app.ui.dialogs.ContactPickerDialog
import com.proximity.app.ui.theme.*
import com.proximity.app.utils.findComponentActivity
import kotlinx.coroutines.delay

class PayContactActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        val app = application as? ProximityApplication
        val settingsRepository = app?.settingsRepository ?: com.proximity.app.data.SettingsRepository(applicationContext)

        setContent {
            val savedSettings by settingsRepository.settingsFlow.collectAsState()
            CompositionLocalProvider(LocalProximityAccentTheme provides TealAccentTheme) {
                ProximityTheme(darkTheme = savedSettings.darkModeEnabled) {
                    PayContactFlowScreen(
                        onBack = { finish() },
                        onReviewPayment = { phone, amount ->
                            initiatePayment(phone, amount)
                        }
                    )
                }
            }
        }
    }

    private fun initiatePayment(phoneNumber: String, amount: String) {
        val intent = Intent(this, PaymentGuidanceActivity::class.java).apply {
            putExtra(PaymentGuidanceActivity.EXTRA_PHONE_NUMBER, phoneNumber)
            putExtra(PaymentGuidanceActivity.EXTRA_AMOUNT, amount)
        }
        startActivity(intent)
        finish()
    }
}

private enum class PayContactStep {
    PHONE_NUMBER,
    AMOUNT
}

@Composable
fun PayContactFlowScreen(
    onBack: () -> Unit,
    onReviewPayment: (String, String) -> Unit
) {
    var step by remember { mutableStateOf(PayContactStep.PHONE_NUMBER) }
    var phoneNumber by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var selectedContactName by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val hostActivity = remember(context) { context.findComponentActivity() }
    var showContactPicker by remember { mutableStateOf(false) }
    var showContactPermissionDialog by remember { mutableStateOf(false) }
    val permissionManager = remember(hostActivity) {
        hostActivity?.let { PermissionManager(it) }
    }

    val contactPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showContactPicker = true
    }

    // Clipboard paste detection: auto-fill 10-digit Indian numbers
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    LaunchedEffect(Unit) {
        val primaryClip = clipboardManager.primaryClip
        if (primaryClip != null && primaryClip.itemCount > 0) {
            val clipboardText = primaryClip.getItemAt(0)?.text?.toString() ?: ""
            val digits = clipboardText.filter { it.isDigit() }
            if (digits.length == 10) {
                phoneNumber = digits
            }
        }
    }

    BackHandler {
        if (step == PayContactStep.AMOUNT) {
            step = PayContactStep.PHONE_NUMBER
        } else {
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Line art contour animation background
        AnimatedContourBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    if (targetState == PayContactStep.AMOUNT) {
                        slideInHorizontally { it } + fadeIn() togetherWith
                                slideOutHorizontally { -it } + fadeOut()
                    } else {
                        slideInHorizontally { -it } + fadeIn() togetherWith
                                slideOutHorizontally { it } + fadeOut()
                    }
                },
                label = "pay_contact_step"
            ) { currentStep ->
                when (currentStep) {
                    PayContactStep.PHONE_NUMBER -> {
                        PhoneNumberStepScreen(
                            phoneNumber = phoneNumber,
                            onPhoneNumberChange = { phoneNumber = it },
                            onSelectContactClick = {
                                val pm = permissionManager
                                if (pm != null && pm.hasContactPermission()) {
                                    showContactPicker = true
                                } else {
                                    showContactPermissionDialog = true
                                }
                            },
                            onContinue = {
                                if (phoneNumber.length == 10) {
                                    step = PayContactStep.AMOUNT
                                }
                            },
                            onBackClick = onBack
                        )
                    }
                    PayContactStep.AMOUNT -> {
                        AmountStepScreen(
                            phoneNumber = phoneNumber,
                            contactName = selectedContactName,
                            amount = amount,
                            onAmountChange = { amount = it },
                            onReviewPayment = {
                                onReviewPayment(phoneNumber, amount)
                            },
                            onBackClick = {
                                step = PayContactStep.PHONE_NUMBER
                            }
                        )
                    }
                }
            }
        }

        if (showContactPicker) {
            ContactPickerDialog(
                onDismiss = { showContactPicker = false },
                onContactSelected = { contact ->
                    val digits = contact.phoneNumber.filter { it.isDigit() }
                    phoneNumber = if (digits.length >= 10) digits.takeLast(10) else digits
                    selectedContactName = contact.name
                    showContactPicker = false
                    if (phoneNumber.length == 10) {
                        step = PayContactStep.AMOUNT
                    }
                }
            )
        }

        if (showContactPermissionDialog) {
            PermissionExplanationDialog(
                title = "Contacts Permission",
                message = "Proximity needs access to your contacts so you can pick a recipient by name instead of typing their number.",
                confirmButtonText = "Grant",
                onConfirm = {
                    showContactPermissionDialog = false
                    contactPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                },
                onDismiss = { showContactPermissionDialog = false }
            )
        }
    }
}

@Composable
private fun PhoneNumberStepScreen(
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    onSelectContactClick: () -> Unit,
    onContinue: () -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val accent = LocalProximityAccentTheme.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(150)
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = "Pay a number",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(onClick = {
                Toast.makeText(context, "Enter a 10-digit mobile number to pay via UPI 123", Toast.LENGTH_SHORT).show()
            }) {
                Icon(
                    imageVector = Icons.Outlined.HelpOutline,
                    contentDescription = "Help",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Headline & Subtitle
        Text(
            text = "Who are you paying?",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Enter their mobile number. We'll find their proximity account.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Label above
        Text(
            text = "Mobile number",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))

        // Phone Input Box with IN +91 Pill
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(
                1.5.dp,
                if (phoneNumber.length == 10) accent.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Country Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "IN +91",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                VerticalDivider(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .width(1.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )

                // Native System Number Pad TextField
                BasicTextField(
                    value = phoneNumber,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }
                        if (digits.length <= 10) {
                            onPhoneNumberChange(digits)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (phoneNumber.length == 10) {
                                onContinue()
                            }
                        }
                    ),
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 1.sp
                    ),
                    decorationBox = { innerTextField ->
                        if (phoneNumber.isEmpty()) {
                            Text(
                                text = "123 456 7890",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                letterSpacing = 1.sp
                            )
                        }
                        innerTextField()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Choose from contacts link
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onSelectContactClick() }
                .padding(vertical = 4.dp, horizontal = 4.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Choose from contacts"
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PermContactCalendar,
                contentDescription = null,
                tint = accent.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Choose from contacts",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent.primary
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Continue Button
        val canContinue = phoneNumber.length == 10
        Button(
            onClick = onContinue,
            enabled = canContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .pressScale(enabled = canContinue),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accent.primary,
                disabledContainerColor = accent.primary.copy(alpha = 0.4f)
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Continue",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun AmountStepScreen(
    phoneNumber: String,
    contactName: String?,
    amount: String,
    onAmountChange: (String) -> Unit,
    onReviewPayment: () -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val accent = LocalProximityAccentTheme.current
    val focusRequester = remember { FocusRequester() }
    val isOverCap = (amount.toLongOrNull() ?: 0L) > AppConstants.UPI123PAY_MAX_AMOUNT.toLong()
    LaunchedEffect(Unit) {
        delay(150)
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = "Enter amount",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(onClick = {
                Toast.makeText(context, "Enter the amount to transfer (up to ₹4,999)", Toast.LENGTH_SHORT).show()
            }) {
                Icon(
                    imageVector = Icons.Outlined.HelpOutline,
                    contentDescription = "Help",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Recipient Summary Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Payment is being processed to:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (!contactName.isNullOrBlank()) "$contactName (+91 ${formatPhoneDisplay(phoneNumber)})" else "+91 ${formatPhoneDisplay(phoneNumber)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        // Center Amount Section with hidden system input field for native keyboard
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Enter the Amount:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Quick amount chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("100", "500", "1000", "2000").forEach { chipAmount ->
                    val chipSelected = amount == chipAmount
                    Button(
                        onClick = { onAmountChange(chipAmount) },
                        modifier = Modifier
                            .widthIn(min = 72.dp)
                            .height(40.dp)
                            .pressScale(enabled = true),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (chipSelected) accent.primary
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            contentColor = if (chipSelected) Color.White
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(
                            text = "\u20B9$chipAmount",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (chipSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Display styled CurrencyText
            CurrencyText(
                amount = if (amount.isEmpty()) "0" else amount,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                color = if (isOverCap) ProximityStatusError else MaterialTheme.colorScheme.onBackground
            )

            // Native system number pad input with accessible touch target
            BasicTextField(
                value = amount,
                onValueChange = { input ->
                    val filtered = input.filter { it.isDigit() }
                    if (filtered.length <= 6) {
                        onAmountChange(filtered)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp)
                    .focusRequester(focusRequester)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Amount input. Enter up to \${AppConstants.UPI123PAY_MAX_AMOUNT} rupees."
                    },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (amount.isNotEmpty() && amount != "0" && !isOverCap) {
                            onReviewPayment()
                        }
                    }
                ),
                singleLine = true
            )

            if (isOverCap) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = Upi123CallStringBuilder.Reason.AMOUNT_ABOVE_CAP.messageFor(context),
                    color = ProximityStatusError,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics {
                        error(Upi123CallStringBuilder.Reason.AMOUNT_ABOVE_CAP.messageFor(context))
                    }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Review Payment Button
        val canReview = amount.isNotEmpty() && amount != "0" && !isOverCap
        Button(
            onClick = onReviewPayment,
            enabled = canReview,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .pressScale(enabled = canReview),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accent.primary,
                disabledContainerColor = accent.primary.copy(alpha = 0.4f)
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Review payment",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

private fun formatPhoneDisplay(number: String): String {
    if (number.length <= 3) return number
    if (number.length <= 6) return "${number.substring(0, 3)} ${number.substring(3)}"
    return "${number.substring(0, 3)} ${number.substring(3, 6)} ${number.substring(6)}"
}

@Composable
private fun PermissionExplanationDialog(
    title: String,
    message: String,
    confirmButtonText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Text(
                text = message,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = LocalProximityAccentTheme.current.accent
                )
            ) {
                Text(confirmButtonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_not_now), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Preview(name = "Pay Contact Flow - Light", showBackground = true)
@Preview(name = "Pay Contact Flow - Dark", showBackground = true, backgroundColor = 0xFF090D14)
@Composable
private fun PayContactFlowPreview() {
    ProximityTheme {
        PayContactFlowScreen(
            onBack = {},
            onReviewPayment = { _, _ -> }
        )
    }
}
