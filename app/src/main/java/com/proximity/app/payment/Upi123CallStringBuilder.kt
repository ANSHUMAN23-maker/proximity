package com.proximity.app.payment

import com.proximity.app.constants.AppConstants

object Upi123CallStringBuilder {

    private val SERVICE_NUMBER_REGEX = Regex("^0?[1-9][0-9]{9,11}$")
    private val PHONE_REGEX = Regex(AppConstants.PHONE_NUMBER_PATTERN)
    private val WHOLE_RUPEES_REGEX = Regex("^[0-9]{1,6}$")

    enum class Reason {
        SERVICE_NUMBER,
        RECIPIENT_NUMBER,
        AMOUNT_NOT_WHOLE_RUPEES,
        AMOUNT_NOT_A_NUMBER,
        AMOUNT_BELOW_MINIMUM,
        AMOUNT_ABOVE_CAP,
    }

    sealed class Result {
        data class Valid(val callString: String) : Result()
        data class Invalid(val reason: Reason) : Result()
    }

    fun build(serviceNumber: String, phoneNumber: String, amount: String): Result {
        val service = serviceNumber.filter(Char::isDigit)
        val phone = phoneNumber.filter(Char::isDigit)
        val rupees = amount.trim()

        if (!SERVICE_NUMBER_REGEX.matches(service)) {
            return Result.Invalid(Reason.SERVICE_NUMBER)
        }
        if (!PHONE_REGEX.matches(phone)) {
            return Result.Invalid(Reason.RECIPIENT_NUMBER)
        }
        // The IVR consumes whole-rupee DTMF digits; decimals cannot be dialled.
        if (!WHOLE_RUPEES_REGEX.matches(rupees)) {
            return Result.Invalid(Reason.AMOUNT_NOT_WHOLE_RUPEES)
        }
        val value = rupees.toLongOrNull()
            ?: return Result.Invalid(Reason.AMOUNT_NOT_A_NUMBER)
        if (value < AppConstants.MIN_AMOUNT_VALUE.toLong()) {
            return Result.Invalid(Reason.AMOUNT_BELOW_MINIMUM)
        }
        if (value > AppConstants.UPI123PAY_MAX_AMOUNT.toLong()) {
            return Result.Invalid(Reason.AMOUNT_ABOVE_CAP)
        }

        return Result.Valid("tel:$service,,1,$phone,,$value,,1")
    }
}
