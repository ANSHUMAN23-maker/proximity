// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentRecordDao {

    @Query("SELECT * FROM payment_records ORDER BY timestampMillis DESC")
    fun observeAllRecords(): Flow<List<PaymentRecord>>

    @Query("SELECT * FROM payment_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PaymentRecord?

    @Query("SELECT * FROM payment_records WHERE status = 'PENDING' ORDER BY timestampMillis DESC LIMIT 1")
    suspend fun getLatestPendingRecord(): PaymentRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: PaymentRecord)

    @Query(
        "UPDATE payment_records SET status = :newStatus, " +
            "bankReferenceId = COALESCE(:refId, bankReferenceId), " +
            "recipientName = COALESCE(:recipientName, recipientName), " +
            "bankName = COALESCE(:bankName, bankName), " +
            "bankConfirmationExcerpt = COALESCE(:excerpt, bankConfirmationExcerpt) " +
            "WHERE id = :id"
    )
    suspend fun updateOutcome(
        id: String,
        newStatus: PaymentRecordStatus,
        refId: String?,
        recipientName: String?,
        bankName: String?,
        excerpt: String?
    )

    @Query(
        "UPDATE payment_records SET status = 'UNVERIFIED' " +
            "WHERE status = 'PENDING' AND verificationDeadlineMillis < :nowMillis"
    )
    suspend fun expireStalePendingRecords(nowMillis: Long): Int

    @Query("DELETE FROM payment_records WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM payment_records")
    suspend fun deleteAll()
}

interface PaymentRecordStore {
    fun observeRecords(): Flow<List<PaymentRecord>>
    suspend fun getById(id: String): PaymentRecord?
    suspend fun getLatestPending(): PaymentRecord?
    suspend fun upsert(record: PaymentRecord)
    suspend fun expireStalePending(nowMillis: Long)
    suspend fun deleteById(id: String)
}
