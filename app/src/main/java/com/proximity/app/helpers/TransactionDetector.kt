package com.proximity.app.helpers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.proximity.app.payment.PaymentSessionManager
import com.proximity.app.payment.sms.SimpleTransaction
import com.proximity.app.payment.sms.SmsTransactionParser

class TransactionDetector private constructor(context: Context) {

    companion object {
        private const val TAG = "TransactionDetector"
        private const val PREF_NAME = "payment_operation"
        private const val KEY_ACTIVE = "is_active"
        private const val KEY_START_TIME = "start_time"
        private const val KEY_OPERATION_TYPE = "operation_type"
        private const val KEY_EXPECTED_AMOUNT = "expected_amount"
        private const val KEY_PHONE_NUMBER = "phone_number"
        private const val KEY_SESSION_TXN_ID = "session_txn_id"
        internal const val OPERATION_WINDOW_MILLIS =
            PaymentSessionManager.DEFAULT_VERIFICATION_DEADLINE_MS + 30_000L

        private const val SMS_CLAIM_WINDOW_MILLIS = 60 * 1000L // dedupe window across pipelines

        @Volatile
        private var instance: TransactionDetector? = null

        fun getInstance(context: Context): TransactionDetector {
            return instance ?: synchronized(this) {
                instance ?: TransactionDetector(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val recentSmsClaims = object : LinkedHashMap<String, Long>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > 64
    }

    @Synchronized
    fun tryClaimSms(body: String): Boolean {
        val key = SmsTransactionParser.claimKey(body).hashCode().toString()
        val now = System.currentTimeMillis()
        val lastClaim = recentSmsClaims[key]
        if (lastClaim != null && now - lastClaim < SMS_CLAIM_WINDOW_MILLIS) {
            return false
        }
        recentSmsClaims[key] = now
        return true
    }

    fun startOperation(
        operationType: String,
        expectedAmount: String? = null,
        phoneNumber: String? = null,
        sessionTxnId: String? = null
    ) {
        Log.d(TAG, "Starting operation: $operationType")

        prefs.edit().apply {
            putBoolean(KEY_ACTIVE, true)
            putLong(KEY_START_TIME, System.currentTimeMillis())
            putString(KEY_OPERATION_TYPE, operationType)
            if (expectedAmount != null) putString(KEY_EXPECTED_AMOUNT, expectedAmount) else remove(KEY_EXPECTED_AMOUNT)
            if (phoneNumber != null) putString(KEY_PHONE_NUMBER, phoneNumber) else remove(KEY_PHONE_NUMBER)
            if (sessionTxnId != null) putString(KEY_SESSION_TXN_ID, sessionTxnId) else remove(KEY_SESSION_TXN_ID)
            apply()
        }
    }

    fun stopOperation() {
        Log.d(TAG, "Stopping operation")

        prefs.edit().apply {
            clear()
            apply()
        }
    }

    fun shouldProcessSMS(): Boolean {
        val isActive = prefs.getBoolean(KEY_ACTIVE, false)
        if (!isActive) {
            return false
        }

        val startTime = prefs.getLong(KEY_START_TIME, 0)
        val elapsed = System.currentTimeMillis() - startTime

        if (elapsed > OPERATION_WINDOW_MILLIS) {
            Log.d(TAG, "Operation timed out after ${elapsed / 1000} seconds")
            stopOperation()
            return false
        }

        val operationType = prefs.getString(KEY_OPERATION_TYPE, null)
        if (operationType.isNullOrEmpty()) {
            stopOperation()
            return false
        }

        return true
    }

    fun getOperationType(): String? = prefs.getString(KEY_OPERATION_TYPE, null)
    fun getPhoneNumber(): String? = prefs.getString(KEY_PHONE_NUMBER, null)

    fun getSessionTxnId(): String? = prefs.getString(KEY_SESSION_TXN_ID, null)

    @Synchronized
    fun processSMS(sender: String, body: String): SimpleTransaction? {
        Log.d(TAG, "Processing SMS")
        if (!shouldProcessSMS()) {
            Log.d(TAG, "Operation no longer active, skipping")
            return null
        }

        val expectedAmount = prefs.getString(KEY_EXPECTED_AMOUNT, null)
        val transaction = SmsTransactionParser.parse(sender, body, expectedAmount)
        if (transaction == null) {
            Log.d(TAG, "SMS did not match a bank transaction")
            return null
        }
        Log.d(TAG, "Matched ${transaction.bankName} transaction: ${transaction.status}")
        if (transaction.transactionType != "CREDIT") {
            stopOperation()
        }

        return transaction
    }
}
