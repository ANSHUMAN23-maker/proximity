// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.utils

import java.math.BigDecimal
import java.math.RoundingMode

object CurrencyFormat {

    fun formatWithSymbol(amount: BigDecimal): String = "\u20B9${formatDigitsOnly(amount)}"

    fun formatWithSymbol(amount: Double): String =
        formatWithSymbol(BigDecimal.valueOf(amount))

    fun formatWithSymbol(amountStr: String?): String {
        val parsed = amountStr?.trim()?.replace(",", "")?.replace("\u20B9", "")?.toBigDecimalOrNull()
            ?: BigDecimal.ZERO
        return formatWithSymbol(parsed)
    }

    fun formatDigitsOnly(amountStr: String?): String {
        val parsed = amountStr?.trim()?.replace(",", "")?.replace("\u20B9", "")?.toBigDecimalOrNull()
            ?: BigDecimal.ZERO
        return formatDigitsOnly(parsed)
    }

    fun formatDigitsOnly(amount: BigDecimal): String {
        val scaled = amount.setScale(2, RoundingMode.HALF_EVEN)
        val isNegative = scaled.signum() < 0
        val absScaled = scaled.abs()
        val plain = absScaled.toPlainString()
        val parts = plain.split(".")
        val intPart = parts[0]
        val decPart = if (parts.size > 1) parts[1].padEnd(2, '0').take(2) else "00"

        val groupedInt = groupIndianDigits(intPart)
        val prefix = if (isNegative) "-" else ""
        return "$prefix$groupedInt.$decPart"
    }

    fun groupIndianDigits(digits: String): String {
        val clean = digits.trimStart('0').ifEmpty { "0" }
        if (clean.length <= 3) return clean
        val lastThree = clean.takeLast(3)
        val rest = clean.dropLast(3)
        val sb = StringBuilder()
        var i = rest.length
        while (i > 0) {
            val start = (i - 2).coerceAtLeast(0)
            if (sb.isNotEmpty()) sb.insert(0, ',')
            sb.insert(0, rest.substring(start, i))
            i = start
        }
        return "$sb,$lastThree"
    }

    fun formatPhoneForDisplay(rawPhone: String?): String {
        val digits = rawPhone?.filter { it.isDigit() }?.takeLast(10) ?: return ""
        if (digits.length != 10) return rawPhone
        return "+91 ${digits.substring(0, 3)} ${digits.substring(3, 6)} ${digits.substring(6, 10)}"
    }
}
