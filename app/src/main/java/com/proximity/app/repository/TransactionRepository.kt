package com.proximity.app.repository

import android.content.Context
import com.proximity.app.data.AppDatabase
import com.proximity.app.data.Transaction
import com.proximity.app.data.TransactionDao
import com.proximity.app.data.TransactionStatus
import com.proximity.app.payment.sms.SimpleTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TransactionRepository private constructor(context: Context) :
    com.proximity.app.payment.PaymentTransactionStore {

    private val transactionDao: TransactionDao = AppDatabase.getDatabase(context).transactionDao()

    companion object {
        @Volatile
        private var INSTANCE: TransactionRepository? = null

        fun getInstance(context: Context): TransactionRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = TransactionRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    fun getAllTransactions(): Flow<List<Transaction>> {
        return transactionDao.getAllTransactions()
    }

    fun getRecentTransactions(limit: Int = 10): Flow<List<Transaction>> {
        return transactionDao.getRecentTransactions(limit)
    }

    /**
     * Get recent transactions as PaymentDetails for UI compatibility
     */
    fun getRecentPaymentDetails(limit: Int = 10): Flow<List<com.proximity.app.data.PaymentDetails>> {
        return transactionDao.getRecentConfirmedTransactions(limit)
            .map { transactions ->
                transactions.map { it.toPaymentDetails() }
            }
    }

    fun getTransactionsByStatus(status: String): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByStatus(status)
    }

    fun getTransactionsByBank(bankName: String): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByBank(bankName)
    }

    fun searchTransactions(query: String): Flow<List<Transaction>> {
        return transactionDao.searchTransactions("%$query%")
    }

    suspend fun getTransactionById(transactionId: String): Transaction? {
        return transactionDao.getTransactionById(transactionId)
    }

    suspend fun saveTransaction(simpleTransaction: SimpleTransaction) {
        val transaction = Transaction.fromSimpleTransaction(simpleTransaction)
        transactionDao.insertTransaction(transaction)
    }

    suspend fun saveTransactions(transactions: List<Transaction>) {
        transactionDao.insertTransactions(transactions)
    }

    suspend fun updateTransaction(transaction: Transaction) {
        transactionDao.updateTransaction(transaction)
    }

    suspend fun deleteTransaction(transaction: Transaction) {
        transactionDao.deleteTransaction(transaction)
    }

    suspend fun deleteTransactionById(transactionId: String) {
        transactionDao.deleteTransactionById(transactionId)
    }

    suspend fun deleteAllTransactions() {
        transactionDao.deleteAllTransactions()
    }

    suspend fun getTransactionCount(): Int {
        return transactionDao.getTransactionCount()
    }

    suspend fun getTotalAmount(): Double? {
        return transactionDao.getTotalAmount()
    }

    fun getTransactionsByDateRange(startTime: Long, endTime: Long): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByDateRange(startTime, endTime)
    }

    override suspend fun insertPending(transaction: Transaction) {
        transactionDao.insertTransaction(transaction)
    }

    override suspend fun transitionStatus(
        transactionId: String,
        expectedStatus: String,
        newStatus: String
    ): Int {
        return transactionDao.transitionStatus(transactionId, expectedStatus, newStatus)
    }

    override suspend fun confirmTransaction(
        transactionId: String,
        status: String,
        parsed: SimpleTransaction,
        verifiedAt: Long
    ): Int {
        return transactionDao.confirmTransaction(
            transactionId = transactionId,
            status = status,
            bankRef = parsed.transactionId,
            bankName = parsed.bankName,
            smsExcerpt = parsed.smsExcerpt,
            upiId = parsed.upiId,
            recipientName = parsed.recipientName,
            amount = parsed.amount,
            verifiedAt = verifiedAt,
            expectedStatus = TransactionStatus.PENDING
        )
    }

    override suspend fun deleteStalePending(now: Long): Int {
        return transactionDao.deleteStalePending(now)
    }

    override suspend fun updateVerifiedPayment(
        transactionId: String,
        status: String,
        amount: String,
        bankRef: String?,
        recipientName: String?,
        verifiedAt: Long
    ): Int {
        val updated = transactionDao.updateVerifiedPayment(
            transactionId = transactionId,
            status = status,
            amount = amount,
            bankRef = bankRef,
            recipientName = recipientName,
            verifiedAt = verifiedAt
        )
        if (updated == 0) {
            val existing = transactionDao.getTransactionById(transactionId)
            if (existing != null) {
                transactionDao.updateTransaction(
                    existing.copy(
                        status = status,
                        amount = amount,
                        bankRef = bankRef,
                        recipientName = recipientName ?: existing.recipientName,
                        verifiedAt = verifiedAt
                    )
                )
            } else {
                transactionDao.insertTransaction(
                    Transaction(
                        transactionId = transactionId,
                        amount = amount,
                        status = status,
                        bankName = "USSD",
                        smsExcerpt = "Confirmed via USSD",
                        timestamp = verifiedAt,
                        transactionType = "DEBIT",
                        recipientName = recipientName,
                        bankRef = bankRef,
                        verifiedAt = verifiedAt,
                        source = com.proximity.app.data.TransactionSource.QR
                    )
                )
            }
            return 1
        }
        return updated
    }

    override suspend fun deletePending(transactionId: String): Int {
        return transactionDao.deletePending(transactionId)
    }
}
