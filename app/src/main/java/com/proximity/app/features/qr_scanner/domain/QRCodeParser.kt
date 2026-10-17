package com.proximity.app.features.qr_scanner.domain

import android.content.Context
import android.net.Uri
import android.util.Log
import com.proximity.app.R
import com.proximity.app.data.UPIData

object QRCodeParser {

    private const val TAG = "QRCodeParser"
    private val VPA_REGEX = Regex("^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z][a-zA-Z0-9]{1,64}$")

    private const val MAX_QR_AMOUNT = 100_000.0

    enum class Reason {
        EMPTY,
        NOT_A_UPI_QR,
        MALFORMED,
        NO_PAYEE_ADDRESS,
        INVALID_PAYEE_ADDRESS,
        INVALID_AMOUNT,
    }

    sealed class ParseResult {
        data class Valid(val data: UPIData) : ParseResult()
        data class Invalid(val reason: Reason) : ParseResult()
    }

    fun parse(qrCode: String): ParseResult {
        val raw = qrCode.trim()
        if (raw.isEmpty()) return ParseResult.Invalid(Reason.EMPTY)

        return when {
            raw.startsWith("upi://", ignoreCase = true) -> parseUpiUri(raw)
            VPA_REGEX.matches(raw) -> ParseResult.Valid(
                UPIData(vpa = raw, payeeName = "", amount = "", transactionNote = "", currency = "INR")
            )
            else -> ParseResult.Invalid(Reason.NOT_A_UPI_QR)
        }
    }

    private fun parseUpiUri(raw: String): ParseResult {
        val uri = try {
            Uri.parse(raw)
        } catch (e: Exception) {
            return ParseResult.Invalid(Reason.MALFORMED)
        }

        val vpa = try {
            uri.getQueryParameter("pa")?.trim().orEmpty()
        } catch (e: UnsupportedOperationException) {
            return ParseResult.Invalid(Reason.MALFORMED)
        }
        if (vpa.isEmpty()) return ParseResult.Invalid(Reason.NO_PAYEE_ADDRESS)
        if (!VPA_REGEX.matches(vpa)) {
            Log.w(TAG, "Rejected structurally invalid VPA in QR")
            return ParseResult.Invalid(Reason.INVALID_PAYEE_ADDRESS)
        }

        val amountParam = uri.getQueryParameter("am")?.trim().orEmpty()
        if (amountParam.isNotEmpty()) {
            val amount = amountParam.toDoubleOrNull()
            if (amount == null || amount <= 0 || amount > MAX_QR_AMOUNT) {
                return ParseResult.Invalid(Reason.INVALID_AMOUNT)
            }
            // At most two decimal places per the NPCI spec
            if (amountParam.matches(Regex("^[0-9]+(\\.[0-9]{1,2})?$")).not()) {
                return ParseResult.Invalid(Reason.INVALID_AMOUNT)
            }
        }

        val payeeName = uri.getQueryParameter("pn")
            ?.trim()
            ?.replace(Regex("[\\p{Cntrl}]"), "")
            ?.take(99)
            .orEmpty()
        val note = uri.getQueryParameter("tn")?.trim()?.take(99).orEmpty()

        return ParseResult.Valid(
            UPIData(
                vpa = vpa,
                payeeName = payeeName,
                amount = amountParam,
                transactionNote = note,
                currency = "INR"
            )
        )
    }

    fun isValidUPIQRCode(qrCode: String): Boolean = parse(qrCode) is ParseResult.Valid

    /**
     * Legacy adapter for call sites built around "empty VPA means invalid".
     * Prefer [parse] â€” it carries the rejection reason.
     */
    fun parseUPIQRCode(qrCode: String): UPIData {
        return when (val result = parse(qrCode)) {
            is ParseResult.Valid -> result.data
            is ParseResult.Invalid -> {
                Log.w(TAG, "QR rejected: ${result.reason.name}")
                UPIData(vpa = "", payeeName = "", amount = "", transactionNote = "", currency = "INR")
            }
        }
    }
}

fun QRCodeParser.Reason.messageFor(context: Context): String = when (this) {
    QRCodeParser.Reason.EMPTY -> context.getString(R.string.qr_reason_empty)
    QRCodeParser.Reason.NOT_A_UPI_QR -> context.getString(R.string.qr_reason_not_upi)
    QRCodeParser.Reason.MALFORMED -> context.getString(R.string.qr_reason_malformed)
    QRCodeParser.Reason.NO_PAYEE_ADDRESS -> context.getString(R.string.qr_reason_no_payee)
    QRCodeParser.Reason.INVALID_PAYEE_ADDRESS -> context.getString(R.string.qr_reason_invalid_payee)
    QRCodeParser.Reason.INVALID_AMOUNT -> context.getString(R.string.qr_reason_invalid_amount)
}
