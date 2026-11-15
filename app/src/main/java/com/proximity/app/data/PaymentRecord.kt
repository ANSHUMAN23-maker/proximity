// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.data

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

enum class PaymentRecordStatus {
    SUCCESS,
    USER_CONFIRMED,
    PENDING,
    UNVERIFIED,
    NEEDS_REVIEW,
    CANCELLED,
    FAILED
}

enum class PaymentChannel {
    USSD_99,
    UPI_123PAY
}

@Parcelize
@Entity(
    tableName = "payment_records",
    indices = [
        Index(value = ["timestampMillis"]),
        Index(value = ["status"]),
        Index(value = ["bankReferenceId"])
    ]
)
data class PaymentRecord(
    @PrimaryKey val id: String,
    val recipientName: String?,
    val phoneNumber: String?,
    val vpa: String?,
    val amountRupees: String,
    val bankName: String,
    val bankReferenceId: String?,
    val status: PaymentRecordStatus,
    val channel: PaymentChannel,
    val timestampMillis: Long,
    val verificationDeadlineMillis: Long,
    val bankConfirmationExcerpt: String?
) : Parcelable
