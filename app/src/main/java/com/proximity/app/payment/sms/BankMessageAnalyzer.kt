// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.payment.sms

import com.proximity.app.utils.CurrencyFormat
import java.math.BigDecimal
import java.math.RoundingMode

object BankMessageAnalyzer {

    enum class MessageDirection {
        DEBIT,
        CREDIT,
        FAILED,
        IRRELEVANT
    }

    data class AnalyzedBankMessage(
        val bankDisplayName: String,
        val direction: MessageDirection,
        val amount: BigDecimal?,
        val referenceId: String?,
        val vpa: String?,
        val recipientName: String?,
        val privacySafeExcerpt: String
    )

    private data class BankKeywordSpec(
        val displayName: String,
        val senderPatterns: List<Regex>,
        val bodyPatterns: List<Regex>
    )

    private val KNOWN_BANK_SPECS: List<BankKeywordSpec> = listOf(
        listOf("hdfcbk", "hdfcbank", "hdfc bank", "hdfc") to "HDFC Bank",
        listOf("icicib", "icicibank", "icici bank", "icici") to "ICICI Bank",
        listOf("sbiinb", "sbipsg", "sbiupi", "sbipsb", "state bank of india", "sbi") to "State Bank of India",
        listOf("axisbk", "axisbank", "axis bank", "axis") to "Axis Bank",
        listOf("kotakb", "kotak bank", "kotak mahindra", "kotak") to "Kotak Mahindra Bank",
        listOf("pnb", "punjab national", "punbsm") to "Punjab National Bank",
        listOf("baroda", "bobtxn", "bobupi", "bank of baroda", "bob") to "Bank of Baroda",
        listOf("idfcfirst", "idfcfb", "idfc first") to "IDFC First Bank",
        listOf("yesbnk", "yesbank", "yes bank") to "Yes Bank",
        listOf("paytmb", "pytmbk", "paytm payments bank") to "Paytm Payments Bank",
        listOf("unionb", "uboi", "union bank") to "Union Bank of India",
        listOf("canbnk", "canara bank", "canara") to "Canara Bank",
        listOf("indusb", "indusind") to "IndusInd Bank",
        listOf("fedbnk", "federal bank") to "Federal Bank",
        listOf("idbibk", "idbi bank", "idbi") to "IDBI Bank"
    ).map { (keywords, displayName) ->
        val senderRegexes = keywords.map { kw ->
            Regex("""\b${Regex.escape(kw)}\b""", RegexOption.IGNORE_CASE)
        }
        // Short common words like "bob" are excluded from raw SMS body matching unless paired in sender/full phrase.
        val bodyRegexes = keywords.filterNot { it == "bob" }.map { kw ->
            Regex("""\b${Regex.escape(kw)}\b""", RegexOption.IGNORE_CASE)
        }
        BankKeywordSpec(displayName, senderRegexes, bodyRegexes)
    }

    private val DLT_SENDER_REGEX = Regex("^(?:[A-Z]{2}-[A-Z0-9]{5,8}(?:-[A-Z])?|[A-Z]{6})$", RegexOption.IGNORE_CASE)

    private val FAILURE_KEYWORDS = listOf(
        "failed", "declined", "unsuccessful", "could not be processed",
        "rejected", "reversed", "timed out", "not processed"
    )

    private val EXPLICIT_DEBIT_REGEX = Regex(
        """\b(debited|deducted|withdrawn|transferred|dr\.?\s+from|debited\s+from|debited\s+for|debited\s+by)\b""",
        RegexOption.IGNORE_CASE
    )

    private val DEBIT_REGEX = Regex(
        """\b(debited|deducted|withdrawn|transferred|dr\.?\s+from|debited\s+from|debited\s+for|debited\s+by|(?:sent|paid)(?!\s+by))\b""",
        RegexOption.IGNORE_CASE
    )

    private val CREDIT_REGEX = Regex(
        """\b(credited|received|deposited)\b""",
        RegexOption.IGNORE_CASE
    )

    private val BALANCE_LOOKBEHIND_REGEX = Regex(
        """(avl\.?\s*bal|available\s*bal|avail\.?\s*bal|bal\.?\s*is|clear\s*bal|total\s*bal|closing\s*bal|balance)[^;\n\u20B9]{0,24}$""",
        RegexOption.IGNORE_CASE
    )

    private val AMOUNT_CANDIDATE_REGEX = Regex(
        """(?:rs\.?|inr|\u20B9)\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    private val REF_REGEX = Regex(
        """(?:upi\s*/|upi\s*ref(?:\s*no\.?)?|ref(?:\s*no\.?|\s*id)?|utr|rrn|txn(?:\s*id)?)[:\s#/-]*([A-Z0-9]{6,22})""",
        RegexOption.IGNORE_CASE
    )

    private val VPA_EXTRACTION_REGEX = Regex("""([a-zA-Z0-9.\-_]{2,64}@[a-zA-Z][a-zA-Z0-9]{1,32})""")

    private val RECIPIENT_PATTERNS = listOf(
        Regex(
            """(?:\bto\s+(?:vpa\s+)?|\bpaid\s+to\s+|\btrf\s+to\s+|;\s*)([A-Za-z][A-Za-z0-9\s.&'-]{2,35}?)(?=\s+(?:credited|on\b|ref\b|upi\b|utr\b|via\b|\(|\.|;|$))""",
            RegexOption.IGNORE_CASE
        ),
        Regex("""\bto\s+([A-Za-z][A-Za-z\s]{2,28})(?:\s+on|\s+ref|\.|$)""", RegexOption.IGNORE_CASE)
    )

    private val HONORIFIC_PREFIX_REGEX = Regex("""^(?:mr\.?|mrs\.?|ms\.?|m/s\.?|dr\.?|shri|smt)\s+""", RegexOption.IGNORE_CASE)

    fun identifyBank(sender: String?, body: String): String? {
        val cleanSender = sender?.trim().orEmpty()
        for (spec in KNOWN_BANK_SPECS) {
            if (cleanSender.isNotEmpty() && spec.senderPatterns.any { it.containsMatchIn(cleanSender) }) {
                return spec.displayName
            }
        }
        for (spec in KNOWN_BANK_SPECS) {
            if (spec.bodyPatterns.any { it.containsMatchIn(body) }) {
                return spec.displayName
            }
        }
        if (DLT_SENDER_REGEX.matches(cleanSender)) {
            return "Bank ($cleanSender)"
        }
        return null
    }

    fun analyze(sender: String?, body: String): AnalyzedBankMessage? {
        if (body.isBlank()) return null
        val bankName = identifyBank(sender, body) ?: return null
        val lowerBody = body.lowercase()

        // Explicit account-debit verbs take precedence over CREDIT so dual-sided templates
        // ("Acct XX556 debited for Rs 500 ...; KIRANA STORE credited") are classified as DEBIT,
        // while incoming transfers with "credited to your A/c" remain CREDIT.
        val isFailure = FAILURE_KEYWORDS.any { lowerBody.contains(it) }
        val hasExplicitDebit = EXPLICIT_DEBIT_REGEX.containsMatchIn(body)
        val hasDebit = DEBIT_REGEX.containsMatchIn(body)
        val hasCredit = CREDIT_REGEX.containsMatchIn(body)

        val direction = when {
            isFailure -> MessageDirection.FAILED
            hasExplicitDebit -> MessageDirection.DEBIT
            hasCredit -> MessageDirection.CREDIT
            hasDebit -> MessageDirection.DEBIT
            else -> MessageDirection.IRRELEVANT
        }
        if (direction == MessageDirection.IRRELEVANT) return null

        val amount = extractTransactionAmount(body)
        val referenceId = extractReferenceId(body)
        val vpa = VPA_EXTRACTION_REGEX.find(body)?.groupValues?.get(1)
        val recipientName = extractCleanRecipientName(body, vpa)

        val formattedAmt = amount?.let { CurrencyFormat.formatWithSymbol(it) } ?: "\u20B90.00"
        val cleanBank = if (bankName.startsWith("Bank (")) "Bank" else bankName
        val excerpt = when (direction) {
            MessageDirection.DEBIT -> "$formattedAmt debited \u2014 $cleanBank"
            MessageDirection.FAILED -> "$formattedAmt payment failed \u2014 $cleanBank"
            MessageDirection.CREDIT -> "$formattedAmt credited \u2014 $cleanBank"
            MessageDirection.IRRELEVANT -> ""
        }

        return AnalyzedBankMessage(
            bankDisplayName = cleanBank,
            direction = direction,
            amount = amount,
            referenceId = referenceId,
            vpa = vpa,
            recipientName = recipientName,
            privacySafeExcerpt = excerpt
        )
    }

    fun extractTransactionAmount(body: String): BigDecimal? {
        for (match in AMOUNT_CANDIDATE_REGEX.findAll(body)) {
            val startIdx = match.range.first
            val lookbehindStart = (startIdx - 32).coerceAtLeast(0)
            val lookbehindPrefix = body.substring(lookbehindStart, startIdx)
            if (BALANCE_LOOKBEHIND_REGEX.containsMatchIn(lookbehindPrefix)) {
                continue
            }
            val rawDigits = match.groupValues[1].replace(",", "")
            val parsed = rawDigits.toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_EVEN)
            if (parsed != null && parsed > BigDecimal.ZERO) {
                return parsed
            }
        }
        return null
    }

    fun extractReferenceId(body: String): String? {
        val excludedWords = setOf(
            "SUCCESS", "SUCCESSFUL", "COMPLETED", "FAILED", "DECLINED",
            "DEBITED", "CREDITED", "ACCOUNT", "PAYMENT", "BANKING"
        )
        for (match in REF_REGEX.findAll(body)) {
            val candidate = match.groupValues[1].trim()
            if (candidate.uppercase() !in excludedWords && candidate.any { it.isDigit() }) {
                return candidate
            }
        }
        return null
    }

    fun extractCleanRecipientName(body: String, vpa: String?): String? {
        for (pattern in RECIPIENT_PATTERNS) {
            val match = pattern.find(body)
            if (match != null) {
                var raw = match.groupValues[1].trim()
                if (vpa != null && raw.equals(vpa, ignoreCase = true)) continue
                raw = HONORIFIC_PREFIX_REGEX.replace(raw, "").trim()
                raw = raw.replace(Regex("""\s+"""), " ").trim('.', ',', '-', ';')
                if (raw.length in 2..40 && !raw.contains('@')) {
                    return raw
                }
            }
        }
        return null
    }

    fun amountsMatchPaiseExact(expectedStr: String?, actual: BigDecimal?): Boolean {
        if (expectedStr.isNullOrBlank()) return true
        val expected = expectedStr.trim().replace(",", "").replace("\u20B9", "").toBigDecimalOrNull()
            ?: return true
        if (actual == null) return false
        return expected.setScale(2, RoundingMode.HALF_EVEN)
            .compareTo(actual.setScale(2, RoundingMode.HALF_EVEN)) == 0
    }
}
