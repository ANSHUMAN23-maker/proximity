// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.proximity.app.ProximityApplication

class PaymentAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "PaymentAccessibility"
        private const val COOLDOWN_MS = 1500L

        fun isEnabled(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val serviceComponent = "${context.packageName}/${PaymentAccessibilityService::class.java.canonicalName}"
            return enabledServices.split(":").any { component ->
                component.contains(serviceComponent, ignoreCase = true) ||
                    component.contains("${context.packageName}/.accessibility.PaymentAccessibilityService", ignoreCase = true)
            }
        }
    }

    @Volatile private var lastProcessedTime = 0L
    @Volatile private var lastProcessedText = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "PaymentAccessibilityService connected")

        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.notificationTimeout = 100
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val app = ProximityApplication.from(this) ?: return
        val sessionManager = app.paymentSessionManager
        val paymentState = sessionManager.paymentState.value

        // ONLY read and parse dialog text when USSD payment is in progress
        if (!paymentState.isInProgress()) return

        val now = System.currentTimeMillis()
        if (now - lastProcessedTime < COOLDOWN_MS) return

        val rootNode = rootInActiveWindow ?: event.source ?: return
        val collectedText = StringBuilder()
        collectText(rootNode, collectedText)
        val fullText = collectedText.toString().trim()

        if (fullText.isBlank() || fullText == lastProcessedText) return

        if (!UssdDialogParser.isUssdDialogText(fullText)) return

        val expectedPhone = paymentState.getPhoneNumberValue()
        val expectedAmount = paymentState.getAmountValue()

        val parsedTransaction = UssdDialogParser.parse(
            rawText = fullText,
            fallbackPhone = expectedPhone,
            fallbackAmount = expectedAmount
        ) ?: return

        Log.d(
            TAG,
            "Parsed USSD dialog text: status=${parsedTransaction.status}, ref=${parsedTransaction.transactionId}, amount=${parsedTransaction.amount}, recipient=${parsedTransaction.recipientName}"
        )

        lastProcessedTime = now
        lastProcessedText = fullText

        // Confirm payment in session manager and save scraped USSD transaction
        sessionManager.onUssdScraped(parsedTransaction)
        Log.d(TAG, "USSD transaction scraped and saved in session manager. Waiting for user to tap 'I've Completed It'.")
    }

    private fun collectText(node: AccessibilityNodeInfo?, builder: StringBuilder) {
        if (node == null) return
        val nodeText = node.text
        if (!nodeText.isNullOrBlank()) {
            builder.append(nodeText).append(" ")
        }
        for (i in 0 until node.childCount) {
            collectText(node.getChild(i), builder)
        }
    }

    override fun onInterrupt() {}
}
