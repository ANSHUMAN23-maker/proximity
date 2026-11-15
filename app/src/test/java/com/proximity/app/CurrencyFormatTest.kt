// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app

import com.proximity.app.utils.CurrencyFormat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class CurrencyFormatTest {

    @Test
    fun `formatWithSymbol formats strict 3-2-2 Indian grouping and HALF_EVEN rounding`() {
        assertEquals("\u20B90.00", CurrencyFormat.formatWithSymbol(BigDecimal.ZERO))
        assertEquals("\u20B9500.00", CurrencyFormat.formatWithSymbol(BigDecimal("500")))
        assertEquals("\u20B94,999.00", CurrencyFormat.formatWithSymbol(BigDecimal("4999")))
        assertEquals("\u20B91,00,000.00", CurrencyFormat.formatWithSymbol(BigDecimal("100000")))
        assertEquals("\u20B912,34,567.89", CurrencyFormat.formatWithSymbol(BigDecimal("1234567.89")))
        assertEquals("\u20B910.12", CurrencyFormat.formatWithSymbol(BigDecimal("10.125")))
        assertEquals("\u20B910.14", CurrencyFormat.formatWithSymbol(BigDecimal("10.135")))
        assertEquals("+91 987 654 3210", CurrencyFormat.formatPhoneForDisplay("9876543210"))
    }
}
