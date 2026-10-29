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
import kotlin.concurrent.thread

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

        private const val MIN_EVENT_INTERVAL_MS = 300L
        @Volatile private var lastEventTime: Long = 0
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val detectionLock = Any()
    @Volatile private var isDetecting = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "=== USSDAccessibilityService onServiceConnected ===")
        isServiceConnected = true

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_VIEW_FOCUSED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN or
                AccessibilityServiceInfo.FEEDBACK_HAPTIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        setServiceInfo(info)
        Log.d(TAG, "Service info configured with canRetrieveWindowContent=true")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        super.onUnbind(intent)
        Log.d(TAG, "=== USSDAccessibilityService onUnbind ===")
        isServiceConnected = false
        ussdPaymentActive = false
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "=== USSDAccessibilityService onDestroy ===")
        isServiceConnected = false
        ussdPaymentActive = false
    }

    override fun onInterrupt() {
        // Service interrupted - reset state
        Log.d(TAG, "=== USSDAccessibilityService onInterrupt ===")
        ussdPaymentActive = false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!ussdPaymentActive) {
            Log.d(TAG, "Event ignored: ussdPaymentActive=false")
            return
        }

        if (!isServiceConnected) {
            Log.d(TAG, "Event ignored: isServiceConnected=false")
            return
        }

        val now = System.currentTimeMillis()
        synchronized(detectionLock) {
            if (now - lastEventTime < MIN_EVENT_INTERVAL_MS) {
                Log.d(TAG, "Event throttled: ${now - lastEventTime}ms < ${MIN_EVENT_INTERVAL_MS}ms")
                return
            }
            lastEventTime = now
        }

        val eventType = event.eventType
        val eventTypeName = when (eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW_STATE_CHANGED"
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> "VIEW_FOCUSED"
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "VIEW_TEXT_CHANGED"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "WINDOW_CONTENT_CHANGED"
            else -> "UNKNOWN($eventType)"
        }
        Log.d(TAG, "AccessibilityEvent received: $eventTypeName")

        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_VIEW_FOCUSED &&
            eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        if (isDetecting) {
            Log.d(TAG, "Detection already in progress, skipping")
            return
        }

        isDetecting = true
        // Capture text immediately from event source on main thread before recycling
        val eventText = event.source?.text?.toString() ?: ""
        Log.d(TAG, "Event source text captured immediately: $eventText")

        // Process after short delay to allow dialog to fully render
        mainHandler.postDelayed({
            try {
                processAccessibilityEvent(event)
            } finally {
                isDetecting = false
            }
        }, 500L)
    }

    private fun processAccessibilityEvent(event: AccessibilityEvent) {
        Log.d(TAG, "=== Processing accessibility event ===")

        val root = getRootInActiveWindow() ?: run {
            Log.d(TAG, "getRootInActiveWindow() returned null")
            return
        }

        Log.d(TAG, "Root node obtained: class=${root.className}, text=${root.text}, desc=${root.contentDescription}")

        // Collect text from the USSD dialog window
        val dialogText = collectDialogText(root)
        root.recycle()

        if (dialogText.isBlank()) {
            Log.d(TAG, "Dialog text is blank, skipping")
            return
        }

        Log.d(TAG, "=== USSD Dialog Text Detected ===")
        Log.d(TAG, "$dialogText")
        Log.d(TAG, "=== End of Dialog Text ===")

        // Parse for payment result
        val result = parsePaymentResult(dialogText)
        Log.d(TAG, "Parse result: status=${result.status}, amount=${result.amount}, ref=${result.reference}")

        if (result.status != UssdStatus.UNKNOWN) {
            Log.d(TAG, "USSD payment result detected: ${result.status}, amount=${result.amount}, ref=${result.reference}")
            ussdCallback?.invoke(result)
        } else {
            Log.d(TAG, "USSD dialog text did not match any payment result pattern")
        }
    }

    /**
     * Recursively collects all text from the accessibility node tree.
     */
    private fun collectDialogText(node: AccessibilityNodeInfo): String {
        val texts = mutableListOf<String>()
        collectTextRecursive(node, texts)
        return texts.joinToString(" | ")
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

        // Success indicators - comprehensive list for Indian USSD responses
        val successKeywords = listOf(
            "success", "successful", "payment successful", "transaction successful",
            "payment success", "txn successful", "transaction status: successful",
            "debited", "credited", "payment completed", "transaction completed",
            "amount debited", "amount credited", "txn status: success",
            "payment processed", "transaction processed",
            "succeeded", "txn succeeded", "payment succeeded", "transaction succeeded",
            "₹ debited", "rs debited", "inr debited",
            "transaction complete", "payment complete"
        )

        // Failure indicators
        val failedKeywords = listOf(
            "failed", "failure", "payment failed", "transaction failed",
            "declined", "rejected", "insufficient", "error", "cancelled", "canceled",
            "txn failed", "transaction failed", "payment failed",
            "insufficient balance", "insufficient funds",
            "invalid pin", "wrong pin", "incorrect pin",
            "transaction declined", "payment declined",
            "txn declined", "txn rejected",
            "network error", "server error", "technical error",
            "time out", "timeout", "timed out",
            "user cancelled", "cancelled by user"
        )

        val isSuccess = successKeywords.any { lower.contains(it) }
        val isFailed = failedKeywords.any { lower.contains(it) }

        val status = when {
            isSuccess && !isFailed -> UssdStatus.SUCCESS
            isFailed && !isSuccess -> UssdStatus.FAILED
            isSuccess && isFailed -> UssdStatus.UNKNOWN // ambiguous
            else -> UssdStatus.UNKNOWN
        }

        // Extract amount (₹ symbol or currency patterns)
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
        // Multiple patterns for Indian USSD formats
        val patterns = listOf(
            """(?:₹|Rs\.?|INR|rupees?)\s*([\d,]+(?:\.\d{1,2})?)""",
            """(?:amount|amt|rs|₹)\s*:?\s*([\d,]+(?:\.\d{1,2})?)""",
            """debited\s+(?:of\s+)?(?:₹|rs\.?|inr)?\s*([\d,]+(?:\.\d{1,2})?)""",
            """([\d,]+(?:\.\d{1,2})?)\s*(?:debited|credited|deducted)"""
        )
        for (pattern in patterns) {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val match = regex.find(text)
            if (match != null) {
                val value = match.groupValues.getOrNull(1)
                if (value != null && value.isNotBlank()) {
                    return value
                }
            }
        }
        return null
    }

    /**
     * Extracts transaction reference from text.
     * Uses word boundaries to avoid matching English words like "successful", "pending",
     * "completed" as transaction references.
     */
    private fun extractReference(text: String): String? {
        // Words that should NOT be treated as transaction references even if they match
        val excludedWords = setOf(
            "successful", "success", "failure", "failed", "pending",
            "completed", "processed", "declined", "rejected", "cancelled",
            "canceled", "insufficient", "error", "timeout", "transaction"
        )

        val patterns = listOf(
            """\b(?:ref(?:erence)?|ref\s*no|reference\s*no|txn|transaction|UTR|UPI)\s+([A-Za-z0-9]{6,})""",
            """\bUTR\s+([A-Za-z0-9]{10,})""",
            """\b(?:ref|txn)\s*id\s+([A-Za-z0-9]{6,})"""
        )
        for (pattern in patterns) {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val match = regex.find(text)
            if (match != null) {
                val value = match.groupValues.getOrNull(1)
                if (value != null && value.isNotBlank() && value.length >= 6
                    && !excludedWords.contains(value.lowercase())) {
                    return value
                }
            }
        }
        return null
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
        Log.d(TAG, "USSD callback set: ${callback != null}")
    }

    /**
     * Checks if the accessibility service is enabled and connected.
     */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val services = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val enabled = services.any { it.id == "${context.packageName}/.accessibility.UssdAccessibilityService" }
        Log.d(TAG, "isAccessibilityEnabled: $enabled, services found: ${services.size}")
        return enabled
    }
}
