package com.proximity.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.proximity.app.helpers.TransactionDetector
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SimpleSMSReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SimpleSMSReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val sender = messages[0]?.originatingAddress ?: return
        val body = messages.filterNotNull().joinToString("") { it.messageBody ?: "" }
        if (body.isBlank()) return

        Log.d(TAG, "SMS received")

        val detector = TransactionDetector.getInstance(context)
        if (!detector.shouldProcessSMS()) {
            Log.d(TAG, "No active payment operation, ignoring SMS")
            return
        }

        if (!detector.tryClaimSms(body)) {
            Log.d(TAG, "SMS already claimed")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val completed = withTimeoutOrNull(8_000.milliseconds) {
                    SmsIngestionPipeline.ingest(context, detector, sender, body)
                }
                if (completed == null) {
                    Log.w(TAG, "SMS processing timed out before completion")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
