// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.proximity.app.accessibility.UssdDialogReaderService
import com.proximity.app.dependency.ServiceRegistry
import com.proximity.app.features.qr.QrScannerActivity
import com.proximity.app.ui.onboarding.OnboardingFlowActivity

class AppCore : Application() {

    lateinit var services: ServiceRegistry
        private set

    @Volatile
    var isInForeground: Boolean = false
        private set

    private var startedActivityCount = 0

    override fun onCreate() {
        super.onCreate()
        services = ServiceRegistry(this)
        services.initializeBackgroundServices()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {
                startedActivityCount++
                isInForeground = startedActivityCount > 0
            }
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {
                startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
                isInForeground = startedActivityCount > 0
                if (startedActivityCount == 0 &&
                    !activity.isChangingConfigurations &&
                    activity !is QrScannerActivity &&
                    activity !is OnboardingFlowActivity &&
                    !services.paymentLifecycleManager.isUssdPaymentInFlight() &&
                    !UssdDialogReaderService.ussdPaymentActive
                ) {
                    UssdDialogReaderService.disableService()
                }
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    override fun onTerminate() {
        services.shutdown()
        super.onTerminate()
    }
}
