// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.ui.connectivity

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.proximity.app.accessibility.UssdDialogReaderService
import com.proximity.app.constants.AppLimits
import com.proximity.app.data.UserPreferencesStore

class ConnectivityTestCoordinator(private val preferencesStore: UserPreferencesStore) {

    fun markUssdPassed() {
        preferencesStore.update { it.copy(ussdTestPassed = true, ussdReportedNotWorking = false) }
    }

    fun markUssdReportedIssue() {
        preferencesStore.update { it.copy(ussdTestPassed = false, ussdReportedNotWorking = true) }
    }

    fun markUpi123Passed() {
        preferencesStore.update { it.copy(upi123TestPassed = true) }
    }

    fun markAllTestsCompleted() {
        UssdDialogReaderService.disableService()
        preferencesStore.update { it.copy(connectivityTestsCompleted = true) }
    }

    fun dialUssdTest(context: Context) {
        UssdDialogReaderService.reset()
        UssdDialogReaderService.ussdPaymentActive = true
        dialUriSafely(context, "tel:*99%23")
    }

    fun dialUpi123Test(context: Context) {
        val ivrNumber = if (preferencesStore.settingsFlow.value.isJioPrimarySim) {
            AppLimits.UPI123_JIO_IVR_NUMBER
        } else {
            AppLimits.UPI123_IVR_NUMBER
        }
        dialUriSafely(context, "tel:$ivrNumber")
    }

    private fun dialUriSafely(context: Context, uriStr: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_CALL, Uri.parse(uriStr)))
        } catch (_: SecurityException) {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse(uriStr)))
        } catch (_: Exception) {
        }
    }
}
