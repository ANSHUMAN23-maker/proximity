// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app

import com.proximity.app.payment.SmsDeduplicator
import com.proximity.app.payment.sms.BankMessageAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class SmsParserTest {

    @Test
    fun `BankMessageAnalyzer prioritizes DEBIT over CREDIT and filters Avl Bal lookbehind`() {
        val sms = "Avl Bal Rs 14,250.00. Acct XX556 debited for Rs 500.00 on 11-Nov-26; Mr. Ramesh Kumar credited. UPI Ref No 631512345678."
        val analyzed = BankMessageAnalyzer.analyze("AD-HDFCBK", sms)
        assertNotNull(analyzed)
        assertEquals(BankMessageAnalyzer.MessageDirection.DEBIT, analyzed!!.direction)
        assertEquals("HDFC Bank", analyzed.bankDisplayName)
        assertEquals(BigDecimal("500.00"), analyzed.amount)
        assertEquals("631512345678", analyzed.referenceId)
        assertEquals("Ramesh Kumar", analyzed.recipientName)
        assertEquals("\u20B9500.00 debited \u2014 HDFC Bank", analyzed.privacySafeExcerpt)
        assertFalse(analyzed.privacySafeExcerpt.contains("XX556"))
    }

    @Test
    fun `SmsDeduplicator deduplicates within 60 second claim window`() {
        val dedup = SmsDeduplicator(60_000L)
        assertTrue(dedup.tryClaim("AD-SBIUPI", "Acct debited by Rs 100.00", 1_000L))
        assertFalse(dedup.tryClaim("AD-SBIUPI", "Acct debited by Rs 100.00", 30_000L))
        assertTrue(dedup.tryClaim("AD-SBIUPI", "Acct debited by Rs 100.00", 62_000L))
    }

    @Test
    fun `BankMessageAnalyzer ignores personal messages mentioning Bob and prioritizes sender ID`() {
        assertNull(BankMessageAnalyzer.analyze("+919876543210", "Hey, sent Rs 500 to Bob for lunch"))

        val bodyOverlap = BankMessageAnalyzer.analyze(
            "AD-HDFCBK",
            "Acct XX123 debited by Rs 250.00 via SBI kiosk. UPI/631512345678."
        )
        assertNotNull(bodyOverlap)
        assertEquals("HDFC Bank", bodyOverlap!!.bankDisplayName)
        assertEquals("631512345678", bodyOverlap.referenceId)
    }

    @Test
    fun `BankMessageAnalyzer skips dated balance lookbehind and treats sent by as credit`() {
        val sms = "Credited INR 450.00 sent by Riya. Avl Bal as on 12-Nov is Rs 14,000.00."
        val analyzed = BankMessageAnalyzer.analyze("VK-ICICIB", sms)
        assertNotNull(analyzed)
        assertEquals(BankMessageAnalyzer.MessageDirection.CREDIT, analyzed!!.direction)
        assertEquals(BigDecimal("450.00"), analyzed.amount)
    }
}
