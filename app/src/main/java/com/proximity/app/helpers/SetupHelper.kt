package com.proximity.app.helpers

import android.content.Context
import com.proximity.app.R

class SetupHelper(
    private val context: Context,
    private val uiCallback: UICallback
) {
    companion object {
        private const val PREFS = "ProximityPrefs"
        private const val KEY_USER_REPORTED_USSD_NOT_WORKING = "user_reported_ussd_not_working"

        fun isPrimarySimUssdCapable(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val primarySim = prefs.getString("selected_primary_sim", "") ?: ""
            return primarySim != "jio"
        }

        fun hasUserReportedUssdNotWorking(context: Context): Boolean {
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_USER_REPORTED_USSD_NOT_WORKING, false)
        }

        fun setUserReportedUssdNotWorking(context: Context, reported: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_USER_REPORTED_USSD_NOT_WORKING, reported)
                .apply()
        }

        internal fun clearUserReportedUssdNotWorking(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(KEY_USER_REPORTED_USSD_NOT_WORKING)
                .apply()
        }

        fun isScanToPayUssdAvailable(context: Context): Boolean {
            return isPrimarySimUssdCapable(context) && !hasUserReportedUssdNotWorking(context)
        }
        fun getScanToPayBlockedMessage(context: Context): String? {
            if (isScanToPayUssdAvailable(context)) return null
            if (!isPrimarySimUssdCapable(context)) {
                return "Scan to pay is not available - Jio does not support *99# USSD payments"
            }
            return "Scan to pay is not available - USSD does not work for you on this device, so this feature can't be used."
        }
    }
    interface UICallback {
        fun showToast(message: String)
        fun navigateToTestConfiguration()
    }

    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String
    )
    data class SetupData(
        val selectedBank: String,
        val selectedPrimarySim: String,
        val isDualSimEnabled: Boolean,
        val selectedSecondarySim: String,
        val disclaimerAccepted: Boolean
    )
    fun getBanks(): List<Pair<String, String>> {
        return listOf(
            "sbi" to "State Bank of India",
            "hdfc" to "HDFC Bank",
            "icici" to "ICICI Bank",
            "axis" to "Axis Bank",
            "kotak" to "Kotak Mahindra Bank",
            "pnb" to "Punjab National Bank",
            "bob" to "Bank of Baroda",
            "yes" to "Yes Bank",
            "idbi" to "IDBI Bank",
            "canara" to "Canara Bank",
            "union" to "Union Bank of India"
        )
    }
    fun getSimCarriers(): List<Pair<String, String>> {
        return listOf(
            "jio" to "Jio",
            "airtel" to "Airtel",
            "vodafone" to "Vodafone",
            "bsnl" to "BSNL"
        )
    }
    fun getSecondarySimOptions(primarySim: String): List<Pair<String, String>> {
        val allCarriers = getSimCarriers()
        return if (primarySim.isNotEmpty()) {
            allCarriers.filter { it.first != primarySim }
        } else {
            allCarriers
        }
    }

    fun validateDisclaimer(disclaimerAccepted: Boolean): ValidationResult {
        if (!disclaimerAccepted) {
            return ValidationResult(false, "Please accept the disclaimer to proceed")
        }
        return ValidationResult(true, "")
    }
    fun validateBankSelection(selectedBank: String): ValidationResult {
        if (selectedBank.isEmpty()) {
            return ValidationResult(false, "Please select your bank")
        }
        return ValidationResult(true, "")
    }
    fun validatePrimarySimSelection(selectedPrimarySim: String): ValidationResult {
        if (selectedPrimarySim.isEmpty()) {
            return ValidationResult(false, "Please select your primary SIM")
        }
        return ValidationResult(true, "")
    }

    fun validateSecondarySimSelection(
        isDualSimEnabled: Boolean,
        selectedSecondarySim: String
    ): ValidationResult {
        if (isDualSimEnabled && selectedSecondarySim.isEmpty()) {
            return ValidationResult(false, "Please select your secondary SIM")
        }
        return ValidationResult(true, "")
    }

    fun validateForm(setupData: SetupData): ValidationResult {
        // Validate disclaimer
        val disclaimerValidation = validateDisclaimer(setupData.disclaimerAccepted)
        if (!disclaimerValidation.isValid) {
            return disclaimerValidation
        }

        val bankValidation = validateBankSelection(setupData.selectedBank)
        if (!bankValidation.isValid) {
            return bankValidation
        }

        val primarySimValidation = validatePrimarySimSelection(setupData.selectedPrimarySim)
        if (!primarySimValidation.isValid) {
            return primarySimValidation
        }

        val secondarySimValidation = validateSecondarySimSelection(
            setupData.isDualSimEnabled,
            setupData.selectedSecondarySim
        )
        if (!secondarySimValidation.isValid) {
            return secondarySimValidation
        }

        return ValidationResult(true, "")
    }

    fun saveSetupData(setupData: SetupData) {
        val sharedPreferences = context.getSharedPreferences("ProximityPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .putBoolean("setup_completed", true)
            .putString("selected_bank", setupData.selectedBank)
            .putString("selected_primary_sim", setupData.selectedPrimarySim)
            .putBoolean("is_dual_sim_enabled", setupData.isDualSimEnabled)
            .putString("selected_secondary_sim", setupData.selectedSecondarySim)
            .putBoolean("disclaimer_accepted", setupData.disclaimerAccepted)
            .apply()
    }

    fun completeSetup(setupData: SetupData) {
        val validationResult = validateForm(setupData)

        if (validationResult.isValid) {
            saveSetupData(setupData)

            uiCallback.showToast(context.getString(R.string.status_setup_complete))

            uiCallback.navigateToTestConfiguration()
        } else {
            uiCallback.showToast(validationResult.errorMessage)
        }
    }

    fun isSetupCompleted(): Boolean {
        val sharedPreferences = context.getSharedPreferences("ProximityPrefs", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean("setup_completed", false)
    }
}
