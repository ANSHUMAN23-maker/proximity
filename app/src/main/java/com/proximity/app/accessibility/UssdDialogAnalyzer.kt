// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.accessibility

object UssdDialogAnalyzer {

    private val SUCCESS_KEYWORDS = listOf(
        "payment successful", "transaction successful", "payment success",
        "txn successful", "transaction status: successful", "payment completed",
        "transaction completed", "amount debited", "amount credited", "txn status: success",
        "payment processed", "transaction processed", "txn succeeded", "payment succeeded",
        "transaction succeeded", "\u20B9 debited", "rs debited", "inr debited",
        "transaction complete", "payment complete", "successfully paid",
        "successfully sent", "successful", "success", "succeeded", "debited"
    )

    private val FAILED_KEYWORDS = listOf(
        "payment failed", "transaction failed", "txn failed", "insufficient balance",
        "insufficient funds", "invalid pin", "wrong pin", "incorrect pin",
        "transaction declined", "payment declined", "txn declined", "txn rejected",
        "network error", "server error", "technical error", "user cancelled",
        "cancelled by user", "failed", "failure", "declined", "rejected",
        "insufficient", "timeout", "timed out", "unsuccessful"
    )

    private val AMOUNT_PATTERNS = listOf(
        Regex("""(?:\u20B9|\b(?:Rs\.?|INR|rupees?))\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""(?:\u20B9|\b(?:amount|amt|rs))\s*:?\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""\bdebited\s+(?:of\s+)?(?:\u20B9|rs\.?|inr)?\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""([\d,]+(?:\.\d{1,2})?)\s*(?:debited|credited|deducted)\b""", RegexOption.IGNORE_CASE)
    )

    private val NEGATIVE_SUCCESS_PHRASES = listOf(
        "not debited", "not deducted", "not successful", "unsuccessful", "not completed"
    )

    private val REF_PATTERNS = listOf(
        Regex("""(?:ref(?:erence)?(?:\s*id|\s*no\.?)?|txn(?:\s*id)?|utr|rrn)[:.\s#-]*([A-Z0-9]{6,22})""", RegexOption.IGNORE_CASE),
        Regex("""\b([A-Z0-9]{12,22})\b""")
    )

    private val EXCLUDED_REF_WORDS = setOf(
        "SUCCESSFUL", "SUCCESS", "FAILURE", "FAILED", "PENDING",
        "COMPLETED", "DECLINED", "PAYMENT", "TRANSACTION", "NUMBER",
        "AMOUNT", "CONFIRM", "CANCELLED", "CANCELED", "REJECTED"
    )

    fun parsePaymentResult(text: String): UssdDetectionResult {
        val lower = text.lowercase()

        val hasNegatedSuccess = NEGATIVE_SUCCESS_PHRASES.any { lower.contains(it) }
        val isSuccess = !hasNegatedSuccess && SUCCESS_KEYWORDS.any { lower.contains(it) }
        val isFailed = hasNegatedSuccess || FAILED_KEYWORDS.any { lower.contains(it) }

        val status = when {
            isFailed -> UssdDetectionStatus.FAILED
            isSuccess -> UssdDetectionStatus.SUCCESS
            else -> UssdDetectionStatus.UNKNOWN
        }

        val amount = extractAmount(text)
        val reference = extractReference(text)
        val recipientName = extractRecipientName(text)

        return UssdDetectionResult(
            status = status,
            amount = amount,
            reference = reference,
            recipientName = recipientName,
            rawText = text
        )
    }

    private fun extractAmount(text: String): String? {
        for (regex in AMOUNT_PATTERNS) {
            val match = regex.find(text)
            if (match != null) {
                val value = match.groupValues.getOrNull(1)?.replace(",", "")
                if (!value.isNullOrBlank()) {
                    return value
                }
            }
        }
        return null
    }

    private fun extractReference(text: String): String? {
        for (pattern in REF_PATTERNS) {
            for (match in pattern.findAll(text)) {
                val candidate = match.groupValues.getOrNull(1)?.trim().orEmpty()
                if (candidate.isNotEmpty() &&
                    candidate.uppercase() !in EXCLUDED_REF_WORDS &&
                    candidate.any { it.isDigit() }
                ) {
                    return candidate
                }
            }
        }
        return null
    }

    private fun extractRecipientName(text: String): String? {
        val recipientPattern = Regex(
            """\b(?:paid\s+to|sent\s+to|payee(?:\s*name)?|beneficiary|to)[:.\s]+([A-Za-z][A-Za-z0-9\s.&'-]{2,35}?)(?=\s+(?:\u20B9|rs\.?|inr|for|ref|txn|utr|on|\.|\||\n|$))""",
            RegexOption.IGNORE_CASE
        )
        return recipientPattern.find(text)?.groupValues?.get(1)
            ?.trim()
            ?.takeIf { !it.equals("cancel", ignoreCase = true) && !it.equals("ok", ignoreCase = true) }
    }
}
