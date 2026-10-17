package com.proximity.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [Index("timestamp"), Index("status")]
)
data class Transaction(
    @PrimaryKey
    val transactionId: String,
    val amount: String,
    val status: String,
    val bankName: String,
    val smsExcerpt: String,
    val timestamp: Long = System.currentTimeMillis(),
    val upiId: String? = null,
    val transactionType: String = "DEBIT",
    val recipientName: String? = null,
    val phoneNumber: String? = null,
    val bankRef: String? = null,
    val deadlineAt: Long? = null,
    val source: String? = null,
    val verifiedAt: Long? = null
) {
    fun toPaymentDetails(): PaymentDetails {
        return PaymentDetails(
            id = transactionId,
            recipientName = recipientName,
            phoneNumber = phoneNumber ?: "",
            amount = amount.toDoubleOrNull() ?: 0.0,
            timestamp = timestamp,
            status = when (status.uppercase()) {
                "SUCCESS", "SUCCESSFUL", "COMPLETED" -> PaymentStatus.COMPLETED
                "USER_ATTESTED", "USER_CONFIRMED" -> PaymentStatus.USER_CONFIRMED
                "UNVERIFIED" -> PaymentStatus.UNVERIFIED
                "NEEDS_REVIEW" -> PaymentStatus.NEEDS_REVIEW
                "PENDING" -> PaymentStatus.PENDING
                "CANCELLED" -> PaymentStatus.CANCELLED
                "FAILED", "DECLINED" -> PaymentStatus.FAILED
                else -> PaymentStatus.PENDING
            }
        )
    }

    companion object {
        fun fromSimpleTransaction(simpleTransaction: com.proximity.app.payment.sms.SimpleTransaction): Transaction {
            return Transaction(
                transactionId = simpleTransaction.transactionId,
                amount = simpleTransaction.amount,
                status = simpleTransaction.status,
                bankName = simpleTransaction.bankName,
                smsExcerpt = simpleTransaction.smsExcerpt,
                timestamp = simpleTransaction.timestamp,
                upiId = simpleTransaction.upiId,
                transactionType = simpleTransaction.transactionType,
                recipientName = simpleTransaction.recipientName,
                phoneNumber = simpleTransaction.phoneNumber,
                bankRef = simpleTransaction.transactionId,
                source = TransactionSource.SMS
            )
        }
    }
}
