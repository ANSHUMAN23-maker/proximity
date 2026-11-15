// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.accessibility

data class UssdDetectionResult(
    val status: UssdDetectionStatus,
    val amount: String? = null,
    val reference: String? = null,
    val recipientName: String? = null,
    val rawText: String? = null,
    val detectedAt: Long = System.currentTimeMillis()
) {
    fun mergeWithPrevious(previous: UssdDetectionResult?): UssdDetectionResult {
        if (previous == null) return this
        val resolvedStatus = when {
            this.status == UssdDetectionStatus.SUCCESS || previous.status == UssdDetectionStatus.SUCCESS ->
                UssdDetectionStatus.SUCCESS
            this.status == UssdDetectionStatus.FAILED -> UssdDetectionStatus.FAILED
            else -> this.status
        }
        return UssdDetectionResult(
            status = resolvedStatus,
            amount = this.amount ?: previous.amount,
            reference = this.reference ?: previous.reference,
            recipientName = this.recipientName ?: previous.recipientName,
            rawText = this.rawText ?: previous.rawText
        )
    }
}

enum class UssdDetectionStatus {
    SUCCESS,
    FAILED,
    CANCELLED,
    UNKNOWN
}
