package com.proximity.app.services

import java.util.regex.Pattern

data class ParsedUssdTransaction(
    val isSuccess: Boolean,
    val amount: String?,
    val recipient: String?,
    val refId: String?,
    val rawText: String
)

object UssdTransactionParser {

    private val FAILURE_KEYWORDS = listOf(
        "fail", "declined", "unsuccessful", "invalid", "cancelled",
        "canceled", "timed out", "timeout", "wrong pin", "incorrect pin",
        "insufficient", "error", "unable", "denied"
    )

    // Keywords that indicate this is Proximity's own UI, not a USSD dialog
    private val EXCLUDED_KEYWORDS = listOf(
        "proximity ussd reader", "app info", "use proximity", "shortcut",
        "android setup", "usb debugging", "connect to a network", "wifi, off",
        "paid to", "amount paid", "transaction id", "transaction successful",
        "payment successful"
    )

    private val SUCCESS_KEYWORDS = listOf(
        "success", "successful", "paid", "sent", "transferred", "completed"
    )

    // Amount patterns: Rs. 100, Rs 100.50, ₹100, INR 100
    private val AMOUNT_PATTERNS = listOf(
        Pattern.compile("(?i)(?:rs\\.?|₹|inr)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)"),
        Pattern.compile("(?i)([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:rs\\.?|₹|inr)"),
        Pattern.compile("(?i)(?:paid|sent|transferred|amount)\\s+(?:rs\\.?|₹|inr)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)")
    )

    // Recipient patterns: "to john@upi", "paid to 9876543210", "sent to Merchant Name"
    private val RECIPIENT_PATTERNS = listOf(
        Pattern.compile("([a-zA-Z0-9.\\-_]+@[a-zA-Z0-9]+)"),
        Pattern.compile("(?i)(?:to|paid to|sent to|transferred to)\\s+([A-Za-z0-9._\\-@\\s]+?)(?=\\.|\\(|\\bref\\b|\\btxn\\b|\\bfor\\b|\\bwith\\b|\\bwas\\b|$)")
    )

    // Reference ID patterns: "Ref No 123456789012", "Txn ID: TXN12345", "RRN 123456"
    private val REF_PATTERNS = listOf(
        Pattern.compile("(?i)(?:ref(?:erence)?(?:\\s*no|\\s*id|\\s*num|\\s*number)?|txn(?:\\s*id)?|rrn)\\s*[:.-]?\\s*([A-Za-z0-9]{6,20})")
    )

    fun parse(text: String?): ParsedUssdTransaction {
        if (text.isNullOrBlank()) {
            return ParsedUssdTransaction(
                isSuccess = false,
                amount = null,
                recipient = null,
                refId = null,
                rawText = text.orEmpty()
            )
        }

        val nonNullText = text
        val lower = nonNullText.lowercase()

        // Check for excluded keywords (Proximity UI, system settings, etc.)
        val hasExcluded = EXCLUDED_KEYWORDS.any { lower.contains(it) }
        if (hasExcluded) {
            return ParsedUssdTransaction(
                isSuccess = false,
                amount = null,
                recipient = null,
                refId = null,
                rawText = nonNullText
            )
        }

        // Check for explicit failure keywords next
        val hasFailure = FAILURE_KEYWORDS.any { lower.contains(it) }
        if (hasFailure) {
            return ParsedUssdTransaction(
                isSuccess = false,
                amount = null,
                recipient = null,
                refId = null,
                rawText = nonNullText
            )
        }

        // Check for success keywords
        val hasSuccess = SUCCESS_KEYWORDS.any { lower.contains(it) }
        if (!hasSuccess) {
            return ParsedUssdTransaction(
                isSuccess = false,
                amount = null,
                recipient = null,
                refId = null,
                rawText = nonNullText
            )
        }

        val amount = extractAmount(nonNullText)
        val recipient = extractRecipient(nonNullText)
        val refId = extractRefId(nonNullText)

        return ParsedUssdTransaction(
            isSuccess = true,
            amount = amount,
            recipient = recipient,
            refId = refId,
            rawText = nonNullText
        )
    }

    private fun extractAmount(text: String): String? {
        for (pattern in AMOUNT_PATTERNS) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val rawAmount = matcher.group(1)?.replace(",", "")
                if (!rawAmount.isNullOrBlank()) {
                    return rawAmount
                }
            }
        }
        return null
    }

    private fun extractRecipient(text: String): String? {
        for (pattern in RECIPIENT_PATTERNS) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val candidate = matcher.group(1)?.trim()
                if (!candidate.isNullOrBlank() && candidate.length > 2) {
                    return candidate
                }
            }
        }
        return null
    }

    private fun extractRefId(text: String): String? {
        for (pattern in REF_PATTERNS) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val candidate = matcher.group(1)?.trim()
                if (!candidate.isNullOrBlank()) {
                    return candidate
                }
            }
        }
        return null
    }
}
