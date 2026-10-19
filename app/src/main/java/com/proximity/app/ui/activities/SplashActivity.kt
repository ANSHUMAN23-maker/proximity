// Copyright © 2026 Proximity. All rights reserved.
// Proprietary and confidential — No license granted.
// See LICENSE file for full terms.

package com.proximity.app.ui.activities

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.proximity.app.MainActivity
import com.proximity.app.ProximityApplication
import com.proximity.app.R
import com.proximity.app.SetupActivity
import com.proximity.app.TestConfigurationActivity
import com.proximity.app.constants.AppConstants
import com.proximity.app.ui.components.AnimatedContourBackground
import com.proximity.app.ui.theme.LocalProximityAccentTheme
import com.proximity.app.ui.theme.ProximityTheme
import com.proximity.app.ui.theme.TealAccentTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashActivity : ComponentActivity() {

    companion object {
        internal const val SHORTCUT_ACTION_SCAN_QR = "com.proximity.app.action.SCAN_QR"
        internal const val SHORTCUT_ACTION_PAY_CONTACT = "com.proximity.app.action.PAY_CONTACT"
        internal const val SHORTCUT_ACTION_RECHARGE = "com.proximity.app.action.RECHARGE"
        internal const val SHORTCUT_ACTION_TRANSACTIONS = "com.proximity.app.action.VIEW_TRANSACTIONS"
        internal const val SHORTCUT_ACTION_SETUP = "com.proximity.app.action.SETUP"
    }

    private var pendingShortcutAction: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Capture shortcut intent action for post-splash routing
        pendingShortcutAction = intent.action

        val prefs = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode_enabled", false)

        // Set initial window background and system bar icon appearance synchronously on Frame 1
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        val bgColor = if (isDarkMode) Color.parseColor("#090D14") else Color.parseColor("#F8FAFC")
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.decorView.setBackgroundColor(bgColor)

        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDarkMode
        insetsController.isAppearanceLightNavigationBars = !isDarkMode

        setContent {
            // Lazy async background pre-warming of app container services
            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    val app = application as? ProximityApplication
                    app?.container?.settingsRepository
                }
            }

            CompositionLocalProvider(LocalProximityAccentTheme provides TealAccentTheme) {
                ProximityTheme(darkTheme = isDarkMode) {
                    SplashScreenContent(
                        onSplashFinished = {
                            navigateToNextScreen()
                        }
                    )
                }
            }
        }
    }

    private fun navigateToNextScreen() {
        val prefs = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val setupCompleted = prefs.getBoolean(AppConstants.KEY_SETUP_COMPLETED, false)
        val testCompleted = prefs.getBoolean(AppConstants.KEY_TEST_COMPLETED, false)

        // If setup is not complete, always route to SetupActivity first
        if (!setupCompleted) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            overrideTransition()
            return
        }

        // If setup is complete but shortcut targets a specific screen, route there
        val targetIntent = when (pendingShortcutAction) {
            SHORTCUT_ACTION_SCAN_QR -> Intent(this, MainActivity::class.java).apply {
                putExtra("shortcut_action", SHORTCUT_ACTION_SCAN_QR)
            }
            SHORTCUT_ACTION_PAY_CONTACT -> Intent(this, MainActivity::class.java).apply {
                putExtra("shortcut_action", SHORTCUT_ACTION_PAY_CONTACT)
            }
            SHORTCUT_ACTION_RECHARGE -> Intent(this, MainActivity::class.java).apply {
                putExtra("shortcut_action", SHORTCUT_ACTION_RECHARGE)
            }
            SHORTCUT_ACTION_TRANSACTIONS -> Intent(this, com.proximity.app.ui.activities.TransactionHistoryActivity::class.java)
            SHORTCUT_ACTION_SETUP -> Intent(this, SetupActivity::class.java)
            else -> when {
                !testCompleted -> Intent(this, TestConfigurationActivity::class.java)
                else -> Intent(this, MainActivity::class.java)
            }
        }

        startActivity(targetIntent)
        finish()
        overrideTransition()
    }

    private fun overrideTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}

/**
 * Splash Screen content featuring smooth spring-popup logo entrance, lazy-loaded Lottie animation,
 * and adaptive Light/Dark theme styling.
 */
@Composable
fun SplashScreenContent(
    onSplashFinished: () -> Unit
) {
    val accent = LocalProximityAccentTheme.current

    // Lazy load the Lottie composition asynchronously in the background
    val compositionResult = rememberLottieComposition(LottieCompositionSpec.Asset("splash_logo.json"))
    val composition = compositionResult.value
    val isLottieLoading = compositionResult.isLoading

    // Smooth spring popup entrance scale & alpha for Frame 1
    val entranceScale = remember { Animatable(0.5f) }
    val entranceAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            entranceAlpha.animateTo(1f, animationSpec = tween(400))
        }
        launch {
            entranceScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    val lottieProgress by animateLottieCompositionAsState(
        composition = composition,
        iterations = 1,
        speed = 1.25f
    )

    // Trigger splash finish when Lottie animation reaches 100%
    LaunchedEffect(lottieProgress) {
        if (lottieProgress >= 1.0f) {
            onSplashFinished()
        }
    }

    // Safety fallback timeout
    LaunchedEffect(Unit) {
        delay(2200)
        onSplashFinished()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            // Background line art animation
            AnimatedContourBackground(modifier = Modifier.fillMaxSize())

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .scale(entranceScale.value)
                    .alpha(entranceAlpha.value)
            ) {
                // Unified 220.dp Logo Container for perfectly centered popup & smooth transition
                Box(
                    modifier = Modifier.size(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isLottieLoading && composition != null) {
                        LottieAnimation(
                            composition = composition,
                            progress = { lottieProgress },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Instant Frame-1 Brand Emblem Popup
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .clip(CircleShape)
                                .background(accent.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_launcher_foreground),
                                contentDescription = "Proximity Logo",
                                modifier = Modifier.size(96.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Proximity",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Offline Payments, Simplified",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Preview(name = "Splash Screen - Light", showBackground = true)
@Preview(name = "Splash Screen - Dark", showBackground = true, backgroundColor = 0xFF090D14)
@Composable
private fun SplashScreenContentPreview() {
    ProximityTheme {
        SplashScreenContent(onSplashFinished = {})
    }
}
