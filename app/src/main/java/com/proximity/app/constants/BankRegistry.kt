package com.proximity.app.constants

object BankRegistry {

    data class BankInfo(
        val id: String,
        val name: String,
        val ussdSupported: Boolean = true
    )

    private val banks = listOf(
        BankInfo("sbi", "State Bank of India"),
        BankInfo("hdfc", "HDFC Bank"),
        BankInfo("icici", "ICICI Bank"),
        BankInfo("axis", "Axis Bank"),
        BankInfo("kotak", "Kotak Mahindra Bank"),
        BankInfo("pnb", "Punjab National Bank"),
        BankInfo("bob", "Bank of Baroda"),
        BankInfo("yes", "Yes Bank"),
        BankInfo("idbi", "IDBI Bank"),
        BankInfo("canara", "Canara Bank"),
        BankInfo("union", "Union Bank of India")
    )
    fun nameForId(bankId: String): String =
        banks.find { it.id == bankId }?.name
            ?: bankId.replaceFirstChar { it.uppercase() }

    fun allPairs(): List<Pair<String, String>> =
        banks.map { it.id to it.name }

    fun all(): List<BankInfo> = banks

    fun isValidBankId(bankId: String): Boolean =
        banks.any { it.id == bankId }
}
