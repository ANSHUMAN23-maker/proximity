// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app

import com.proximity.app.payment.QrCodeDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeParserTest {

    @Test
    fun `decode parses valid upi URI and standalone VPA`() {
        val res = QrCodeDecoder.decode("upi://pay?pa=merchant.store@okhdfcbank&pn=Kirana%20Store&am=250.50&tn=Groceries")
        assertTrue(res is QrCodeDecoder.DecodeResult.Success)
        val s = res as QrCodeDecoder.DecodeResult.Success
        assertEquals("merchant.store@okhdfcbank", s.vpa)
        assertEquals("Kirana Store", s.payeeName)
        assertEquals("250.5", s.amount)
        assertEquals("Groceries", s.transactionNote)

        val standalone = QrCodeDecoder.decode("shopkeeper@ybl")
        assertTrue(standalone is QrCodeDecoder.DecodeResult.Success)
        assertEquals("shopkeeper@ybl", (standalone as QrCodeDecoder.DecodeResult.Success).vpa)
    }

    @Test
    fun `decode rejects empty, non-upi, missing payee, and invalid amount`() {
        assertEquals(
            QrCodeDecoder.DecodeResult.Invalid(QrCodeDecoder.DecodeReason.EMPTY),
            QrCodeDecoder.decode("   ")
        )
        assertEquals(
            QrCodeDecoder.DecodeResult.Invalid(QrCodeDecoder.DecodeReason.NOT_UPI),
            QrCodeDecoder.decode("https://example.com")
        )
        assertEquals(
            QrCodeDecoder.DecodeResult.Invalid(QrCodeDecoder.DecodeReason.NO_PAYEE),
            QrCodeDecoder.decode("upi://pay?pn=Shop&am=100")
        )
        assertEquals(
            QrCodeDecoder.DecodeResult.Invalid(QrCodeDecoder.DecodeReason.INVALID_AMOUNT),
            QrCodeDecoder.decode("upi://pay?pa=shop@upi&am=150000")
        )
        assertEquals(
            QrCodeDecoder.DecodeResult.Invalid(QrCodeDecoder.DecodeReason.NOT_UPI),
            QrCodeDecoder.decode("upi://payfake?pa=shop@upi&am=10")
        )
        assertEquals(
            QrCodeDecoder.DecodeResult.Invalid(QrCodeDecoder.DecodeReason.INVALID_AMOUNT),
            QrCodeDecoder.decode("upi://pay?pa=shop@upi&am=1e2")
        )
    }

    @Test
    fun `decode strips URL fragment before parsing query parameters`() {
        val res = QrCodeDecoder.decode("upi://pay?pa=merchant@sbi&pn=Tea%20Stall&am=25.00#ignored")
        assertTrue(res is QrCodeDecoder.DecodeResult.Success)
        val parsed = res as QrCodeDecoder.DecodeResult.Success
        assertEquals("25", parsed.amount)
        assertEquals("merchant@sbi", parsed.vpa)
    }
}
