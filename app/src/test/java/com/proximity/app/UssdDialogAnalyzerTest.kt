// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app

import com.proximity.app.accessibility.UssdDetectionStatus
import com.proximity.app.accessibility.UssdDialogAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Test

class UssdDialogAnalyzerTest {

    @Test
    fun `parsePaymentResult resolves success over Cancel button and merges partial fields`() {
        val partial1 = UssdDialogAnalyzer.parsePaymentResult("Paid to Suresh Kirana | Amount: Rs 120.00 | Cancel")
        val partial2 = UssdDialogAnalyzer.parsePaymentResult("Transaction Successful | UPI Ref ID: 432198765432 | Cancel")
        val merged = partial2.mergeWithPrevious(partial1)

        assertEquals(UssdDetectionStatus.SUCCESS, merged.status)
        assertEquals("120.00", merged.amount)
        assertEquals("432198765432", merged.reference)
        assertEquals("Suresh Kirana", merged.recipientName)
    }

    @Test
    fun `parsePaymentResult classifies negated success messages as FAILED`() {
        val failed = UssdDialogAnalyzer.parsePaymentResult("Amount not debited. Payment unsuccessful.")
        assertEquals(UssdDetectionStatus.FAILED, failed.status)
    }
}
