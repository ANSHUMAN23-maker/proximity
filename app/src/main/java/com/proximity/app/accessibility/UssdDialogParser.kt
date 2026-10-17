// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.accessibility

import com.proximity.app.payment.sms.SimpleTransaction
import java.util.Locale

object UssdDialogParser {

    private val SUCCESS_KEYWORDS = listOf(
        "successful", "successfully", "completed", "paid to",
        "sent to", "transferred to", "txn successful", "payment successful", "success"
    )

    private val FAILURE_KEYWORDS = listOf(
        "failed", "failure", "declined", "rejected",
        "unsuccessful", "incorrect", "cancelled", "expired", "timed out"
    )

    // Amount patterns
    private val AMOUNT_PATTERNS = listOf(
        Regex("""(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""amount\s*(?:of)?\s*(?:Rs\.?|INR|₹)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""([0-9,]+(?:\.[0-9]{1,2})?)\s*(?:Rs\.?|INR|₹)""", RegexOption.IGNORE_CASE)
    )

    // Reference ID patterns
    private val REF_PATTERNS = listOf(
        Regex("""(?:Ref(?:\s*No|\s*ID|\s*Num)?|UPI\s*Ref|Txn\s*ID|Reference)\s*[:\-]?\s*([A-Za-z0-9]{6,18})""", RegexOption.IGNORE_CASE),
        Regex("""\b([0-9]{8,18})\b""")
    )

    // Recipient patterns
    private val RECIPIENT_PATTERNS = listOf(
        Regex("""(?:sent|paid|transferred)\s+to\s+([a-zA-Z0-9\s.]+?)(?:\s+was|\s+Ref|\s+via|\s+of|[.]|\n|;|$)""", RegexOption.IGNORE_CASE),
        Regex("""to\s+([a-zA-Z0-9\s\.]+?)\s+(?:was|successful|Ref|of|\.)""", RegexOption.IGNORE_CASE),
        Regex("""payment\s+to\s+([a-zA-Z0-9\s\.]+?)(?:\s+of|\s+was|\.)""", RegexOption.IGNORE_CASE),
        Regex("""\bto\s+(\d{10})\b""")
    )

    fun isUssdDialogText(text: String): Boolean {
        val lower = text.lowercase(Locale.getDefault())
        return SUCCESS_KEYWORDS.any { lower.contains(it) } || FAILURE_KEYWORDS.any { lower.contains(it) }
    }

    fun parse(
        rawText: String,
        fallbackPhone: String? = null,
        fallbackAmount: String? = null,
        clock: () -> Long = System::currentTimeMillis
    ): SimpleTransaction? {
        if (!isUssdDialogText(rawText)) return null

        val lowerText = rawText.lowercase(Locale.getDefault())
        val isFailure = FAILURE_KEYWORDS.any { lowerText.contains(it) }
        val isSuccess = !isFailure && SUCCESS_KEYWORDS.any { lowerText.contains(it) }

        if (!isSuccess && !isFailure) return null

        val status = if (isSuccess) "SUCCESS" else "FAILED"

        // Extract Amount
        var amount: String? = null
        for (pattern in AMOUNT_PATTERNS) {
            val match = pattern.find(rawText)
            if (match != null) {
                amount = match.groupValues[1].replace(",", "")
                break
            }
        }
        if (amount == null) amount = fallbackAmount ?: "0"

        // Extract Reference Number
        var refNumber: String? = null
        for (pattern in REF_PATTERNS) {
            val match = pattern.find(rawText)
            if (match != null) {
                val candidate = match.groupValues[1]
                if (candidate.length >= 6) {
                    refNumber = candidate
                    break
                }
            }
        }
        val transactionId = refNumber ?: "USSD_${clock()}"

        // Extract Recipient Name
        var recipientName: String? = null
        for (pattern in RECIPIENT_PATTERNS) {
            val match = pattern.find(rawText)
            if (match != null) {
                recipientName = match.groupValues[1].trim()
                if (recipientName.isNotEmpty()) break
            }
        }

        return SimpleTransaction(
            transactionId = transactionId,
            amount = amount,
            status = status,
            bankName = "USSD *99#",
            smsExcerpt = rawText.take(150),
            timestamp = clock(),
            transactionType = "DEBIT",
            recipientName = recipientName,
            phoneNumber = fallbackPhone
        )
    }
}
