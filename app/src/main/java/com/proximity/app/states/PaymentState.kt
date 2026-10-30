package com.proximity.app.states

import java.util.UUID

sealed class PaymentState {

    companion object {
        const val PROGRESS_IDLE = 0f
        const val PROGRESS_INITIATING = 0.1f
        const val PROGRESS_WAITING = 0.7f
        const val PROGRESS_UNVERIFIED = 0.8f
        const val PROGRESS_DONE = 1f
        const val PROGRESS_FAILED = 0f
    }

    object Idle : PaymentState()

    data class Initiating(
        val phoneNumber: String,
        val amount: String,
        val transactionId: String = UUID.randomUUID().toString(),
        val upiId: String? = null
    ) : PaymentState()

    data class InProgress(
        val step: String,
        val progress: Float,
        val phoneNumber: String,
        val amount: String,
        val transactionId: String,
        val upiId: String? = null
    ) : PaymentState()
    data class WaitingForVerification(
        val timeout: Long,
        val phoneNumber: String,
        val amount: String,
        val transactionId: String,
        val upiId: String? = null
    ) : PaymentState()
    data class Unverified(
        val transactionId: String,
        val phoneNumber: String,
        val amount: String,
        val upiId: String? = null
    ) : PaymentState()
    data class Success(
        val transactionId: String,
        val phoneNumber: String,
        val amount: String,
        val bankReference: String? = null,
        val recipientName: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    ) : PaymentState()
    data class Failed(
        val error: String,
        val errorCode: String? = null,
        val phoneNumber: String,
        val amount: String,
        val transactionId: String,
        val canRetry: Boolean = true
    ) : PaymentState()
    data class NeedsReview(
        val transactionId: String,
        val phoneNumber: String,
        val amount: String
    ) : PaymentState()
    data class Cancelled(
        val phoneNumber: String,
        val amount: String,
        val transactionId: String,
        val reason: String = "User cancelled"
    ) : PaymentState()

    data class Timeout(
        val timeoutType: TimeoutType,
        val phoneNumber: String,
        val amount: String,
        val transactionId: String
    ) : PaymentState()

    fun isTerminal(): Boolean = this is Success || this is Failed || this is Cancelled || this is NeedsReview

    fun isInProgress(): Boolean = this is Initiating || this is InProgress ||
        this is WaitingForVerification || this is Unverified

    fun canRetry(): Boolean = when (this) {
        is Failed -> canRetry
        is Timeout -> true
        else -> false
    }

    fun getTransactionIdValue(): String? = when (this) {
        is Initiating -> this.transactionId
        is InProgress -> this.transactionId
        is WaitingForVerification -> this.transactionId
        is Unverified -> this.transactionId
        is Success -> this.transactionId
        is Failed -> this.transactionId
        is NeedsReview -> this.transactionId
        is Cancelled -> this.transactionId
        is Timeout -> this.transactionId
        else -> null
    }

    fun getPhoneNumberValue(): String? = when (this) {
        is Initiating -> this.phoneNumber
        is InProgress -> this.phoneNumber
        is WaitingForVerification -> this.phoneNumber
        is Unverified -> this.phoneNumber
        is Success -> this.phoneNumber
        is Failed -> this.phoneNumber
        is NeedsReview -> this.phoneNumber
        is Cancelled -> this.phoneNumber
        is Timeout -> this.phoneNumber
        else -> null
    }

    fun getAmountValue(): String? = when (this) {
        is Initiating -> this.amount
        is InProgress -> this.amount
        is WaitingForVerification -> this.amount
        is Unverified -> this.amount
        is Success -> this.amount
        is Failed -> this.amount
        is NeedsReview -> this.amount
        is Cancelled -> this.amount
        is Timeout -> this.amount
        else -> null
    }

    fun getUpiIdValue(): String? = when (this) {
        is Initiating -> this.upiId
        is InProgress -> this.upiId
        is WaitingForVerification -> this.upiId
        is Unverified -> this.upiId
        else -> null
    }
}

enum class TimeoutType {
    CALL_INITIATION,
    BANK_VERIFICATION,
    USER_RESPONSE,
    NETWORK_CONNECTION
}


fun PaymentState.getDisplayMessage(): String = when (this) {
    is PaymentState.Idle -> "Ready to make payment"
    is PaymentState.Initiating -> "Initiating payment..."
    is PaymentState.InProgress -> "Processing: $step"
    is PaymentState.WaitingForVerification -> "Waiting for bank verification..."
    is PaymentState.Unverified -> "Please verify payment outcome"
    is PaymentState.Success -> "Payment successful!"
    is PaymentState.Failed -> "Payment failed: $error"
    is PaymentState.NeedsReview -> "Payment needs review - check your bank statement"
    is PaymentState.Cancelled -> "Payment cancelled: $reason"
    is PaymentState.Timeout -> "Payment timeout: ${timeoutType.name}"
}

fun PaymentState.getProgressPercentage(): Float = when (this) {
    is PaymentState.Idle -> PaymentState.PROGRESS_IDLE
    is PaymentState.Initiating -> PaymentState.PROGRESS_INITIATING
    is PaymentState.InProgress -> progress
    is PaymentState.WaitingForVerification -> PaymentState.PROGRESS_WAITING
    is PaymentState.Unverified -> PaymentState.PROGRESS_UNVERIFIED
    is PaymentState.Success -> PaymentState.PROGRESS_DONE
    is PaymentState.Failed -> PaymentState.PROGRESS_FAILED
    is PaymentState.NeedsReview -> PaymentState.PROGRESS_DONE
    is PaymentState.Cancelled -> PaymentState.PROGRESS_FAILED
    is PaymentState.Timeout -> PaymentState.PROGRESS_FAILED
}
