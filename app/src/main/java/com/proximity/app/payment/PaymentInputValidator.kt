package com.proximity.app.payment

import com.proximity.app.constants.AppConstants

object PaymentInputValidator {

    fun isValidPhoneNumber(phoneNumber: String?): Boolean {
        if (phoneNumber.isNullOrBlank()) return false
        return phoneNumber.matches(Regex(AppConstants.PHONE_NUMBER_PATTERN))
    }

    fun isValidAmount(amount: String?): Boolean {
        val amountValue = amount?.toDoubleOrNull() ?: return false
        return amountValue >= AppConstants.MIN_AMOUNT_VALUE && amountValue <= AppConstants.MAX_AMOUNT_VALUE
    }
}
