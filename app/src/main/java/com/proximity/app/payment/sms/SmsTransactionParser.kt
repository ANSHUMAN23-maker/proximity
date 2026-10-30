package com.proximity.app.payment.sms

import android.os.Parcelable
import com.proximity.app.data.TransactionStatus
import kotlinx.parcelize.Parcelize
import java.util.Locale

@Parcelize
data class SimpleTransaction(
    val transactionId: String,
    val amount: String,
    val status: String,
    val bankName: String,
    val smsExcerpt: String,
    val timestamp: Long = System.currentTimeMillis(),
    val upiId: String? = null,
    val transactionType: String = "DEBIT",
    val recipientName: String? = null, // who money was sent to
    val phoneNumber: String? = null // phone number if available
) : Parcelable

object SmsTransactionParser {

    private const val NAME_STOP_WORDS =
        "has|have|had|was|were|is|are|will|could|did|does|failed|declined|unsuccessful|not|due"

    private const val NAME_TERMINATOR =
        "(?:\\s+(?:via|@|on|dated|for|from|to|UPI|Ref)\\b|\\s+(?:$NAME_STOP_WORDS)\\b|\\.|,|;|$)"

    // Name extraction patterns - CASE-INSENSITIVE
    private val RECIPIENT_PATTERNS = listOf(
        // Pattern for "sent to NAME" or "paid to NAME"
        "(?:sent|paid|transferred)\\s+to\\s+([a-zA-Z][a-zA-Z\\s\\.]+?)$NAME_TERMINATOR",

        // Pattern for "to NAME via/@ UPI"
        "to\\s+([a-zA-Z][a-zA-Z\\s\\.]+?)\\s+(?:via|@)",

        // Pattern for "to merchant NAME"
        "to\\s+(?:merchant|M/s\\.?|Mr\\.?|Mrs\\.?|Ms\\.?)\\s*([a-zA-Z][a-zA-Z\\s\\.]+?)" +
            "(?:\\s+(?:via|@|on|for)\\b|\\s+(?:$NAME_STOP_WORDS)\\b|\\.|,|;|$)",

        // Pattern for "Payment to NAME of Rs"
        "Payment\\s+to\\s+([a-zA-Z][a-zA-Z\\s\\.]+?)\\s+(?:of|for)\\s+(?:Rs\\.?|INR|\u20B9)",

        // Pattern for "NAME - amount debited"
        "([a-zA-Z][a-zA-Z\\s\\.]+?)\\s*[-â€“]\\s*(?:Rs\\.?|INR|\u20B9)",

        // Additional patterns for common formats
        "(?:Rs\\.?|INR|\u20B9)\\s*[0-9,]+(?:\\.[0-9]{2})?\\s+(?:sent|paid|transferred)\\s+to\\s+" +
            "([a-zA-Z][a-zA-Z\\s\\.]+?)$NAME_TERMINATOR",
        "\\bto\\s+([a-zA-Z][a-zA-Z\\s\\.]{2,40}?)$NAME_TERMINATOR",

        // Pattern for VPA format (name from UPI ID)
        "to\\s+([a-zA-Z][a-zA-Z0-9\\s]+?)@",

        // Pattern for phone numbers - LAST PRIORITY
        "to\\s+(\\d{10})(?:\\s|\\.|,|;|$)",

        "\\b([a-zA-Z][a-zA-Z\\s\\.]{2,40}?)\\s+credited\\b"
    )

    private val SENDER_PATTERNS = listOf(
        // Pattern for "received from NAME"
        "(?:received|credited)\\s+from\\s+([a-zA-Z][a-zA-Z\\s\\.]+?)(?:\\s+(?:via|@|on|for)|\\.|,|;|$)",

        // Pattern for "from NAME via/@ UPI"
        "from\\s+([a-zA-Z][a-zA-Z\\s\\.]+?)\\s+(?:via|@)",

        // Additional pattern for credit messages
        "(?:Rs\\.?|INR|\u20B9)\\s*[0-9,]+(?:\\.[0-9]{2})?\\s+(?:received|credited)\\s+from\\s+" +
            "([a-zA-Z][a-zA-Z\\s\\.]+?)$NAME_TERMINATOR",

        "\\bfrom\\s+([a-zA-Z][a-zA-Z\\s\\.]{2,40}?)$NAME_TERMINATOR",

        // Pattern for sender VPA
        "from\\s+([a-zA-Z][a-zA-Z0-9\\s]+?)@"
    )

    // Bank identifiers - comprehensive list
    private val BANK_KEYWORDS = mapOf(
        "HDFC" to "HDFC Bank",
        "ICICI" to "ICICI Bank",
        "SBI" to "State Bank of India",
        "AXIS" to "Axis Bank",
        "KOTAK" to "Kotak Bank",
        "PNB" to "Punjab National Bank",
        "BOB" to "Bank of Baroda",
        "IDFC" to "IDFC First Bank",
        "YES" to "Yes Bank",
        "PAYTM" to "Paytm Payments Bank",
        "UNION" to "Union Bank",
        "CANARA" to "Canara Bank",
        "IndusInd" to "IndusInd Bank",
        "Federal" to "Federal Bank"
    )

    // Transaction success indicators
    private val SUCCESS_INDICATORS = listOf(
        "successful",
        "successfully",
        "completed",
        "credited",
        "debited",
        "transferred",
        "sent to",
        "received from",
        "payment of",
        "paid to",
        "txn successful"
    )

    private val FAILURE_INDICATORS = listOf(
        "failed",
        "failure",
        "declined",
        "rejected",
        "unsuccessful",
        "not successful",
        "could not be processed",
        "cannot be processed",
        "not processed",
        "not completed",
        "insufficient",
        "reversed",
        "not debited",
        "txn expired",
        "timed out"
    )

    private val DEBIT_INDICATORS = listOf(
        "debited",
        "sent to",
        "paid to",
        "withdrawn",
        "spent",
        "transferred to"
    )

    // Amount patterns - multiple formats
    private val AMOUNT_PATTERNS = listOf(
        "(?:Rs\\.?|INR|\u20B9)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
        "amount\\s*(?:of)?\\s*(?:Rs\\.?|INR|\u20B9)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
        "([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:Rs\\.?|INR|\u20B9)"
    )

    fun parse(
        sender: String,
        body: String,
        expectedAmount: String?,
        clock: () -> Long = System::currentTimeMillis,
        randomSuffix: () -> Int = { (1000..9999).random() }
    ): SimpleTransaction? {
        val bankName = detectBank(sender, body) ?: return null
        if (!isTransactionMessage(body)) return null
        val amount = extractAmount(body) ?: return null

        val transactionId = extractTransactionId(body, clock) ?: generateTransactionId(clock, randomSuffix)
        val upiId = extractUPIId(body)
        val transactionType = detectTransactionType(body)
        val (recipientName, phoneNumber) = extractRecipientInfo(body, transactionType)

        if (transactionType != "CREDIT") {
            if (!describesTransaction(body)) return null

            if (!expectedAmount.isNullOrEmpty() && !isAmountMatching(amount, expectedAmount)) {
                return null
            }
        }

        val status = if (detectsFailure(body)) TransactionStatus.FAILED else TransactionStatus.SUCCESS

        return SimpleTransaction(
            transactionId = transactionId,
            amount = amount,
            status = status,
            bankName = bankName,
            smsExcerpt = buildExcerpt(amount, transactionType, bankName, status),
            timestamp = clock(),
            upiId = upiId,
            transactionType = transactionType,
            recipientName = recipientName,
            phoneNumber = phoneNumber
        )
    }

    private val TRANSACTION_VERBS = listOf(
        "debited", "credited", "sent", "paid", "transferred",
        "withdrawn", "spent", "received", "deducted"
    )

    internal fun describesTransaction(body: String): Boolean {
        val bodyLower = body.lowercase(Locale.getDefault())
        return TRANSACTION_VERBS.any { bodyLower.contains(it) } || detectsFailure(body)
    }

    internal fun detectsFailure(body: String): Boolean {
        val bodyLower = body.lowercase(Locale.getDefault())
        return FAILURE_INDICATORS.any { bodyLower.contains(it) }
    }

    private const val PAISE_PER_RUPEE = 100

    internal fun isAmountMatching(extracted: String, expected: String): Boolean {
        val extractedNum = extracted.replace(",", "").toDoubleOrNull() ?: return false
        val expectedNum = expected.replace(",", "").toDoubleOrNull() ?: return false
        if (expectedNum <= 0.0) return true
        return Math.round(extractedNum * PAISE_PER_RUPEE) == Math.round(expectedNum * PAISE_PER_RUPEE)
    }

    private val AMBIGUOUS_BODY_PHRASES = mapOf(
        "YES" to Regex("\\bYES\\s+BANK\\b"),
        "BOB" to Regex("\\bBANK\\s+OF\\s+BARODA\\b|\\bBOB\\s+BANK\\b")
    )

    internal fun detectBank(sender: String, body: String): String? {
        val senderUpper = sender.uppercase(Locale.getDefault())
        val bodyUpper = body.uppercase(Locale.getDefault())

        for ((keyword, bankName) in BANK_KEYWORDS) {
            if (senderUpper.contains(keyword.uppercase())) {
                return bankName
            }
        }

        for ((keyword, bankName) in BANK_KEYWORDS) {
            val ambiguous = AMBIGUOUS_BODY_PHRASES[keyword.uppercase()]
            val matched = if (ambiguous != null) {
                ambiguous.containsMatchIn(bodyUpper)
            } else {
                Regex("\\b${Regex.escape(keyword.uppercase())}\\b").containsMatchIn(bodyUpper)
            }
            if (matched) {
                return bankName
            }
        }
        if (sender.matches(Regex("^[A-Z]{2}-[A-Z0-9]{6}(-[A-Z])?$")) ||
            sender.matches(Regex("^[A-Z]{6}$")) ||
            sender.matches(Regex("^[0-9]{6}$"))
        ) {
            return "Bank"
        }

        return null
    }

    internal fun claimKey(body: String): String {
        return body.trim().replace(Regex("\\s+"), " ").take(120)
    }

    internal fun isTransactionMessage(body: String): Boolean {
        val bodyLower = body.lowercase(Locale.getDefault())

        for (indicator in SUCCESS_INDICATORS) {
            if (bodyLower.contains(indicator)) {
                return true
            }
        }

        if (detectsFailure(body)) {
            return true
        }

        for (pattern in AMOUNT_PATTERNS) {
            if (Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(body)) {
                return true
            }
        }

        return false
    }

    private val BALANCE_CONTEXT =
        Regex("(?:avl|available|avlbl|a/c|account)?\\s*bal(?:ance)?\\s*[:.]?\\s*$", RegexOption.IGNORE_CASE)

    private const val BALANCE_LOOKBEHIND_CHARS = 24

    internal fun extractAmount(body: String): String? {

        val candidates = AMOUNT_PATTERNS
            .flatMap { pattern ->
                Regex(pattern, RegexOption.IGNORE_CASE).findAll(body).mapNotNull { match ->
                    val amount = match.groups[1]?.value?.replace(",", "")
                    if (amount.isNullOrEmpty()) null else match.range.first to amount
                }
            }
            .sortedBy { it.first }
        if (candidates.isEmpty()) return null

        val nonBalance = candidates.filterNot { (start, _) ->
            BALANCE_CONTEXT.containsMatchIn(body.take(start).takeLast(BALANCE_LOOKBEHIND_CHARS))
        }
        return (nonBalance.ifEmpty { candidates }).first().second
    }

    internal fun extractTransactionId(body: String, clock: () -> Long): String? {
        val patterns = listOf(
            "\\b(?:ref|txn|transaction|id)\\b\\s*(?:no|number|id)?\\s*[:.#]?\\s*([A-Z0-9]+)",
            "([A-Z0-9]{10,})" // Generic pattern for long alphanumeric
        )

        for (pattern in patterns) {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val match = regex.find(body)

            if (match != null && match.groups.size > 1) {
                val baseId = match.groups[1]?.value
                if (!baseId.isNullOrEmpty()) {
                    return baseId
                }
            }
        }

        return null
    }

    internal fun extractUPIId(body: String): String? {
        val patterns = listOf(
            "(?:UPI:|from|to|UPI ID:?)\\s*([a-zA-Z0-9._-]+@[a-zA-Z0-9]+)",
            "(?:VPA:?)\\s*([a-zA-Z0-9._-]+@[a-zA-Z0-9]+)"
        )

        for (pattern in patterns) {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val match = regex.find(body)
            if (match != null && match.groups.size > 1) {
                return match.groups[1]?.value
            }
        }
        return null
    }

    /**
     * DEBIT (money left the user's account) or CREDIT (money arrived).
     *
     * A debit verb wins over a credit word: ICICI/Union-style templates name
     * both sides of the same payment ("Acct XX556 debited for Rs 500.00 â€¦;
     * KIRANA STORE credited"), and reading those as CREDIT would classify
     * our own outgoing confirmation as somebody else's incoming money — the
     * pipeline then drops it as unrelated and the payment never appears.
     */
    internal fun detectTransactionType(body: String): String {
        val bodyLower = body.lowercase(Locale.getDefault())
        return when {
            DEBIT_INDICATORS.any { bodyLower.contains(it) } -> "DEBIT"
            bodyLower.contains("credited") ||
                bodyLower.contains("received") ||
                bodyLower.contains("added") -> "CREDIT"
            else -> "DEBIT"
        }
    }

    internal fun generateTransactionId(clock: () -> Long, randomSuffix: () -> Int): String =
        "TXN${clock()}${randomSuffix()}"

    /** Extract recipient (DEBIT) or sender (CREDIT) name and phone number. */
    internal fun extractRecipientInfo(body: String, transactionType: String): Pair<String?, String?> {
        var recipientName: String? = null
        var phoneNumber: String? = null

        val patterns = if (transactionType == "CREDIT") SENDER_PATTERNS else RECIPIENT_PATTERNS

        for (pattern in patterns) {
            val regex = Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            val match = regex.find(body) ?: continue
            val extracted = match.groups[1]?.value?.trim()
            if (extracted.isNullOrEmpty()) continue

            if (extracted.matches(Regex("\\d{10}"))) {
                phoneNumber = extracted
                continue
            }

            val cleanedName = cleanupName(extracted)
            if (!cleanedName.isNullOrEmpty()) {
                recipientName = cleanedName
                break // Stop if we found a good name
            }
        }

        // If no name found but we have a UPI ID, extract a name from it
        if (recipientName.isNullOrEmpty()) {
            val upiName = extractNameFromUPI(body)
            if (!upiName.isNullOrEmpty()) {
                recipientName = upiName
            }
        }

        return Pair(recipientName, phoneNumber)
    }

    @Suppress("ReturnCount") // guard clauses read clearer than one accumulated condition here
    internal fun cleanupName(name: String): String? {
        var cleaned = name
            .trim()
            .replace(Regex("\\s+"), " ") // Multiple spaces to single space
            .replace(Regex("[-—]$"), "") // Remove trailing dashes
            .replace(Regex("\\.$"), "") // Remove trailing periods
            .trim()

        // Remove common prefixes/suffixes that aren't part of the name
        // We use regex with word boundaries (\b) so we don't accidentally
        // turn "Mridul" into "idul" or "Drishti" into "ishti".
        cleaned = cleaned.replace(
            Regex("^(?i)(?:M/s|Mr|Mrs|Ms|Dr|merchant)\\b\\.?\\s*"),
            ""
        ).trim()

        // If name is too short or too long, it's probably not valid
        if (cleaned.length < 2 || cleaned.length > 50) {
            return null
        }

        // If name contains only numbers or special characters, it's not valid
        if (!cleaned.contains(Regex("[a-zA-Z]"))) {
            return null
        }

        // Convert to proper case (handle both uppercase and lowercase input)
        return cleaned.split(" ").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
        }
    }

    /** e.g. "johnsmith@okhdfcbank" -> "John Smith", "john.smith@ybl" -> "John Smith" */
    internal fun extractNameFromUPI(body: String): String? {
        val upiPattern = Regex("([a-zA-Z][a-zA-Z0-9._-]+)@[a-zA-Z0-9]+", RegexOption.IGNORE_CASE)
        val upiPrefix = upiPattern.find(body)?.groups?.get(1)?.value
        if (upiPrefix.isNullOrEmpty()) return null

        return upiPrefix
            .replace(".", " ")
            .replace("_", " ")
            .replace("-", " ")
            .split(" ")
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.uppercase() }
            }
    }

    /**
     * Privacy-safe stored summary, built only from extracted fields so the
     * raw SMS body (account fragments, balances) never reaches the database.
     */
    internal fun buildExcerpt(
        amount: String,
        transactionType: String,
        bankName: String,
        status: String
    ): String {
        val verb = when {
            status == TransactionStatus.FAILED -> "payment failed"
            transactionType == "CREDIT" -> "credited"
            else -> "debited"
        }
        return buildString {
            append("\u20B9").append(amount).append(" ").append(verb)
            if (bankName.isNotBlank()) append(" \u2014 ").append(bankName)
        }
    }
}





