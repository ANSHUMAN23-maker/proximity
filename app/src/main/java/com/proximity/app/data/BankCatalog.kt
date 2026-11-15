// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.data

import androidx.annotation.StringRes
import com.proximity.app.R

data class SupportedBank(
    val id: String,
    val displayName: String,
    val ifscPrefix: String,
    @StringRes val nameRes: Int
)

object BankCatalog {
    val ALL_BANKS: List<SupportedBank> = listOf(
        SupportedBank("sbi", "State Bank of India", "SBIN", R.string.bank_sbi),
        SupportedBank("hdfc", "HDFC Bank", "HDFC", R.string.bank_hdfc),
        SupportedBank("icici", "ICICI Bank", "ICIC", R.string.bank_icici),
        SupportedBank("axis", "Axis Bank", "UTIB", R.string.bank_axis),
        SupportedBank("kotak", "Kotak Mahindra Bank", "KKBK", R.string.bank_kotak),
        SupportedBank("pnb", "Punjab National Bank", "PUNB", R.string.bank_pnb),
        SupportedBank("bob", "Bank of Baroda", "BARB", R.string.bank_bob),
        SupportedBank("yes", "Yes Bank", "YESB", R.string.bank_yes),
        SupportedBank("idbi", "IDBI Bank", "IBKL", R.string.bank_idbi),
        SupportedBank("canara", "Canara Bank", "CNRB", R.string.bank_canara),
        SupportedBank("union", "Union Bank of India", "UBIN", R.string.bank_union)
    )

    fun findById(id: String?): SupportedBank? =
        ALL_BANKS.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) }

    fun displayNameFor(id: String?): String =
        findById(id)?.displayName ?: "HDFC Bank"
}

data class TelecomOperator(
    val id: String,
    val title: String,
    val subtitle: String,
    val portalDomain: String,
    val portalUrl: String,
    val supportsUssd99: Boolean
)

object OperatorCatalog {
    val JIO = TelecomOperator(
        id = "jio", title = "Reliance Jio", subtitle = "Jio 4G / 5G",
        portalDomain = "www.jio.com", portalUrl = "https://www.jio.com", supportsUssd99 = false
    )
    val AIRTEL = TelecomOperator(
        id = "airtel", title = "Airtel", subtitle = "Airtel India",
        portalDomain = "www.airtel.in", portalUrl = "https://www.airtel.in", supportsUssd99 = true
    )
    val VODAFONE = TelecomOperator(
        id = "vodafone", title = "Vodafone Idea", subtitle = "Vi India",
        portalDomain = "www.myvi.in", portalUrl = "https://www.myvi.in", supportsUssd99 = true
    )
    val BSNL = TelecomOperator(
        id = "bsnl", title = "BSNL", subtitle = "Bharat Sanchar Nigam Limited",
        portalDomain = "www.bsnl.co.in", portalUrl = "https://www.bsnl.co.in", supportsUssd99 = true
    )

    val ALL_OPERATORS: List<TelecomOperator> = listOf(JIO, AIRTEL, VODAFONE, BSNL)

    fun findById(rawId: String?): TelecomOperator? {
        val normalized = rawId?.trim()?.lowercase() ?: return null
        return when (normalized) {
            "jio", "reliance jio" -> JIO
            "airtel", "bharti airtel" -> AIRTEL
            "vodafone", "vi", "vodafone idea" -> VODAFONE
            "bsnl" -> BSNL
            else -> null
        }
    }

    fun isJio(rawId: String?): Boolean = findById(rawId)?.id == "jio"
}
