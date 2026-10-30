package com.proximity.app.payment

import android.util.Log
import com.proximity.app.accessibility.UssdAccessibilityService
import com.proximity.app.data.Transaction
import com.proximity.app.data.TransactionSource
import com.proximity.app.data.TransactionStatus
import com.proximity.app.payment.sms.SimpleTransaction
import com.proximity.app.states.PaymentState
import com.proximity.app.states.TimeoutType
import com.proximity.app.telephony.CallSessionEvent
import com.proximity.app.telephony.CallStateSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface PaymentTransactionStore {
    suspend fun insertPending(transaction: Transaction)

    suspend fun transitionStatus(transactionId: String, expectedStatus: String, newStatus: String): Int

    suspend fun confirmTransaction(
        transactionId: String,
        status: String,
        parsed: SimpleTransaction,
        verifiedAt: Long
    ): Int

    suspend fun deleteStalePending(now: Long): Int

    suspend fun updateVerifiedPayment(
        transactionId: String,
        status: String,
        amount: String,
        bankRef: String?,
        recipientName: String? = null,
        verifiedAt: Long
    ): Int

    suspend fun deletePending(transactionId: String): Int
}

class PaymentSessionManager(
    private val store: PaymentTransactionStore,
    private val coordinator: CallStateSource,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val verificationDeadlineMs: Long = DEFAULT_VERIFICATION_DEADLINE_MS,
    private val minRealCallDurationMs: Long = DEFAULT_MIN_REAL_CALL_DURATION_MS
) {

    companion object {
        private const val TAG = "PaymentSessionManager"
        const val DEFAULT_VERIFICATION_DEADLINE_MS = 10 * 60 * 1000L // 10 minutes
        const val DEFAULT_MIN_REAL_CALL_DURATION_MS = 5_000L
        private const val COORDINATOR_TAG = "payment-session"
        private const val GRACE_PERIOD_MS = 5_000L
    }

    private val _paymentState = MutableStateFlow<PaymentState>(PaymentState.Idle)
    val paymentState: StateFlow<PaymentState> = _paymentState.asStateFlow()

    private val sessionLock = Any()

    @Volatile private var collectJob: Job? = null

    @Volatile private var watchdogJob: Job? = null
    @Volatile private var gracePeriodJob: Job? = null

    @Volatile private var pendingInsertJob: Job? = null
    private var coordinatorAcquired = false

    fun begin(
        phoneNumber: String,
        amount: String,
        upiId: String? = null,
        source: String = TransactionSource.MANUAL
    ): String {
        // Activate USSD accessibility payment detection for any new payment session
        UssdAccessibilityService.ussdPaymentActive = true
        Log.d(TAG, "Payment session begun - USSD accessibility detection activated")

        val initiating: PaymentState.Initiating
        val supersededTxnId: String?
        synchronized(sessionLock) {
            val previous = _paymentState.value
            supersededTxnId = if (previous.isInProgress()) {
                Log.w(TAG, "begin() supersedes the session still in flight")
                cleanupLocked()
                previous.getTransactionIdValue()
            } else {
                null
            }
            initiating = PaymentState.Initiating(phoneNumber, amount, upiId = upiId)
            _paymentState.value = initiating
            if (!coordinatorAcquired) {
                coordinator.acquire(COORDINATOR_TAG)
                coordinatorAcquired = true
            }
        }

        val txnId = initiating.transactionId
        val now = clock()
        val deadlineAt = now + verificationDeadlineMs

        if (supersededTxnId != null) {
            val previousInsert = pendingInsertJob
            scope.launch {
                previousInsert?.join()
                store.deletePending(supersededTxnId)
            }
        }

        pendingInsertJob = scope.launch {
            runCatching { store.deleteStalePending(now) }
            store.insertPending(
                Transaction(
                    transactionId = txnId,
                    amount = amount,
                    status = TransactionStatus.PENDING,
                    bankName = "",
                    smsExcerpt = "",
                    timestamp = now,
                    transactionType = "DEBIT",
                    phoneNumber = phoneNumber,
                    upiId = upiId,
                    deadlineAt = deadlineAt,
                    source = source
                )
            )
            Log.d(TAG, "PENDING row recorded for session")
        }

        collectJob = scope.launch {
            coordinator.sessionEvents.collect { event -> onCallSessionEvent(event) }
        }

        watchdogJob = scope.launch {
            delay(verificationDeadlineMs)
            onVerificationDeadline()
        }

        Log.d(TAG, "Payment session started")
        return txnId
    }

    fun onDialFailed(reason: String) {
        finishSession(TransactionStatus.CANCELLED) { phone, amount, txnId ->
            PaymentState.Cancelled(phone, amount, txnId, reason)
        }
    }

    /**
     * Handles a USSD accessibility-detected payment result.
     * Transitions the session to terminal Success using the detected amount/reference/recipient.
     */
    fun onUssdAccessibilityResult(amount: String?, reference: String?, recipientName: String? = null) {
        var txnId = ""
        var detectedAmount = ""
        var verifiedAt = 0L

        synchronized(sessionLock) {
            val state = _paymentState.value
            txnId = state.getTransactionIdValue() ?: return
            val phone = state.getPhoneNumberValue() ?: ""
            val sessAmount = state.getAmountValue() ?: ""
            detectedAmount = amount?.takeIf { it.isNotBlank() } ?: sessAmount
            verifiedAt = clock()

            if (state !is PaymentState.Success) {
                _paymentState.value = PaymentState.Success(
                    transactionId = txnId,
                    phoneNumber = phone,
                    amount = detectedAmount,
                    bankReference = reference,
                    recipientName = recipientName,
                    timestamp = verifiedAt
                )
                cleanupLocked()
            }
        }

        scope.launch {
            pendingInsertJob?.join()
            store.updateVerifiedPayment(
                transactionId = txnId,
                status = TransactionStatus.SUCCESS,
                amount = detectedAmount,
                bankRef = reference,
                recipientName = recipientName,
                verifiedAt = verifiedAt
            )
        }
        Log.d(TAG, "USSD accessibility confirmed payment as SUCCESS: amount=$detectedAmount, ref=$reference, recipient=$recipientName")
    }

    fun onUserCancelled() {
        finishSession(TransactionStatus.CANCELLED) { phone, amount, txnId ->
            PaymentState.Cancelled(phone, amount, txnId, "Cancelled by user")
        }
    }

    /**
     * Marks the session as user-confirmed success without SMS verification.
     */
    fun onUserConfirmedSuccess(amount: String?, reference: String?, recipientName: String? = null) {
        var txnId = ""
        var detectedAmount = ""
        var verifiedAt = 0L

        synchronized(sessionLock) {
            val state = _paymentState.value
            if (!state.isInProgress()) return
            txnId = state.getTransactionIdValue() ?: return
            val phone = state.getPhoneNumberValue() ?: ""
            val sessAmount = state.getAmountValue() ?: ""
            detectedAmount = amount ?: sessAmount
            verifiedAt = clock()

            _paymentState.value = PaymentState.Success(
                transactionId = txnId,
                phoneNumber = phone,
                amount = detectedAmount,
                bankReference = reference,
                recipientName = recipientName,
                timestamp = verifiedAt
            )
            cleanupLocked()
        }

        scope.launch {
            pendingInsertJob?.join()
            store.updateVerifiedPayment(
                transactionId = txnId,
                status = TransactionStatus.SUCCESS,
                amount = detectedAmount,
                bankRef = reference,
                recipientName = recipientName,
                verifiedAt = verifiedAt
            )
        }
        Log.d(TAG, "User confirmed payment success via accessibility: amount=$detectedAmount, ref=$reference, recipient=$recipientName")
    }

    fun onCallNeverStarted() {
        synchronized(sessionLock) {
            if (_paymentState.value !is PaymentState.Initiating) return
        }
        finishSession(TransactionStatus.CANCELLED) { phone, amount, txnId ->
            PaymentState.Cancelled(phone, amount, txnId, "The payment call was never connected")
        }
    }

    fun onSmsConfirmed(parsed: SimpleTransaction): String? {
        if (parsed.transactionType == "CREDIT") {
            Log.d(TAG, "CREDIT SMS during DEBIT session - not a confirmation")
            return null
        }

        val newStatus = when {
            parsed.status.equals(TransactionStatus.FAILED, ignoreCase = true) ->
                TransactionStatus.FAILED
            parsed.status.equals(TransactionStatus.NEEDS_REVIEW, ignoreCase = true) ->
                TransactionStatus.NEEDS_REVIEW
            else -> TransactionStatus.SUCCESS
        }
        val verifiedAt = clock()
        val txnId: String
        synchronized(sessionLock) {
            val current = _paymentState.value
            if (!current.isInProgress()) return null
            txnId = current.getTransactionIdValue() ?: return null
            val phone = current.getPhoneNumberValue() ?: parsed.phoneNumber ?: ""
            val amount = current.getAmountValue() ?: parsed.amount

            _paymentState.value = when (newStatus) {
                TransactionStatus.FAILED -> PaymentState.Failed(
                    error = "Bank reported the payment as failed",
                    phoneNumber = phone,
                    amount = amount,
                    transactionId = txnId,
                    canRetry = true
                )
                TransactionStatus.NEEDS_REVIEW -> PaymentState.NeedsReview(
                    transactionId = txnId,
                    phoneNumber = phone,
                    amount = amount
                )
                else -> PaymentState.Success(
                    transactionId = txnId,
                    phoneNumber = phone,
                    amount = amount,
                    bankReference = parsed.transactionId,
                    timestamp = verifiedAt
                )
            }
            cleanupLocked()
        }

        scope.launch {
            pendingInsertJob?.join()
            val updated = store.confirmTransaction(
                transactionId = txnId,
                status = newStatus,
                parsed = parsed,
                verifiedAt = verifiedAt
            )
            Log.d(TAG, "Session row confirmed as $newStatus (rows=$updated)")
        }
        return txnId
    }

    fun acknowledgeResult() {
        synchronized(sessionLock) {
            val state = _paymentState.value
            if (state.isTerminal() || state is PaymentState.Timeout) {
                _paymentState.value = PaymentState.Idle
            }
        }
    }

    fun reconcileStalePending() {
        scope.launch {
            val discarded = runCatching { store.deleteStalePending(clock()) }.getOrDefault(0)
            if (discarded > 0) {
                Log.d(TAG, "Discarded $discarded unconfirmed stale PENDING transaction(s)")
            }
        }
    }

    private fun onCallSessionEvent(event: CallSessionEvent) {
        when (event) {
            is CallSessionEvent.Started -> {
                synchronized(sessionLock) {
                    val state = _paymentState.value
                    if (state is PaymentState.Initiating) {
                        _paymentState.value = PaymentState.InProgress(
                            step = "On the line with the UPI service",
                            progress = 0.4f,
                            phoneNumber = state.phoneNumber,
                            amount = state.amount,
                            transactionId = state.transactionId,
                            upiId = state.upiId
                        )
                    }
                }
            }
            is CallSessionEvent.Ended -> {
                val state = synchronized(sessionLock) { _paymentState.value }
                when {
                    state is PaymentState.InProgress && event.durationMs < minRealCallDurationMs -> {
                        finishSession(TransactionStatus.CANCELLED) { phone, amount, txnId ->
                            PaymentState.Cancelled(
                                phone,
                                amount,
                                txnId,
                                "Call ended before the payment flow could complete"
                            )
                        }
                    }
                    state is PaymentState.InProgress -> {
                        var startedShortTimer = false
                        synchronized(sessionLock) {
                            // Re-check: an SMS may have confirmed while we were deciding.
                            val s = _paymentState.value
                            if (s is PaymentState.InProgress) {
                                _paymentState.value = PaymentState.WaitingForVerification(
                                    timeout = verificationDeadlineMs,
                                    phoneNumber = s.phoneNumber,
                                    amount = s.amount,
                                    transactionId = s.transactionId,
                                    upiId = s.upiId
                                )
                                // If it's a QR payment (has UPI ID), don't wait 10 minutes.
                                if (s.upiId != null) {
                                    startedShortTimer = true
                                }
                            }
                        }

                        if (startedShortTimer) {
                            gracePeriodJob?.cancel()
                            gracePeriodJob = scope.launch {
                                delay(GRACE_PERIOD_MS) // 5 second grace period
                                synchronized(sessionLock) {
                                    val current = _paymentState.value
                                    if (current is PaymentState.WaitingForVerification) {
                                        _paymentState.value = PaymentState.Unverified(
                                            transactionId = current.transactionId,
                                            phoneNumber = current.phoneNumber,
                                            amount = current.amount,
                                            upiId = current.upiId
                                        )
                                    }
                                }
                            }
                        }
                    }
                    else -> { /* Initiating without OFFHOOK, or already terminal - nothing to do */ }
                }
            }
        }
    }

    fun onUssdCallEndedManually() {
        synchronized(sessionLock) {
            val s = _paymentState.value
            if (s is PaymentState.Initiating) {
                // Synthesize the missing InProgress state
                _paymentState.value = PaymentState.InProgress(
                    step = "USSD Call Completed",
                    progress = 1.0f,
                    phoneNumber = s.phoneNumber,
                    amount = s.amount,
                    transactionId = s.transactionId,
                    upiId = s.upiId
                )
            }
        }
        onCallSessionEvent(com.proximity.app.telephony.CallSessionEvent.Ended(durationMs = 10000L)) // fake duration
    }

    private fun onVerificationDeadline() {
        val txnId = synchronized(sessionLock) {
            val s = _paymentState.value
            if (!s.isInProgress()) return
            val id = s.getTransactionIdValue() ?: return
            _paymentState.value = PaymentState.Timeout(
                timeoutType = TimeoutType.BANK_VERIFICATION,
                phoneNumber = s.getPhoneNumberValue() ?: "",
                amount = s.getAmountValue() ?: "",
                transactionId = id
            )
            cleanupLocked()
            id
        }

        scope.launch {
            pendingInsertJob?.join()
            store.deletePending(txnId)
        }
        Log.w(TAG, "No bank SMS before deadline - unconfirmed session discarded")
    }

    private fun finishSession(
        rowStatus: String,
        terminalState: (phone: String, amount: String, txnId: String) -> PaymentState
    ) {
        val txnId = synchronized(sessionLock) {
            val s = _paymentState.value
            if (!s.isInProgress()) return
            val id = s.getTransactionIdValue() ?: return
            _paymentState.value = terminalState(
                s.getPhoneNumberValue() ?: "",
                s.getAmountValue() ?: "",
                id
            )
            cleanupLocked()
            id
        }

        scope.launch {
            pendingInsertJob?.join()
            store.transitionStatus(txnId, TransactionStatus.PENDING, rowStatus)
        }
    }

    private fun cleanupLocked() {
        collectJob?.cancel()
        collectJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        gracePeriodJob?.cancel()
        gracePeriodJob = null
        if (coordinatorAcquired) {
            coordinator.release(COORDINATOR_TAG)
            coordinatorAcquired = false
        }
    }
}
