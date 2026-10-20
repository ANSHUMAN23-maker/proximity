package com.proximity.app.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.proximity.app.ProximityApplication
import com.proximity.app.data.TransactionStatus
import com.proximity.app.payment.sms.SimpleTransaction
import com.proximity.app.receivers.PaymentResultNotifier
import com.proximity.app.repository.TransactionRepository
import com.proximity.app.states.PaymentState
import com.proximity.app.ui.activities.PaymentResultActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class UssdAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "UssdAccessibilityService"
        private const val DEBOUNCE_WINDOW_MS = 10000L

        // Only process telephony/dialer/system packages that actually show USSD dialogs.
        // This prevents reading Proximity's own PaymentResultActivity text and causing loops.
        private val ALLOWED_PACKAGES = setOf(
            "com.android.phone",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.samsung.android.incallui",
            "com.miui.telephony",
            "com.oppo.engineermode",
            "com.vivo.phone",
            "com.transsion.dialer",
            "com.lge.dialer",
            "com.huawei.dialer",
            "android",
            "com.android.systemui",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller"
        )

        @Volatile private var lastProcessedText: String? = null
        @Volatile private var lastProcessedTimestamp: Long = 0L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkgName = event.packageName?.toString() ?: ""
        if (pkgName.isBlank() || pkgName !in ALLOWED_PACKAGES) {
            return
        }

        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        val rootNode = rootInActiveWindow ?: event.source ?: return
        val extractedText = StringBuilder()
        traverseNodes(rootNode, extractedText)
        val fullText = extractedText.toString().trim()

        if (fullText.isEmpty()) return

        val parseResult = UssdTransactionParser.parse(fullText)
        if (!parseResult.isSuccess) return

        val now = System.currentTimeMillis()
        if (lastProcessedText == fullText && (now - lastProcessedTimestamp) < DEBOUNCE_WINDOW_MS) {
            Log.d(TAG, "Ignoring duplicate USSD event within debounce window")
            return
        }

        lastProcessedText = fullText
        lastProcessedTimestamp = now

        Log.d(TAG, "USSD Payment Success Detected from [$pkgName]: $fullText")
        handleSuccessTransaction(parseResult)
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    private fun traverseNodes(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null) return
        val text = node.text
        if (!text.isNullOrBlank()) {
            sb.append(text).append(" ")
        }
        val count = node.childCount
        for (i in 0 until count) {
            traverseNodes(node.getChild(i), sb)
        }
    }

    private fun handleSuccessTransaction(parseResult: ParsedUssdTransaction) {
        serviceScope.launch {
            val app = application as? ProximityApplication
            val sessionManager = app?.paymentSessionManager
            val currentState = sessionManager?.paymentState?.value

            var activePhone = ""
            var activeAmount = ""
            var activeUpiId: String? = null
            var activeTxnId: String? = null

            if (currentState is PaymentState.InProgress) {
                activePhone = currentState.phoneNumber
                activeAmount = currentState.amount
                activeUpiId = currentState.upiId
                activeTxnId = currentState.transactionId
            } else if (currentState is PaymentState.WaitingForVerification) {
                activePhone = currentState.phoneNumber
                activeAmount = currentState.amount
                activeUpiId = currentState.upiId
                activeTxnId = currentState.transactionId
            } else if (currentState is PaymentState.Initiating) {
                activePhone = currentState.phoneNumber
                activeAmount = currentState.amount
                activeUpiId = currentState.upiId
                activeTxnId = currentState.transactionId
            }

            val finalAmount = parseResult.amount ?: activeAmount.ifBlank { "0" }
            val finalRecipient = parseResult.recipient ?: activeUpiId ?: activePhone
            val finalRefId = parseResult.refId ?: activeTxnId ?: ("USSD_" + System.currentTimeMillis())

            val transaction = SimpleTransaction(
                transactionId = finalRefId,
                amount = finalAmount,
                status = TransactionStatus.SUCCESS,
                bankName = "USSD (*99#)",
                smsExcerpt = parseResult.rawText,
                timestamp = System.currentTimeMillis(),
                transactionType = "DEBIT",
                phoneNumber = activePhone,
                upiId = activeUpiId,
                recipientName = finalRecipient
            )

            sessionManager?.onSmsConfirmed(transaction)
            runCatching {
                TransactionRepository.getInstance(applicationContext).saveTransaction(transaction)
            }

            LocalBroadcastManager.getInstance(applicationContext)
                .sendBroadcast(Intent("DISMISS_OVERLAY"))
            LocalBroadcastManager.getInstance(applicationContext)
                .sendBroadcast(Intent("com.proximity.app.SMS_RECEIVED"))

            val successIntent = Intent(applicationContext, PaymentResultActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("transaction_id", finalRefId)
                putExtra("amount", finalAmount)
                putExtra("status", TransactionStatus.SUCCESS)
                putExtra("bank_name", "USSD (*99#)")
                putExtra("message", parseResult.rawText)
                putExtra("timestamp", System.currentTimeMillis())
                putExtra("upi_id", activeUpiId)
                putExtra("transaction_type", "DEBIT")
                putExtra("recipient_name", finalRecipient)
                putExtra("phone_number", activePhone)
            }

            PaymentResultNotifier.notifyResult(applicationContext, successIntent)
            startActivity(successIntent)
        }
    }
}
