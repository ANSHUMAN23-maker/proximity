package com.proximity.app.receivers

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.proximity.app.ProximityApplication
import com.proximity.app.helpers.TransactionDetector
import com.proximity.app.payment.sms.SimpleTransaction
import com.proximity.app.repository.TransactionRepository
import com.proximity.app.ui.activities.PaymentResultActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SmsIngestionPipeline {

    private const val TAG = "SmsIngestionPipeline"

    suspend fun ingest(context: Context, detector: TransactionDetector, sender: String, body: String) {
        val windowTxnId = detector.getSessionTxnId()

        val transaction = detector.processSMS(sender, body)
        if (transaction == null) {
            Log.d(TAG, "Not a transaction SMS")
            return
        }

        val sessionManager = ProximityApplication.from(context)?.paymentSessionManager
        val sessionTxnId = sessionManager?.onSmsConfirmed(transaction)
        val recordTxnId = resolveOwnerTxnId(
            sessionTxnId = sessionTxnId,
            windowTxnId = windowTxnId,
            parsed = transaction
        ) { orphanId -> adoptOrphanedRow(context, orphanId, transaction) }

        if (isUnrelatedIncomingCredit(recordTxnId, transaction)) {
            Log.d(TAG, "Unrelated incoming credit during payment window - ignored")
            return
        }

        if (recordTxnId == null) {
            TransactionRepository.getInstance(context).saveTransaction(transaction)
            Log.d(TAG, "Transaction saved (no session, no adoptable row)")
        }

        withContext(Dispatchers.Main) {
            // Dismiss the USSD overlay (QR flow) and notify listening screens.
            LocalBroadcastManager.getInstance(context)
                .sendBroadcast(Intent("DISMISS_OVERLAY"))

            val resolvedTxnId = recordTxnId ?: transaction.transactionId
            val smsBroadcastIntent = Intent("com.proximity.app.SMS_RECEIVED").apply {
                putExtra("transaction_id", resolvedTxnId)
                putExtra("amount", transaction.amount)
                putExtra("status", transaction.status)
                putExtra("bank_name", transaction.bankName)
                putExtra("message", transaction.smsExcerpt)
                putExtra("timestamp", transaction.timestamp)
                putExtra("upi_id", transaction.upiId)
                putExtra("transaction_type", transaction.transactionType)
                putExtra("recipient_name", transaction.recipientName)
                putExtra("phone_number", transaction.phoneNumber)
            }
            LocalBroadcastManager.getInstance(context).sendBroadcast(smsBroadcastIntent)

            val successIntent = Intent(context, PaymentResultActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("transaction_id", resolvedTxnId)
                putExtra("amount", transaction.amount)
                putExtra("status", transaction.status)
                putExtra("bank_name", transaction.bankName)
                putExtra("message", transaction.smsExcerpt)
                putExtra("timestamp", transaction.timestamp)
                putExtra("upi_id", transaction.upiId)
                putExtra("transaction_type", transaction.transactionType)
                putExtra("recipient_name", transaction.recipientName)
                putExtra("phone_number", transaction.phoneNumber)
            }
            PaymentResultNotifier.notifyResult(context, successIntent)
            try {
                context.startActivity(successIntent)
            } catch (e: Exception) {
                Log.w(TAG, "Direct activity launch from background receiver failed: ${e.message}")
            }
        }
    }

    internal suspend fun resolveOwnerTxnId(
        sessionTxnId: String?,
        windowTxnId: String?,
        parsed: SimpleTransaction,
        adopt: suspend (String) -> Int
    ): String? = when {
        sessionTxnId != null -> sessionTxnId
        windowTxnId == null || parsed.transactionType == "CREDIT" -> null
        else -> {
            val updated = runCatching { adopt(windowTxnId) }
                .onFailure { Log.e(TAG, "Adopting orphaned row failed", it) }
                .getOrDefault(0)
            if (updated > 0) {
                Log.d(TAG, "Confirmation reattached to orphaned session row")
                windowTxnId
            } else {
                null
            }
        }
    }
    internal fun isUnrelatedIncomingCredit(recordTxnId: String?, parsed: SimpleTransaction): Boolean =
        recordTxnId == null && parsed.transactionType == "CREDIT"

    private suspend fun adoptOrphanedRow(
        context: Context,
        txnId: String,
        parsed: SimpleTransaction
    ): Int {
        return TransactionRepository.getInstance(context).confirmTransaction(
            transactionId = txnId,
            status = parsed.status,
            parsed = parsed,
            verifiedAt = System.currentTimeMillis()
        )
    }
}
