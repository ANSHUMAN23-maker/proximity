// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.payment

import java.math.BigDecimal

object Upi123InputValidator {
    const val IVR_NUMBER = "08045163666"
    const val JIO_IVR_NUMBER = "08045163581"
    const val MIN_AMOUNT = 1
    const val MAX_AMOUNT = 4999
    private val INDIAN_MOBILE_REGEX = Regex("^[1-9][0-9]{9}$")

    sealed class ValidationResult {
        data class Valid(
            val cleanPhone: String,
            val wholeRupees: Int,
            val dtmfUriString: String
        ) : ValidationResult()

        data class Invalid(val reason: ValidationReason) : ValidationResult()
    }

    enum class ValidationReason {
        INVALID_PHONE,
        EMPTY_OR_NON_NUMERIC_AMOUNT,
        DECIMALS_NOT_ALLOWED,
        BELOW_MINIMUM,
        ABOVE_MAX_CAP
    }

    fun normalizeIndianPhone(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val digits = raw.filter { it.isDigit() }
        return if (digits.length >= 10) digits.takeLast(10) else digits
    }

    fun isValidIndianMobile(raw: String?): Boolean {
        val normalized = normalizeIndianPhone(raw)
        return INDIAN_MOBILE_REGEX.matches(normalized)
    }

    fun validate(rawPhone: String?, rawAmount: String?, isJio: Boolean = false): ValidationResult {
        val cleanPhone = normalizeIndianPhone(rawPhone)
        if (!INDIAN_MOBILE_REGEX.matches(cleanPhone)) {
            return ValidationResult.Invalid(ValidationReason.INVALID_PHONE)
        }

        val trimmedAmount = rawAmount?.trim()?.replace(",", "")?.replace("\u20B9", "") ?: ""
        if (trimmedAmount.isEmpty()) {
            return ValidationResult.Invalid(ValidationReason.EMPTY_OR_NON_NUMERIC_AMOUNT)
        }

        if (trimmedAmount.contains('.')) {
            val bd = trimmedAmount.toBigDecimalOrNull()
                ?: return ValidationResult.Invalid(ValidationReason.EMPTY_OR_NON_NUMERIC_AMOUNT)
            if (bd.stripTrailingZeros().scale() > 0) {
                return ValidationResult.Invalid(ValidationReason.DECIMALS_NOT_ALLOWED)
            }
            return validateBigDecimal(cleanPhone, bd, isJio)
        }

        val bd = trimmedAmount.toBigDecimalOrNull()
            ?: return ValidationResult.Invalid(ValidationReason.EMPTY_OR_NON_NUMERIC_AMOUNT)
        return validateBigDecimal(cleanPhone, bd, isJio)
    }

    private fun validateBigDecimal(cleanPhone: String, bd: BigDecimal, isJio: Boolean): ValidationResult {
        if (bd < BigDecimal(MIN_AMOUNT)) {
            return ValidationResult.Invalid(ValidationReason.BELOW_MINIMUM)
        }
        if (bd > BigDecimal(MAX_AMOUNT)) {
            return ValidationResult.Invalid(ValidationReason.ABOVE_MAX_CAP)
        }
        val whole = bd.intValueExact()
        val dtmf = buildDtmfUriString(cleanPhone, whole, isJio)
        return ValidationResult.Valid(cleanPhone, whole, dtmf)
    }

    fun buildDtmfUriString(phone10Digits: String, wholeRupees: Int, isJio: Boolean = false): String {
        require(INDIAN_MOBILE_REGEX.matches(phone10Digits)) { "Invalid 10-digit Indian phone number" }
        require(wholeRupees in MIN_AMOUNT..MAX_AMOUNT) { "Amount out of UPI 123Pay bounds (1..4999)" }
        val ivrNumber = if (isJio) JIO_IVR_NUMBER else IVR_NUMBER
        return "tel:$ivrNumber,,1,$phone10Digits,,$wholeRupees,,1"
    }
}
