// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.payment

import java.math.BigDecimal
import java.net.URLDecoder

object QrCodeDecoder {

    private val VPA_REGEX = Regex("^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z][a-zA-Z0-9]{1,64}$")
    val MAX_QR_AMOUNT = BigDecimal("100000.00")

    enum class DecodeReason {
        EMPTY,
        NOT_UPI,
        MALFORMED,
        NO_PAYEE,
        INVALID_PAYEE,
        INVALID_AMOUNT
    }

    sealed class DecodeResult {
        data class Success(
            val vpa: String,
            val payeeName: String?,
            val amount: String?,
            val transactionNote: String?
        ) : DecodeResult()

        data class Invalid(val reason: DecodeReason) : DecodeResult()
    }

    fun decode(rawContent: String?): DecodeResult {
        val trimmed = rawContent?.trim() ?: ""
        if (trimmed.isEmpty()) {
            return DecodeResult.Invalid(DecodeReason.EMPTY)
        }

        // Standalone VPA support
        if (!trimmed.contains("://") && trimmed.contains("@") && !trimmed.contains(" ")) {
            return if (VPA_REGEX.matches(trimmed)) {
                DecodeResult.Success(
                    vpa = trimmed,
                    payeeName = null,
                    amount = null,
                    transactionNote = null
                )
            } else {
                DecodeResult.Invalid(DecodeReason.INVALID_PAYEE)
            }
        }

        if (!trimmed.startsWith("upi://", ignoreCase = true)) {
            return DecodeResult.Invalid(DecodeReason.NOT_UPI)
        }

        val queryStart = trimmed.indexOf('?')
        if (queryStart < 0 || queryStart == trimmed.length - 1) {
            return DecodeResult.Invalid(DecodeReason.MALFORMED)
        }

        val authorityAndPath = trimmed.substring(6, queryStart).lowercase()
        if (authorityAndPath != "pay" && authorityAndPath != "pay/") {
            return DecodeResult.Invalid(DecodeReason.NOT_UPI)
        }

        val queryString = trimmed.substring(queryStart + 1).substringBefore('#')
        val params = mutableMapOf<String, String>()
        try {
            for (pair in queryString.split('&')) {
                if (pair.isBlank()) continue
                val eqIdx = pair.indexOf('=')
                if (eqIdx <= 0) continue
                val key = URLDecoder.decode(pair.substring(0, eqIdx), "UTF-8").lowercase().trim()
                val value = URLDecoder.decode(pair.substring(eqIdx + 1), "UTF-8").trim()
                if (key.isNotEmpty() && !params.containsKey(key)) {
                    params[key] = value
                }
            }
        } catch (_: Exception) {
            return DecodeResult.Invalid(DecodeReason.MALFORMED)
        }

        val pa = params["pa"]?.trim().orEmpty()
        if (pa.isEmpty()) {
            return DecodeResult.Invalid(DecodeReason.NO_PAYEE)
        }
        if (!VPA_REGEX.matches(pa)) {
            return DecodeResult.Invalid(DecodeReason.INVALID_PAYEE)
        }

        val rawAm = params["am"]?.trim().orEmpty()
        val validatedAmount: String? = if (rawAm.isNotEmpty()) {
            if (rawAm.contains('e', ignoreCase = true)) {
                return DecodeResult.Invalid(DecodeReason.INVALID_AMOUNT)
            }
            val bd = rawAm.toBigDecimalOrNull() ?: return DecodeResult.Invalid(DecodeReason.INVALID_AMOUNT)
            if (bd <= BigDecimal.ZERO || bd > MAX_QR_AMOUNT) {
                return DecodeResult.Invalid(DecodeReason.INVALID_AMOUNT)
            }
            if (bd.stripTrailingZeros().scale() > 2) {
                return DecodeResult.Invalid(DecodeReason.INVALID_AMOUNT)
            }
            bd.stripTrailingZeros().toPlainString()
        } else {
            null
        }

        val sanitizedPn = params["pn"]
            ?.replace(Regex("[\\p{Cntrl}]"), "")
            ?.trim()
            ?.take(99)
            ?.takeIf { it.isNotEmpty() }

        val sanitizedTn = params["tn"]
            ?.replace(Regex("[\\p{Cntrl}]"), "")
            ?.trim()
            ?.take(99)
            ?.takeIf { it.isNotEmpty() }

        return DecodeResult.Success(
            vpa = pa,
            payeeName = sanitizedPn,
            amount = validatedAmount,
            transactionNote = sanitizedTn
        )
    }
}
