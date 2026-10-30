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

/**
 * Result of USSD dialog accessibility detection.
 */
data class UssdAccessibilityResult(
    val status: UssdStatus,
    val amount: String? = null,
    val reference: String? = null,
    val recipientName: String? = null,
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
 * changes, view focus events, and window content changes, traversing the accessibility node
 * hierarchy to find USSD dialog content, and detects payment success/failure indicators.
 *
 * The user must manually enable this service in Android Settings before it can function.
 */
class UssdAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "UssdAccessibilityService"

        /** Whether an USSD payment session is currently active. */
        @Volatile
        var ussdPaymentActive: Boolean = false

        /** The most recently detected USSD payment result during this session. */
        @Volatile
        var latestResult: UssdAccessibilityResult? = null

        /** Callback invoked when accessibility detects a payment result in the USSD dialog. */
        @Volatile
        var ussdCallback: ((UssdAccessibilityResult) -> Unit)? = null

        /** Whether the accessibility service is currently connected and listening. */
        @Volatile
        var isServiceConnected: Boolean = false

        private const val MIN_EVENT_INTERVAL_MS = 150L
        @Volatile private var lastEventTime: Long = 0

        /** Resets transient detection state for a new payment session. */
        fun reset() {
            latestResult = null
            ussdCallback = null
            lastEventTime = 0
            Log.d(TAG, "UssdAccessibilityService state reset")
        }

        /**
         * Checks if the accessibility service is enabled and connected.
         * Queries both AccessibilityManager and Settings.Secure for broad OEM compatibility.
         */
        fun isAccessibilityEnabled(context: Context): Boolean {
            return try {
                val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
                val services = manager?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK) ?: emptyList()
                val enabledInManager = services.any { service ->
                    service.id.contains("UssdAccessibilityService", ignoreCase = true)
                }
                if (enabledInManager) return true

                val enabledServicesSetting = android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: ""
                val enabledInSettings = enabledServicesSetting.contains("UssdAccessibilityService", ignoreCase = true)
                Log.d(TAG, "isAccessibilityEnabled (settings fallback): $enabledInSettings")
                enabledInSettings
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check accessibility service status", e)
                false
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "=== USSDAccessibilityService onServiceConnected ===")
        isServiceConnected = true

        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_VIEW_FOCUSED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN or
                AccessibilityServiceInfo.FEEDBACK_HAPTIC or
                AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        serviceInfo = info
        Log.d(TAG, "Service info configured with canRetrieveWindowContent=true & interactive windows")
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
        Log.d(TAG, "=== USSDAccessibilityService onInterrupt ===")
        ussdPaymentActive = false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!ussdPaymentActive) {
            return
        }

        // Event delivered by system indicates service is active
        isServiceConnected = true

        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_VIEW_FOCUSED &&
            eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        val now = System.currentTimeMillis()
        // Do not throttle window state changes or if no success detected yet
        if (latestResult?.status == UssdStatus.SUCCESS && now - lastEventTime < MIN_EVENT_INTERVAL_MS) {
            return
        }
        lastEventTime = now

        // Collect all text from this event immediately while references are valid
        val texts = mutableListOf<String>()

        // 1. Text directly on the event
        for (item in event.text) {
            if (!item.isNullOrBlank()) {
                texts.add(item.toString())
            }
        }

        // 2. Event source node
        try {
            event.source?.let { source ->
                collectTextRecursive(source, texts)
                source.recycle()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Could not inspect event source", e)
        }

        // 3. Active window root node
        try {
            rootInActiveWindow?.let { root ->
                collectTextRecursive(root, texts)
                root.recycle()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Could not inspect rootInActiveWindow", e)
        }

        // 4. All interactive windows (retrieves system dialogs/telephony UI)
        try {
            for (window in windows) {
                window.root?.let { windowRoot ->
                    collectTextRecursive(windowRoot, texts)
                    windowRoot.recycle()
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Could not inspect windows", e)
        }

        val dialogText = texts.filter { it.isNotBlank() }.distinct().joinToString(" | ")
        if (dialogText.isBlank()) {
            return
        }

        Log.d(TAG, "USSD text captured: $dialogText")

        val result = parsePaymentResult(dialogText)
        if (result.status != UssdStatus.UNKNOWN) {
            val current = latestResult
            // Once a payment has succeeded, never overwrite it with non-success exit/close events
            if (current != null && current.status == UssdStatus.SUCCESS && result.status != UssdStatus.SUCCESS) {
                Log.d(TAG, "Ignoring subsequent non-success status (${result.status}) because SUCCESS is already confirmed")
                return
            }

            // Merge detected fields so amount, reference, and recipient name are never lost
            val merged = if (current != null && current.status == UssdStatus.SUCCESS) {
                current.copy(
                    amount = result.amount ?: current.amount,
                    reference = result.reference ?: current.reference,
                    recipientName = result.recipientName ?: current.recipientName,
                    rawText = result.rawText ?: current.rawText
                )
            } else {
                result
            }

            Log.d(
                TAG,
                "USSD payment result detected: ${merged.status}, amount=${merged.amount}, " +
                    "ref=${merged.reference}, recipient=${merged.recipientName}"
            )
            latestResult = merged
            ussdCallback?.invoke(merged)
        }
    }

    /**
     * Recursively collects all text from the accessibility node tree.
     */
    private fun collectTextRecursive(node: AccessibilityNodeInfo?, texts: MutableList<String>) {
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
    internal fun parsePaymentResult(text: String): UssdAccessibilityResult {
        val lower = text.lowercase()

        // Success indicators - comprehensive list for Indian USSD responses
        val successKeywords = listOf(
            "payment successful", "transaction successful", "payment success",
            "txn successful", "transaction status: successful", "payment completed",
            "transaction completed", "amount debited", "amount credited", "txn status: success",
            "payment processed", "transaction processed", "txn succeeded", "payment succeeded",
            "transaction succeeded", "₹ debited", "rs debited", "inr debited",
            "transaction complete", "payment complete", "successful", "success",
            "succeeded", "debited", "credited"
        )

        // Failure indicators
        val failedKeywords = listOf(
            "payment failed", "transaction failed", "txn failed", "insufficient balance",
            "insufficient funds", "invalid pin", "wrong pin", "incorrect pin",
            "transaction declined", "payment declined", "txn declined", "txn rejected",
            "network error", "server error", "technical error", "user cancelled",
            "cancelled by user", "failed", "failure", "declined", "rejected",
            "insufficient", "cancelled", "canceled", "timeout", "timed out"
        )

        val isSuccess = successKeywords.any { lower.contains(it) }
        val isFailed = failedKeywords.any { lower.contains(it) }

        val status = when {
            isSuccess && !isFailed -> UssdStatus.SUCCESS
            isFailed && !isSuccess -> UssdStatus.FAILED
            isSuccess && isFailed -> {
                // If text has both "success" and "cancel" (e.g. Cancel button in dialog),
                // check if the main message is explicitly a successful payment.
                if (lower.contains("successful") || lower.contains("payment success") || lower.contains("debited")) {
                    UssdStatus.SUCCESS
                } else {
                    UssdStatus.UNKNOWN
                }
            }
            else -> UssdStatus.UNKNOWN
        }

        // Extract amount (₹ symbol or currency patterns)
        val amount = extractAmount(text)

        // Extract reference number
        val reference = extractReference(text)

        // Extract recipient name
        val recipientName = extractRecipientName(text)

        return UssdAccessibilityResult(
            status = status,
            amount = amount,
            reference = reference,
            recipientName = recipientName,
            rawText = text
        )
    }

    /**
     * Extracts amount from text using ₹ symbol or currency patterns.
     */
    private fun extractAmount(text: String): String? {
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
     * Uses findAll and exclusions to avoid matching English words like "successful", "pending",
     * "completed" as transaction references.
     */
    private fun extractReference(text: String): String? {
        val excludedWords = setOf(
            "successful", "success", "failure", "failed", "pending",
            "completed", "processed", "declined", "rejected", "cancelled",
            "canceled", "insufficient", "error", "timeout", "transaction"
        )

        val patterns = listOf(
            """\b(?:ref[\s._-]*no\.?|reference[\s._-]*no\.?|ref[\s._-]*id|reference[\s._-]*id|txn[\s._-]*id|transaction[\s._-]*id|UPI[\s._-]*ref(?:[\s._-]*no)?|rrn|rr[\s._-]*no\.?)\s*[:.#-]?\s*([A-Za-z0-9]{6,})""",
            """\b(?:ref(?:erence)?|txn|UTR|UPI)\s*[:.#-]?\s*([A-Za-z0-9]{6,})""",
            """\bUTR\s*[:.#-]?\s*([A-Za-z0-9]{10,})""",
            """\btransaction\s*[:.#-]\s*([A-Za-z0-9]{6,})"""
        )
        for (pattern in patterns) {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val matches = regex.findAll(text)
            for (match in matches) {
                val value = match.groupValues.getOrNull(1)?.trim()
                if (value != null && value.isNotBlank() && value.length >= 6 &&
                    !excludedWords.contains(value.lowercase())
                ) {
                    return value
                }
            }
        }
        return null
    }

    /**
     * Extracts recipient / beneficiary name from USSD text.
     */
    private fun extractRecipientName(text: String): String? {
        val excluded = setOf(
            "successful", "success", "failed", "failure", "pending", "completed",
            "debited", "credited", "payment", "transaction", "amount", "account",
            "bank", "reference", "ref", "utr", "upi", "rupees", "rs", "inr",
            "is", "was", "has", "been", "via", "for", "of", "with", "status"
        )
        val patterns = listOf(
            """(?:to|paid to|sent to|credited to|debited to|transferred to)\s+([A-Za-z0-9@._\s]{2,30}?)(?=\s*(?:is\s+|was\s+|has\s+|for\s+|of\s+|via\s+|successful|succeeded|success|ref|ref\.?|txn|transaction|utr|\.|\$|,))""",
            """(?:to|paid to|sent to|credited to|debited to|transferred to)\s+([A-Za-z0-9@._\s]{2,25})"""
        )
        for (pattern in patterns) {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val match = regex.find(text)
            if (match != null) {
                val candidate = match.groupValues.getOrNull(1)?.trim() ?: continue
                val words = candidate.split(Regex("""\s+""")).takeWhile { word ->
                    !excluded.contains(word.lowercase()) && !word.all { it.isDigit() }
                }
                val cleaned = words.joinToString(" ").trimEnd { it == '.' || it == ',' || it == '-' || it == ':' }
                if (cleaned.length >= 2 && !excluded.contains(cleaned.lowercase())) {
                    return cleaned
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
        if (!active) {
            latestResult = null
        }
        Log.d(TAG, "USSD payment active set to: $active")
    }

    /**
     * Sets the callback for accessibility-detected payment results.
     */
    fun setUssdCallback(callback: ((UssdAccessibilityResult) -> Unit)?) {
        ussdCallback = callback
        Log.d(TAG, "USSD callback set: ${callback != null}")
    }
}
