// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proximity.app.constants.PermissionConstants
import com.proximity.app.helpers.SetupHelper
import com.proximity.app.helpers.TestConfigurationHelper
import com.proximity.app.managers.CallType
import com.proximity.app.ui.components.AnimatedContourBackground
import com.proximity.app.ui.dialogs.Upi123InstructionDialog
import com.proximity.app.ui.dialogs.Upi123ProgressDialog
import com.proximity.app.ui.dialogs.UssdInstructionDialog
import com.proximity.app.ui.dialogs.UssdProgressDialog
import com.proximity.app.ui.theme.TealAccentTheme
import com.proximity.app.ui.theme.ProximityAccentGreen
import com.proximity.app.ui.theme.ProximityDarkGray
import com.proximity.app.ui.theme.ProximityLightGray
import com.proximity.app.ui.theme.ProximityMediumGray
import com.proximity.app.ui.theme.ProximityStatusWarning
import com.proximity.app.ui.theme.ProximitySurfaceDim
import com.proximity.app.ui.theme.ProximityTextGray
import com.proximity.app.ui.theme.ProximityTextLightGray
import com.proximity.app.ui.theme.ProximityTextPale
import com.proximity.app.ui.theme.ProximityTheme
import com.proximity.app.ui.theme.LocalProximityAccentTheme
import com.proximity.app.ui.theme.ProximityBlack
import kotlinx.coroutines.delay

class TestConfigurationActivity : ComponentActivity() {
    private lateinit var testHelper: TestConfigurationHelper
    private var showUssdAccessibilityDialog = false

    // Phone-call permission group, requested before a test dial. No auto-retry:
    // the user re-taps the test action once granted.
    private val phonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        testHelper.onPhonePermissionsResult(results.values.all { it })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize test helper
        testHelper = TestConfigurationHelper(
            this,
            object : TestConfigurationHelper.UICallback {
                override fun showToast(message: String) {
                    runOnUiThread {
                        android.widget.Toast.makeText(this@TestConfigurationActivity, message, android.widget.Toast.LENGTH_LONG).show()
                    }
                }

                override fun updateUssdTesting(isTesting: Boolean) {
                    // State will be managed by the composable
                }

                override fun updateUssdDialog(show: Boolean) {
                    // State will be managed by the composable
                }

                override fun updateUssdTestCompleted(completed: Boolean) {
                    // State will be managed by the composable
                }

                override fun updateUpi123Testing(isTesting: Boolean) {
                    // State will be managed by the composable
                }

                override fun updateUpi123TestCompleted(completed: Boolean) {
                    // State will be managed by the composable
                }

                override fun updateUpi123Dialog(show: Boolean) {
                    // State will be managed by the composable
                }

                override fun updateUpi123ConfigurationOptions(show: Boolean) {
                    // State will be managed by the composable
                }

                override fun updateVoiceTesting(isTesting: Boolean) {
                    // State will be managed by the composable
                }

                override fun updateVoiceDialog(show: Boolean) {
                    // State will be managed by the composable
                }

                override fun updateVoiceTestCompleted(completed: Boolean) {
                    // State will be managed by the composable
                }

                override fun updateCallCompleteButton(show: Boolean) {
                    // State will be managed by the composable
                }

                override fun updateUssdProgressMessage(message: String) {
                    // State will be managed by the composable
                }

                override fun updateUssdConfigurationOptions(show: Boolean) {
                    // State will be managed by the composable
                }

                override fun navigateToMain() {
                    val intent = Intent(this@TestConfigurationActivity, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                }

                override fun requestPhonePermissions() {
                    phonePermissionLauncher.launch(PermissionConstants.PHONE_PERMISSIONS)
                }

                override fun onAccessibilityAcknowledged() {
                    runOnUiThread {
                        showUssdAccessibilityDialog = true
                    }
                }
            }
        )

        // Initialize the helper
        testHelper.initialize()

        setTheme(R.style.Theme_Proximity)
        // Edge-to-edge: Compose insets are the single source of padding (see MainActivity).
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            CompositionLocalProvider(LocalProximityAccentTheme provides TealAccentTheme) {
                ProximityTheme {
                    TestConfigurationScreen(
                        testHelper = testHelper,
                        showUssdAccessibilityDialog = ::showUssdAccessibilityDialog
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregisters the CallManager's PhoneStateListener and cancels
        // pending timeout runnables â€” without this, a listener registered
        // for an in-flight test call leaks past the screen.
        if (::testHelper.isInitialized) {
            testHelper.cleanup()
        }
    }
}

@Composable
fun TestConfigurationScreen(
    testHelper: TestConfigurationHelper,
    showUssdAccessibilityDialog: () -> Boolean
) {
    val context = LocalContext.current
    val accent = LocalProximityAccentTheme.current

    // Get test states from helper
    val testStates = testHelper.getTestStates()
    var ussdTestCompleted by remember { mutableStateOf(testStates.ussdTestCompleted) }
    var upi123TestCompleted by remember { mutableStateOf(testStates.upi123TestCompleted) }
    var ussdTesting by remember { mutableStateOf(testStates.ussdTesting) }
    var upi123Testing by remember { mutableStateOf(testStates.upi123Testing) }
    // Holds the pending real-call dial action until the user consents (a test
    // dial places a real *99#/UPI 123 call that may incur carrier charges).
    var pendingDial by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showUssdDialog by remember { mutableStateOf(testStates.showUssdDialog) }
    var showUpi123Dialog by remember { mutableStateOf(testStates.showUpi123Dialog) }
    var showUssdConfigurationOptions by remember { mutableStateOf(testStates.showUssdConfigurationOptions) }
    var showUpi123ConfigurationOptions by remember { mutableStateOf(testStates.showUpi123ConfigurationOptions) }
    var ussdProgressMessage by remember { mutableStateOf(testStates.ussdProgressMessage) }
    var showCallCompleteButton by remember { mutableStateOf(testStates.showCallCompleteButton) }
    var showUpi123InstructionGuidance by remember { mutableStateOf(false) }
    var showUssdInstructionGuidance by remember { mutableStateOf(false) }
    var showA11yDialog by remember { mutableStateOf(showUssdAccessibilityDialog()) }

    // Sync the accessibility dialog flag from the activity
    LaunchedEffect(showUssdAccessibilityDialog) {
        showA11yDialog = showUssdAccessibilityDialog()
    }

    // Load existing test results
    LaunchedEffect(Unit) {
        val existingResults = testHelper.getTestResults()
        if (existingResults != null) {
            ussdTestCompleted = existingResults.ussdEnabled
            upi123TestCompleted = existingResults.upi123Enabled
        }
    }

    // Update states when helper states change
    LaunchedEffect(testStates) {
        ussdTestCompleted = testStates.ussdTestCompleted
        upi123TestCompleted = testStates.upi123TestCompleted
        ussdTesting = testStates.ussdTesting
        upi123Testing = testStates.upi123Testing
        showUssdDialog = testStates.showUssdDialog
        showUpi123Dialog = testStates.showUpi123Dialog
        showUssdConfigurationOptions = testStates.showUssdConfigurationOptions
        showUpi123ConfigurationOptions = testStates.showUpi123ConfigurationOptions
        ussdProgressMessage = testStates.ussdProgressMessage
        showCallCompleteButton = testStates.showCallCompleteButton
    }

    // Add a periodic state check to ensure UI updates
    LaunchedEffect(Unit) {
        while (true) {
            delay(100) // Check every 100ms
            val currentStates = testHelper.getTestStates()
            ussdTestCompleted = currentStates.ussdTestCompleted
            upi123TestCompleted = currentStates.upi123TestCompleted
            ussdTesting = currentStates.ussdTesting
            upi123Testing = currentStates.upi123Testing
            showUssdDialog = currentStates.showUssdDialog
            showUpi123Dialog = currentStates.showUpi123Dialog
            showUssdConfigurationOptions = currentStates.showUssdConfigurationOptions
            showUpi123ConfigurationOptions = currentStates.showUpi123ConfigurationOptions
            ussdProgressMessage = currentStates.ussdProgressMessage
            showCallCompleteButton = currentStates.showCallCompleteButton
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedContourBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 420.dp)
                .align(Alignment.Center)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Hero Typography & Back Arrow
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                IconButton(
                    onClick = {
                        context.startActivity(Intent(context, SetupActivity::class.java))
                        (context as? Activity)?.finish()
                    },
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.testcfg_back),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                Text(
                    text = "System Tests",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    lineHeight = 40.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Test Buttons Container
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // USSD Test Button
                val isJioSim = !SetupHelper.isPrimarySimUssdCapable(context)
                val userReportedUssdIssue = SetupHelper.hasUserReportedUssdNotWorking(context)
                TestButton(
                    title = stringResource(R.string.testcfg_set_up),
                    code = "*99#",
                    description = when {
                        isJioSim -> stringResource(R.string.testcfg_jio_no_ussd)
                        userReportedUssdIssue -> stringResource(R.string.testcfg_ussd_reported_issue)
                        else -> stringResource(R.string.testcfg_enable_scan_payments)
                    },
                    isCompleted = ussdTestCompleted || isJioSim || userReportedUssdIssue,
                    isTesting = ussdTesting,
                    isUnsupported = isJioSim,
                    onClick = {
                        if (isJioSim) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.testcfg_jio_no_ussd),
                                Toast.LENGTH_LONG
                            ).show()
                        } else if (!ussdTestCompleted && !ussdTesting) {
                            showUssdInstructionGuidance = true
                        }
                    }
                )

                // UPI123 Test Button
                TestButton(
                    title = stringResource(R.string.testcfg_set_up),
                    code = "UPI123",
                    description = stringResource(R.string.testcfg_enable_manual_payments),
                    isCompleted = upi123TestCompleted,
                    isTesting = upi123Testing,
                    onClick = {
                        if (!upi123TestCompleted && !upi123Testing) {
                            showUpi123InstructionGuidance = true
                        }
                    }
                )

            }

            Spacer(modifier = Modifier.height(40.dp))

            // Continue Button â€” Gradient
            val canContinue = testHelper.canContinue()
            val allTestsCompleted = testHelper.allTestsCompleted()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(58.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        color = if (canContinue) accent.primary else ProximityMediumGray
                    )
                    .then(
                        if (canContinue) {
                            Modifier.clickable { testHelper.continueToMain() }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        allTestsCompleted -> "All Tests Passed! Continue"
                        canContinue -> "Continue with partial setup"
                        else -> "Complete tests to continue"
                    },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (canContinue) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Skip path
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Skip for now - payments may not work until tests pass",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable { testHelper.skipTests() }
                    .padding(8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        // USSD Accessibility Permission Dialog
        if (showA11yDialog) {
            AlertDialog(
                onDismissRequest = { showA11yDialog = false },
                title = {
                    Text(
                        text = stringResource(R.string.ussd_accessibility_permission_title),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.ussd_accessibility_permission_message),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showA11yDialog = false
                        com.proximity.app.utils.UssdAccessibilityUtils.openAccessibilitySettings(context)
                    }) {
                        Text(stringResource(R.string.ussd_accessibility_permission_cta), fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showA11yDialog = false
                        testHelper.retryUssdAfterAccessibility()
                    }) {
                        Text(
                            text = stringResource(R.string.ussd_accessibility_permission_skip),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }

        // USSD Progress Dialog
        UssdProgressDialog(
            isVisible = showUssdDialog || showUssdConfigurationOptions,
            progressMessage = ussdProgressMessage,
            showConfigurationOptions = showUssdConfigurationOptions,
            onConfigured = { testHelper.handleUssdConfigurationConfirmation(true) },
            onNotConfigured = { testHelper.handleUssdConfigurationConfirmation(false) },
            onDismiss = { testHelper.dismissUssdDialog(fromDoesNotWork = false) },
            onDoesNotWork = { testHelper.dismissUssdDialog(fromDoesNotWork = true) }
        )

        // UPI123 Progress Dialog
        Upi123ProgressDialog(
            isVisible = showUpi123Dialog || showUpi123ConfigurationOptions,
            showConfigurationOptions = showUpi123ConfigurationOptions,
            onConfigured = { testHelper.handleUpi123ConfigurationConfirmation(true) },
            onNotConfigured = { testHelper.handleUpi123ConfigurationConfirmation(false) },
            onDismiss = { testHelper.dismissUpi123Dialog() }
        )

        // UPI123 Instruction Guidance Dialog
        Upi123InstructionDialog(
            isVisible = showUpi123InstructionGuidance,
            onProceed = {
                showUpi123InstructionGuidance = false
                pendingDial = {
                    upi123Testing = true
                    testHelper.initiateUpi123Test()
                }
            },
            onAlreadySetUp = {
                showUpi123InstructionGuidance = false
                testHelper.handleUpi123ConfigurationConfirmation(true)
                android.widget.Toast.makeText(context, "UPI 123Pay marked as set up", android.widget.Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                showUpi123InstructionGuidance = false
            }
        )

        // USSD Instruction Guidance Dialog
        UssdInstructionDialog(
            isVisible = showUssdInstructionGuidance,
            onProceed = {
                showUssdInstructionGuidance = false
                pendingDial = {
                    ussdTesting = true
                    testHelper.initiateCall(CallType.USSD)
                }
            },
            onAlreadySetUp = {
                showUssdInstructionGuidance = false
                testHelper.handleUssdConfigurationConfirmation(true)
                android.widget.Toast.makeText(context, "USSD *99# marked as set up", android.widget.Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                showUssdInstructionGuidance = false
            }
        )

        // Consent before placing a real test call to the carrier.
        pendingDial?.let { dial ->
            AlertDialog(
                onDismissRequest = { pendingDial = null },
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                title = {
                    Text(
                        stringResource(R.string.testcfg_consent_title),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Text(
                        "This places a real call to your carrier's *99# / UPI 123 " +
                            "service to check setup on your SIM. Standard call or USSD " +
                            "charges from your operator may apply.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        pendingDial = null
                        dial()
                    }) {
                        Text(stringResource(R.string.action_continue), fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDial = null }) {
                        Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }
    }
}



@Composable
fun TestButton(
    title: String,
    code: String,
    description: String,
    isCompleted: Boolean,
    isTesting: Boolean,
    isUnsupported: Boolean = false,
    onClick: () -> Unit
) {
    val accent = LocalProximityAccentTheme.current

    val iconBgColor = when {
        isUnsupported -> ProximityStatusWarning.copy(alpha = 0.15f)
        isCompleted -> ProximityAccentGreen.copy(alpha = 0.15f)
        else -> accent.primary.copy(alpha = 0.15f)
    }
    val iconTint = when {
        isUnsupported -> ProximityStatusWarning
        isCompleted -> ProximityAccentGreen
        else -> accent.primary
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = !isCompleted && !isTesting) { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon circle
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = iconBgColor,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (code == "*99#") UssdIcon else UpiIcon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Text content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isUnsupported) ProximityStatusWarning else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = code,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isUnsupported) ProximityStatusWarning else accent.accent,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = if (isUnsupported) ProximityStatusWarning.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }

            // Status indicator
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isTesting -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = accent.primary,
                            strokeWidth = 2.5.dp,
                            trackColor = ProximityLightGray
                        )
                    }
                    isUnsupported -> {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(ProximityStatusWarning, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "!",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    isCompleted -> {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(ProximityAccentGreen, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = CheckIcon,
                                contentDescription = "Completed",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

// Configuration Dialogs - Now integrated into progress dialogs

// Custom Icons


val UssdIcon: ImageVector
    get() {
        return ImageVector.Builder(
            name = "ussd_phone",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Material Design phone icon (filled)
            path(
                fill = androidx.compose.ui.graphics.SolidColor(Color.White)
            ) {
                moveTo(6.62f, 10.79f)
                curveTo(8.06f, 13.62f, 10.38f, 15.93f, 13.21f, 17.38f)
                lineTo(15.41f, 15.18f)
                curveTo(15.68f, 14.91f, 16.08f, 14.82f, 16.43f, 14.94f)
                curveTo(17.55f, 15.31f, 18.76f, 15.51f, 20f, 15.51f)
                curveTo(20.55f, 15.51f, 21f, 15.96f, 21f, 16.51f)
                lineTo(21f, 20f)
                curveTo(21f, 20.55f, 20.55f, 21f, 20f, 21f)
                curveTo(10.61f, 21f, 3f, 13.39f, 3f, 4f)
                curveTo(3f, 3.45f, 3.45f, 3f, 4f, 3f)
                lineTo(7.5f, 3f)
                curveTo(8.05f, 3f, 8.5f, 3.45f, 8.5f, 4f)
                curveTo(8.5f, 5.25f, 8.7f, 6.45f, 9.07f, 7.57f)
                curveTo(9.18f, 7.92f, 9.1f, 8.31f, 8.82f, 8.59f)
                lineTo(6.62f, 10.79f)
                close()
            }
        }.build()
    }

val UpiIcon: ImageVector
    get() {
        return ImageVector.Builder(
            name = "upi_payment",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Material Design credit card / payment icon (filled)
            path(
                fill = androidx.compose.ui.graphics.SolidColor(Color.White)
            ) {
                moveTo(20f, 4f)
                lineTo(4f, 4f)
                curveTo(2.89f, 4f, 2.01f, 4.89f, 2.01f, 6f)
                lineTo(2f, 18f)
                curveTo(2f, 19.11f, 2.89f, 20f, 4f, 20f)
                lineTo(20f, 20f)
                curveTo(21.11f, 20f, 22f, 19.11f, 22f, 18f)
                lineTo(22f, 6f)
                curveTo(22f, 4.89f, 21.11f, 4f, 20f, 4f)
                close()
            }
        }.build()
    }

val CheckIcon: ImageVector = Icons.Filled.CheckCircle
