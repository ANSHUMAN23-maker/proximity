// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.payment

import com.proximity.app.accessibility.UssdDetectionResult
import com.proximity.app.accessibility.UssdDetectionStatus
import com.proximity.app.accessibility.UssdDialogAnalyzer
import com.proximity.app.accessibility.UssdDialogReaderService
import com.proximity.app.constants.AppLimits
import com.proximity.app.data.PaymentChannel
import com.proximity.app.data.PaymentRecord
import com.proximity.app.data.PaymentRecordStatus
import com.proximity.app.data.PaymentRecordStore
import com.proximity.app.payment.sms.BankMessageAnalyzer
import com.proximity.app.utils.CurrencyFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.util.UUID

sealed class PaymentState {
    object Idle : PaymentState()
    data class Initiating(val session: ActivePaymentSession) : PaymentState()
    data class InProgress(val session: ActivePaymentSession) : PaymentState()
    data class WaitingForVerification(val session: ActivePaymentSession) : PaymentState()
    data class Success(val transaction: PaymentRecord) : PaymentState()
    data class Failed(val transaction: PaymentRecord, val reason: String) : PaymentState()
    data class NeedsReview(val transaction: PaymentRecord) : PaymentState()
    data class Unverified(val transaction: PaymentRecord) : PaymentState()
    data class Cancelled(val transaction: PaymentRecord) : PaymentState()
    data class Timeout(val transaction: PaymentRecord) : PaymentState()

    val isTerminal: Boolean
        get() = this is Success || this is Failed || this is NeedsReview ||
            this is Unverified || this is Cancelled || this is Timeout
}

data class ActivePaymentSession(
    val transactionId: String,
    val channel: PaymentChannel,
    val recipientName: String?,
    val phoneNumber: String?,
    val vpa: String?,
    val expectedAmountRupees: String,
    val configuredBankName: String,
    val startedAtMillis: Long,
    val smsWindowCloseMillis: Long
)

class PaymentLifecycleManager(
    private val recordStore: PaymentRecordStore,
    private val scope: CoroutineScope,
    private val onDisableUssdAccessibility: () -> Unit = { UssdDialogReaderService.disableService() },
    private val onPostPaymentNotification: (PaymentRecord) -> Unit = {}
) {
    companion object {
        const val VERIFICATION_DEADLINE_MS = AppLimits.VERIFICATION_DEADLINE_MS
        const val SMS_WINDOW_DURATION_MS = AppLimits.SMS_WINDOW_DURATION_MS
        const val QR_RETURN_GRACE_MS = AppLimits.QR_RETURN_GRACE_MS
        const val MIN_UPI123_CALL_DURATION_MS = AppLimits.MIN_UPI123_CALL_DURATION_MS
    }

    private val mutex = Mutex()
    private val windowMonitor = PaymentWindowMonitor()
    private val smsDeduplicator = SmsDeduplicator(AppLimits.SMS_DEDUP_WINDOW_MS)

    private val _state = MutableStateFlow<PaymentState>(PaymentState.Idle)
    val state: StateFlow<PaymentState> = _state.asStateFlow()

    @Volatile
    private var activeSession: ActivePaymentSession? = null

    @Volatile
    private var lastMergedUssdDialog: UssdDetectionResult? = null

    @Volatile
    private var smsWindowCloseAtMillis: Long = 0L

    @Volatile
    private var ussdInFlightFlag: Boolean = false

    private var smsWindowExpiryJob: Job? = null
    private var qrGraceJob: Job? = null

    private fun updateFastFlagsLocked(newState: PaymentState) {
        val current = activeSession
        smsWindowCloseAtMillis = current?.smsWindowCloseMillis ?: 0L
        ussdInFlightFlag = current != null &&
            current.channel == PaymentChannel.USSD_99 &&
            (newState is PaymentState.Initiating ||
                newState is PaymentState.InProgress ||
                newState is PaymentState.WaitingForVerification)
    }

    /**
     * Restores an unexpired PENDING transaction after process death so incoming bank SMS
     * messages can still reconcile the active window without holding a lock across suspension points.
     */
    suspend fun hydratePendingSessionIfNeeded(nowMillis: Long = System.currentTimeMillis()) = mutex.withLock {
        if (activeSession != null) return@withLock
        recordStore.expireStalePending(nowMillis)
        val pending = recordStore.getLatestPending() ?: return@withLock
        val windowClose = pending.timestampMillis + SMS_WINDOW_DURATION_MS
        if (nowMillis > windowClose || nowMillis > pending.verificationDeadlineMillis) {
            val expired = pending.copy(status = PaymentRecordStatus.UNVERIFIED)
            recordStore.upsert(expired)
            return@withLock
        }

        val restored = ActivePaymentSession(
            transactionId = pending.id,
            channel = pending.channel,
            recipientName = pending.recipientName,
            phoneNumber = pending.phoneNumber,
            vpa = pending.vpa,
            expectedAmountRupees = pending.amountRupees,
            configuredBankName = pending.bankName,
            startedAtMillis = pending.timestampMillis,
            smsWindowCloseMillis = windowClose
        )
        activeSession = restored
        windowMonitor.armWindow(pending.timestampMillis)
        val waitingState = PaymentState.WaitingForVerification(restored)
        _state.value = waitingState
        updateFastFlagsLocked(waitingState)

        smsWindowExpiryJob?.cancel()
        val remainingMs = (windowClose - nowMillis).coerceAtLeast(1L)
        smsWindowExpiryJob = scope.launch {
            delay(remainingMs)
            onSmsWindowTimedOut(restored.transactionId)
        }
    }

    fun resetTerminalState() {
        val cur = _state.value
        if (cur.isTerminal && activeSession == null) {
            _state.value = PaymentState.Idle
        }
    }

    fun isSmsWindowOpen(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val closeMillis = smsWindowCloseAtMillis
        return closeMillis > 0L && windowMonitor.isWindowOpen(nowMillis) && nowMillis <= closeMillis
    }

    fun isUssdPaymentInFlight(): Boolean = ussdInFlightFlag

    suspend fun startNewSession(
        channel: PaymentChannel,
        recipientName: String?,
        phoneNumber: String?,
        vpa: String?,
        amountRupees: String,
        configuredBankName: String,
        nowMillis: Long = System.currentTimeMillis()
    ): ActivePaymentSession = mutex.withLock {
        smsWindowExpiryJob?.cancel()
        qrGraceJob?.cancel()
        lastMergedUssdDialog = null

        activeSession?.let { prev ->
            val prevRow = recordStore.getById(prev.transactionId)
            if (prevRow != null && prevRow.status == PaymentRecordStatus.PENDING) {
                recordStore.upsert(prevRow.copy(status = PaymentRecordStatus.CANCELLED))
            }
        }
        recordStore.expireStalePending(nowMillis)

        val txnId = "PAY-${UUID.randomUUID().toString().take(8).uppercase()}"
        val session = ActivePaymentSession(
            transactionId = txnId,
            channel = channel,
            recipientName = recipientName,
            phoneNumber = phoneNumber,
            vpa = vpa,
            expectedAmountRupees = amountRupees.ifBlank { "0.00" },
            configuredBankName = configuredBankName,
            startedAtMillis = nowMillis,
            smsWindowCloseMillis = nowMillis + SMS_WINDOW_DURATION_MS
        )

        val pendingRecord = PaymentRecord(
            id = txnId,
            recipientName = recipientName,
            phoneNumber = phoneNumber,
            vpa = vpa,
            amountRupees = amountRupees.ifBlank { "0.00" },
            bankName = configuredBankName,
            bankReferenceId = null,
            status = PaymentRecordStatus.PENDING,
            channel = channel,
            timestampMillis = nowMillis,
            verificationDeadlineMillis = nowMillis + VERIFICATION_DEADLINE_MS,
            bankConfirmationExcerpt = null
        )
        recordStore.upsert(pendingRecord)

        activeSession = session
        windowMonitor.armWindow(nowMillis)
        if (channel == PaymentChannel.USSD_99) {
            UssdDialogReaderService.reset()
            UssdDialogReaderService.ussdPaymentActive = true
        }
        val initiating = PaymentState.Initiating(session)
        _state.value = initiating
        updateFastFlagsLocked(initiating)

        smsWindowExpiryJob = scope.launch {
            delay(SMS_WINDOW_DURATION_MS)
            onSmsWindowTimedOut(txnId)
        }

        session
    }

    suspend fun markCallOrUssdInProgress() = mutex.withLock {
        val current = activeSession ?: return@withLock
        val st = _state.value
        if (st is PaymentState.Initiating) {
            val inProgress = PaymentState.InProgress(current)
            _state.value = inProgress
            updateFastFlagsLocked(inProgress)
        }
    }

    suspend fun transitionToWaitingForVerification(startQrGraceTimer: Boolean = false) = mutex.withLock {
        val current = activeSession ?: return@withLock
        val st = _state.value
        if (st.isTerminal) {
            return@withLock
        }

        val waiting = PaymentState.WaitingForVerification(current)
        _state.value = waiting
        updateFastFlagsLocked(waiting)
        if (startQrGraceTimer && current.channel == PaymentChannel.USSD_99) {
            val targetTxnId = current.transactionId
            qrGraceJob?.cancel()
            qrGraceJob = scope.launch {
                delay(QR_RETURN_GRACE_MS)
                mutex.withLock {
                    val curState = _state.value
                    val curSession = activeSession
                    if (curState is PaymentState.WaitingForVerification && curSession?.transactionId == targetTxnId) {
                        smsWindowExpiryJob?.cancel()
                        activeSession = null
                        windowMonitor.disarmWindow()
                        onDisableUssdAccessibility()
                        val row = recordStore.getById(targetTxnId)
                        if (row != null && row.status == PaymentRecordStatus.PENDING) {
                            val updated = row.copy(status = PaymentRecordStatus.UNVERIFIED)
                            recordStore.upsert(updated)
                            val unverified = PaymentState.Unverified(updated)
                            _state.value = unverified
                            updateFastFlagsLocked(unverified)
                        } else {
                            updateFastFlagsLocked(_state.value)
                        }
                    }
                }
            }
        }
    }

    fun onUpi123CallEndedAsync(callDurationMillis: Long): Job = scope.launch {
        onUpi123CallEnded(callDurationMillis)
    }

    suspend fun onUpi123CallEnded(callDurationMillis: Long) {
        if (callDurationMillis < MIN_UPI123_CALL_DURATION_MS) {
            cancelCurrentPayment("Call ended before 5-second minimum duration")
        } else {
            transitionToWaitingForVerification(startQrGraceTimer = false)
        }
    }

    fun cancelCurrentPaymentAsync(reason: String = "Cancelled by user"): Job = scope.launch {
        cancelCurrentPayment(reason)
    }

    suspend fun cancelCurrentPayment(reason: String = "Cancelled by user") = mutex.withLock {
        val current = activeSession ?: return@withLock
        if (_state.value is PaymentState.Success) return@withLock

        qrGraceJob?.cancel()
        smsWindowExpiryJob?.cancel()
        activeSession = null
        windowMonitor.disarmWindow()
        onDisableUssdAccessibility()

        val row = recordStore.getById(current.transactionId)
        val cancelledRecord = (row ?: PaymentRecord(
            id = current.transactionId,
            recipientName = current.recipientName,
            phoneNumber = current.phoneNumber,
            vpa = current.vpa,
            amountRupees = current.expectedAmountRupees,
            bankName = current.configuredBankName,
            bankReferenceId = null,
            status = PaymentRecordStatus.CANCELLED,
            channel = current.channel,
            timestampMillis = current.startedAtMillis,
            verificationDeadlineMillis = current.smsWindowCloseMillis,
            bankConfirmationExcerpt = null
        )).copy(status = PaymentRecordStatus.CANCELLED)

        recordStore.upsert(cancelledRecord)
        val cancelled = PaymentState.Cancelled(cancelledRecord)
        _state.value = cancelled
        updateFastFlagsLocked(cancelled)
    }

    suspend fun ingestBankSms(
        sender: String?,
        body: String,
        nowMillis: Long = System.currentTimeMillis()
    ): PaymentRecord? = mutex.withLock {
        val current = activeSession
        val windowOpen = current != null && nowMillis <= current.smsWindowCloseMillis

        val fallbackPending = if (!windowOpen) recordStore.getLatestPending() else null
        if (!windowOpen && (fallbackPending == null || nowMillis > fallbackPending.verificationDeadlineMillis)) {
            return@withLock null
        }

        if (!smsDeduplicator.tryClaim(sender, body, nowMillis)) {
            return@withLock null
        }

        val analyzed = BankMessageAnalyzer.analyze(sender, body) ?: return@withLock null
        if (analyzed.direction == BankMessageAnalyzer.MessageDirection.CREDIT) {
            return@withLock null
        }

        val targetId = current?.transactionId ?: fallbackPending!!.id
        val existingRow = recordStore.getById(targetId) ?: fallbackPending ?: return@withLock null

        if (existingRow.status == PaymentRecordStatus.SUCCESS) {
            return@withLock existingRow
        }

        val expectedAmount = current?.expectedAmountRupees ?: existingRow.amountRupees
        val isOpenAmountQr = expectedAmount.toBigDecimalOrNull()?.compareTo(BigDecimal.ZERO) == 0
        val resolvedAmountStr = analyzed.amount?.toPlainString() ?: expectedAmount
        val resolvedStatus = when (analyzed.direction) {
            BankMessageAnalyzer.MessageDirection.FAILED -> PaymentRecordStatus.FAILED
            BankMessageAnalyzer.MessageDirection.DEBIT -> {
                if (isOpenAmountQr || BankMessageAnalyzer.amountsMatchPaiseExact(expectedAmount, analyzed.amount)) {
                    PaymentRecordStatus.SUCCESS
                } else {
                    PaymentRecordStatus.NEEDS_REVIEW
                }
            }
            else -> return@withLock null
        }

        val updatedRecord = existingRow.copy(
            recipientName = if (existingRow.recipientName.isNullOrBlank()) analyzed.recipientName ?: existingRow.recipientName else existingRow.recipientName,
            vpa = analyzed.vpa ?: existingRow.vpa,
            amountRupees = resolvedAmountStr,
            bankName = analyzed.bankDisplayName.ifBlank { existingRow.bankName },
            bankReferenceId = analyzed.referenceId ?: existingRow.bankReferenceId,
            status = resolvedStatus,
            bankConfirmationExcerpt = analyzed.privacySafeExcerpt
        )
        recordStore.upsert(updatedRecord)

        qrGraceJob?.cancel()
        if (resolvedStatus == PaymentRecordStatus.SUCCESS ||
            resolvedStatus == PaymentRecordStatus.FAILED ||
            resolvedStatus == PaymentRecordStatus.NEEDS_REVIEW
        ) {
            smsWindowExpiryJob?.cancel()
            activeSession = null
            windowMonitor.disarmWindow()
            onDisableUssdAccessibility()
        }

        val newState = when (resolvedStatus) {
            PaymentRecordStatus.SUCCESS -> PaymentState.Success(updatedRecord)
            PaymentRecordStatus.FAILED -> PaymentState.Failed(updatedRecord, analyzed.privacySafeExcerpt)
            PaymentRecordStatus.NEEDS_REVIEW -> PaymentState.NeedsReview(updatedRecord)
            else -> _state.value
        }
        _state.value = newState
        updateFastFlagsLocked(newState)

        onPostPaymentNotification(updatedRecord)
        updatedRecord
    }

    suspend fun ingestUssdAccessibilityEvent(texts: List<String>): PaymentRecord? = mutex.withLock {
        val current = activeSession ?: return@withLock null
        if (current.channel != PaymentChannel.USSD_99) return@withLock null
        if (_state.value is PaymentState.Success) return@withLock null

        val dialogText = texts.filter { it.isNotBlank() }.distinct().joinToString(" | ")
        if (dialogText.isBlank()) return@withLock null

        val parsed = UssdDialogAnalyzer.parsePaymentResult(dialogText)
        val merged = parsed.mergeWithPrevious(lastMergedUssdDialog)
        lastMergedUssdDialog = merged

        if (merged.status == UssdDetectionStatus.UNKNOWN) {
            return@withLock null
        }

        val existingRow = recordStore.getById(current.transactionId) ?: return@withLock null
        val resolvedAmount = merged.amount ?: existingRow.amountRupees
        val formattedAmt = CurrencyFormat.formatWithSymbol(resolvedAmount)
        val isOpenAmountQr = current.expectedAmountRupees.toBigDecimalOrNull()?.compareTo(BigDecimal.ZERO) == 0
        val detectedDecimal = merged.amount?.toBigDecimalOrNull()
        val amountMatchesExpected = isOpenAmountQr ||
            detectedDecimal == null ||
            BankMessageAnalyzer.amountsMatchPaiseExact(current.expectedAmountRupees, detectedDecimal)

        val newStatus = when {
            merged.status == UssdDetectionStatus.FAILED -> PaymentRecordStatus.FAILED
            amountMatchesExpected -> PaymentRecordStatus.SUCCESS
            else -> PaymentRecordStatus.NEEDS_REVIEW
        }
        val excerpt = when (newStatus) {
            PaymentRecordStatus.SUCCESS -> "$formattedAmt debited via *99# \u2014 ${existingRow.bankName}"
            PaymentRecordStatus.NEEDS_REVIEW -> "$formattedAmt via *99# differs from expected amount \u2014 ${existingRow.bankName}"
            else -> "$formattedAmt USSD payment failed \u2014 ${existingRow.bankName}"
        }

        val updatedRecord = existingRow.copy(
            recipientName = if (existingRow.recipientName.isNullOrBlank()) merged.recipientName ?: existingRow.recipientName else existingRow.recipientName,
            amountRupees = resolvedAmount,
            bankReferenceId = merged.reference ?: existingRow.bankReferenceId,
            status = newStatus,
            bankConfirmationExcerpt = excerpt
        )
        recordStore.upsert(updatedRecord)

        qrGraceJob?.cancel()
        smsWindowExpiryJob?.cancel()
        activeSession = null
        windowMonitor.disarmWindow()
        onDisableUssdAccessibility()

        val nextState = when (newStatus) {
            PaymentRecordStatus.SUCCESS -> PaymentState.Success(updatedRecord)
            PaymentRecordStatus.NEEDS_REVIEW -> PaymentState.NeedsReview(updatedRecord)
            else -> PaymentState.Failed(updatedRecord, excerpt)
        }
        _state.value = nextState
        updateFastFlagsLocked(nextState)
        onPostPaymentNotification(updatedRecord)
        updatedRecord
    }

    private suspend fun onSmsWindowTimedOut(expectedTxnId: String) = mutex.withLock {
        val current = activeSession ?: return@withLock
        if (current.transactionId != expectedTxnId) return@withLock
        if (_state.value is PaymentState.Success) return@withLock
        activeSession = null
        windowMonitor.disarmWindow()
        onDisableUssdAccessibility()

        val row = recordStore.getById(current.transactionId)
        if (row != null && row.status == PaymentRecordStatus.PENDING) {
            val updated = row.copy(status = PaymentRecordStatus.UNVERIFIED)
            recordStore.upsert(updated)
            val timeoutState = PaymentState.Timeout(updated)
            _state.value = timeoutState
            updateFastFlagsLocked(timeoutState)
            onPostPaymentNotification(updated)
        } else {
            updateFastFlagsLocked(_state.value)
        }
    }
}
