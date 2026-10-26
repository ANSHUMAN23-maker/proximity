// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Result of USSD dialog accessibility detection.
 */
data class UssdAccessibilityResult(
    val status: UssdStatus,
    val amount: String? = null,
    val reference: String? = null,
    val rawText: String? = null,
    val detectedAt: Long = System.currentTimeMillis()
)

enum class UssdStatus {
    SUCCESS,
    FAILED,
    CANCELLED,
    UNKNOWN
}

/**
 * AccessibilityService that reads the USSD system dialog box during an active USSD payment.
 *
 * The service is only active when [ussdPaymentActive] is true. It listens for window state
 * changes and view focus events, traverses the accessibility node hierarchy to find USSD
 * dialog content, and detects payment success/failure indicators.
 *
 * The user must manually enable this service in Android Settings before it can function.
 */
class UssdAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "UssdAccessibilityService"

        /** Whether an USSD payment session is currently active. */
        @Volatile
        var ussdPaymentActive: Boolean = false

        /** Callback invoked when accessibility detects a payment result in the USSD dialog. */
        @Volatile
        var ussdCallback: ((UssdAccessibilityResult) -> Unit)? = null

        /** Whether the accessibility service is currently connected and listening. */
        @Volatile
        var isServiceConnected: Boolean = false

        private const val MIN_EVENT_INTERVAL_MS = 500L
        @Volatile private var lastEventTime: Long = 0
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val detectionLock = Any()
    @Volatile private var isDetecting = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "USSD AccessibilityService connected")
        isServiceConnected = true

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_VIEW_FOCUSED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN or
                AccessibilityServiceInfo.FEEDBACK_HAPTIC
            notificationTimeout = 100
            // canRetrieveWindowContent is set via XML meta-data, not here
        }
        setServiceInfo(info)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        super.onUnbind(intent)
        Log.d(TAG, "USSD AccessibilityService unbound")
        isServiceConnected = false
        ussdPaymentActive = false
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "USSD AccessibilityService destroyed")
        isServiceConnected = false
        ussdPaymentActive = false
    }

    override fun onInterrupt() {
        // Service interrupted - reset state
        Log.d(TAG, "USSD AccessibilityService interrupted")
        ussdPaymentActive = false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!ussdPaymentActive) {
            return
        }

        val now = System.currentTimeMillis()
        synchronized(detectionLock) {
            if (now - lastEventTime < MIN_EVENT_INTERVAL_MS) {
                return
            }
            lastEventTime = now
        }

        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_VIEW_FOCUSED &&
            eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        ) {
            return
        }

        if (isDetecting) {
            return
        }

        isDetecting = true
        mainHandler.post {
            try {
                processAccessibilityEvent(event)
            } finally {
                isDetecting = false
            }
        }
    }

    private fun processAccessibilityEvent(event: AccessibilityEvent) {
        val root = getRootInActiveWindow() ?: return

        // Collect text from the USSD dialog window
        val dialogText = collectDialogText(root)
        if (dialogText.isBlank()) {
            return
        }

        Log.d(TAG, "USSD dialog text detected: $dialogText")

        // Parse for payment result
        val result = parsePaymentResult(dialogText)
        if (result.status != UssdStatus.UNKNOWN) {
            Log.d(TAG, "USSD payment result detected: ${result.status}, amount=${result.amount}, ref=${result.reference}")
            ussdCallback?.invoke(result)
        }
    }

    /**
     * Recursively collects all text from the accessibility node tree.
     */
    private fun collectDialogText(node: AccessibilityNodeInfo): String {
        val texts = mutableListOf<String>()
        collectTextRecursive(node, texts)
        return texts.joinToString(" ")
    }

    private fun collectTextRecursive(node: AccessibilityNodeInfo, texts: MutableList<String>) {
        if (node == null) return

        val text = node.text
        if (text != null && text.isNotBlank()) {
            texts.add(text.toString())
        }

        val contentDesc = node.contentDescription
        if (contentDesc != null && contentDesc.isNotBlank()) {
            texts.add(contentDesc.toString())
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectTextRecursive(child, texts)
                child.recycle()
            }
        }
    }

    /**
     * Parses collected dialog text to determine payment status and extract details.
     */
    private fun parsePaymentResult(text: String): UssdAccessibilityResult {
        val lower = text.lowercase()

        // Success indicators
        val successKeywords = listOf(
            "success", "successful", "payment successful", "transaction successful",
            "payment success", "txn successful", "debited", "credited",
            "payment completed", "transaction completed"
        )

        // Failure indicators
        val failedKeywords = listOf(
            "failed", "failure", "payment failed", "transaction failed",
            "declined", "rejected", "insufficient", "error", "cancelled", "canceled"
        )

        val isSuccess = successKeywords.any { lower.contains(it) }
        val isFailed = failedKeywords.any { lower.contains(it) }

        val status = when {
            isSuccess && !isFailed -> UssdStatus.SUCCESS
            isFailed && !isSuccess -> UssdStatus.FAILED
            isSuccess && isFailed -> UssdStatus.UNKNOWN // ambiguous
            else -> UssdStatus.UNKNOWN
        }

        // Extract amount (₹ symbol or numeric patterns)
        val amount = extractAmount(text)

        // Extract reference number
        val reference = extractReference(text)

        return UssdAccessibilityResult(
            status = status,
            amount = amount,
            reference = reference,
            rawText = text
        )
    }

    /**
     * Extracts amount from text using ₹ symbol or currency patterns.
     */
    private fun extractAmount(text: String): String? {
        val amountRegex = Regex("""(?:₹|Rs\.?|INR|rupees?)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        val match = amountRegex.find(text)
        return match?.groupValues?.getOrNull(1)
    }

    /**
     * Extracts transaction reference from text.
     */
    private fun extractReference(text: String): String? {
        val refRegex = Regex("""(?:ref|reference|txn|transaction|UTR|UPI|ID)[:\s#]*([A-Za-z0-9]{6,})""", RegexOption.IGNORE_CASE)
        val match = refRegex.find(text)
        return match?.groupValues?.getOrNull(1)
    }

    /**
     * Enables or disables USSD payment detection mode.
     * Should be called before starting a USSD payment and after it ends.
     */
    fun setPaymentActive(active: Boolean) {
        ussdPaymentActive = active
        Log.d(TAG, "USSD payment active set to: $active")
    }

    /**
     * Sets the callback for accessibility-detected payment results.
     */
    fun setUssdCallback(callback: ((UssdAccessibilityResult) -> Unit)?) {
        ussdCallback = callback
    }

    /**
     * Checks if the accessibility service is enabled and connected.
     */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val services = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return services.any { it.id == "${context.packageName}/.accessibility.UssdAccessibilityService" }
    }
}
