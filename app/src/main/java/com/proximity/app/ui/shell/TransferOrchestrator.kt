// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.ui.shell

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.proximity.app.R
import com.proximity.app.data.UserSettings
import com.proximity.app.features.qr.QrScannerActivity
import com.proximity.app.ui.connectivity.ConnectivityCheckActivity
import com.proximity.app.ui.faq.FaqScreen
import com.proximity.app.ui.payment.PayContactScreen
import com.proximity.app.ui.payment.RechargeWebScreen

object TransferOrchestrator {

    fun openQrScannerOrSetup(
        context: Context,
        settings: UserSettings,
        onAllowedToOpenScanner: () -> Unit
    ) {
        when {
            settings.isJioPrimarySim -> {
                Toast.makeText(context, R.string.testcfg_jio_no_ussd, Toast.LENGTH_SHORT).show()
            }
            settings.ussdReportedNotWorking -> {
                Toast.makeText(context, R.string.testcfg_ussd_reported_issue, Toast.LENGTH_SHORT).show()
                context.startActivity(Intent(context, ConnectivityCheckActivity::class.java))
            }
            !settings.ussdTestPassed -> {
                context.startActivity(Intent(context, ConnectivityCheckActivity::class.java))
            }
            else -> onAllowedToOpenScanner()
        }
    }

    fun launchQrScanner(context: Context) {
        context.startActivity(Intent(context, QrScannerActivity::class.java))
    }

    fun openPayContactOrSetup(
        context: Context,
        settings: UserSettings,
        onAllowedToOpenPayContact: () -> Unit
    ) {
        if (!settings.isPayContactUnlocked) {
            context.startActivity(Intent(context, ConnectivityCheckActivity::class.java))
        } else {
            onAllowedToOpenPayContact()
        }
    }

    fun launchPayContact(context: Context) {
        context.startActivity(Intent(context, PayContactScreen::class.java))
    }

    fun launchRechargePortal(context: Context) {
        context.startActivity(Intent(context, RechargeWebScreen::class.java))
    }

    fun launchFaq(context: Context) {
        context.startActivity(Intent(context, FaqScreen::class.java))
    }
}
