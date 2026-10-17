package com.proximity.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentTransactions(limit: Int = 10): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE status != 'PENDING' ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentConfirmedTransactions(limit: Int = 10): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE status = :status ORDER BY timestamp DESC")
    fun getTransactionsByStatus(status: String): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE bankName = :bankName ORDER BY timestamp DESC")
    fun getTransactionsByBank(bankName: String): Flow<List<Transaction>>

    @Query(
        "SELECT * FROM transactions WHERE recipientName LIKE :query OR phoneNumber LIKE :query ORDER BY timestamp DESC"
    )
    fun searchTransactions(query: String): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE transactionId = :transactionId")
    suspend fun getTransactionById(transactionId: String): Transaction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<Transaction>)

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE transactionId = :transactionId")
    suspend fun deleteTransactionById(transactionId: String)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getTransactionCount(): Int

    @Query("SELECT SUM(CAST(amount AS REAL)) FROM transactions WHERE status IN ('SUCCESS', 'SUCCESSFUL', 'COMPLETED')")
    suspend fun getTotalAmount(): Double?

    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getTransactionsByDateRange(startTime: Long, endTime: Long): Flow<List<Transaction>>

    @Query(
        "UPDATE transactions SET status = :newStatus WHERE transactionId = :transactionId AND status = :expectedStatus"
    )
    suspend fun transitionStatus(transactionId: String, expectedStatus: String, newStatus: String): Int

    @Query(
        "UPDATE transactions SET status = :status, bankRef = :bankRef, bankName = :bankName, " +
            "smsExcerpt = :smsExcerpt, upiId = COALESCE(:upiId, upiId), " +
            "recipientName = COALESCE(:recipientName, recipientName), " +
            "amount = CASE WHEN amount = '' THEN :amount ELSE amount END, " +
            "verifiedAt = :verifiedAt " +
            "WHERE transactionId = :transactionId AND status = :expectedStatus"
    )
    @Suppress("LongParameterList")
    suspend fun confirmTransaction(
        transactionId: String,
        status: String,
        bankRef: String?,
        bankName: String,
        smsExcerpt: String,
        upiId: String?,
        recipientName: String?,
        amount: String,
        verifiedAt: Long,
        expectedStatus: String
    ): Int

    @Query("DELETE FROM transactions WHERE status = 'PENDING' AND deadlineAt IS NOT NULL AND deadlineAt < :now")
    suspend fun deleteStalePending(now: Long): Int

    @Query("DELETE FROM transactions WHERE transactionId = :transactionId AND status = 'PENDING'")
    suspend fun deletePending(transactionId: String): Int
}
