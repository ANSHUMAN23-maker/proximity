// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.proximity.app.AppCore
import com.proximity.app.ui.payment.PaymentResultScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SmsMonitorReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val app = context.applicationContext as? AppCore ?: return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val sender = messages.firstOrNull()?.displayOriginatingAddress
        val combinedBody = messages.joinToString(separator = "") { it.displayMessageBody.orEmpty() }
        if (combinedBody.isBlank()) return

        val pendingResult = goAsync()
        app.services.appScope.launch(Dispatchers.IO) {
            try {
                // Stay safely inside Android's 10s BroadcastReceiver execution budget.
                val updatedRecord = withTimeoutOrNull(8_500L) {
                    app.services.paymentLifecycleManager.ingestBankSms(sender, combinedBody)
                }
                if (updatedRecord != null && app.isInForeground) {
                    try {
                        val launchIntent = Intent(context, PaymentResultScreen::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra(PaymentResultScreen.EXTRA_PAYMENT_RECORD, updatedRecord)
                        }
                        context.startActivity(launchIntent)
                    } catch (_: Exception) {
                        // Notification already posted by PaymentAlertPublisher
                    }
                }
            } catch (_: Throwable) {
                // Never crash broadcast receiver on malformed SMS payloads or database I/O faults.
            } finally {
                pendingResult.finish()
            }
        }
    }
}
