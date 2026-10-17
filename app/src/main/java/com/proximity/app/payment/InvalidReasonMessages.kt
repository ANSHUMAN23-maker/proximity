package com.proximity.app.payment

import android.content.Context
import com.proximity.app.R
import com.proximity.app.constants.AppConstants
import com.proximity.app.utils.CurrencyFormat
fun Upi123CallStringBuilder.Reason.messageFor(context: Context): String = when (this) {
    Upi123CallStringBuilder.Reason.SERVICE_NUMBER ->
        context.getString(R.string.error_invalid_service_number)

    Upi123CallStringBuilder.Reason.RECIPIENT_NUMBER ->
        context.getString(R.string.error_invalid_phone_number)

    Upi123CallStringBuilder.Reason.AMOUNT_NOT_WHOLE_RUPEES ->
        context.getString(R.string.error_amount_whole_rupees_only)

    Upi123CallStringBuilder.Reason.AMOUNT_NOT_A_NUMBER ->
        context.getString(R.string.error_invalid_amount)

    Upi123CallStringBuilder.Reason.AMOUNT_BELOW_MINIMUM ->
        context.getString(
            R.string.error_amount_below_minimum,
            CurrencyFormat.inr(AppConstants.MIN_AMOUNT_VALUE)
        )

    Upi123CallStringBuilder.Reason.AMOUNT_ABOVE_CAP ->
        context.getString(
            R.string.error_amount_above_123pay_cap,
            CurrencyFormat.inr(AppConstants.UPI123PAY_MAX_AMOUNT)
        )
}
