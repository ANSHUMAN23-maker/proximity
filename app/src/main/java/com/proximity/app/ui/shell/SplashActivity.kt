// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.ui.shell

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import com.proximity.app.AppCore
import com.proximity.app.theme.AppTheme
import com.proximity.app.ui.connectivity.ConnectivityCheckActivity
import com.proximity.app.ui.onboarding.OnboardingFlowActivity
import com.proximity.app.ui.splash.SplashScreen

class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val app = application as AppCore

        setContent {
            val settings by app.services.preferencesStore.settingsFlow.collectAsState()
            AppTheme(settings = settings) {
                SplashScreen(
                    onSplashFinished = {
                        val nextIntent = when {
                            !settings.setupCompleted ->
                                Intent(this@SplashActivity, OnboardingFlowActivity::class.java)
                            !settings.connectivityTestsCompleted ->
                                Intent(this@SplashActivity, ConnectivityCheckActivity::class.java)
                            else ->
                                Intent(this@SplashActivity, HomeShellActivity::class.java)
                        }
                        startActivity(nextIntent)
                        finish()
                    }
                )
            }
        }
    }
}
