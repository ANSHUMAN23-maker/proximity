// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.data

import android.content.Context
import android.content.SharedPreferences
import com.proximity.app.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserSettings(
    val setupCompleted: Boolean = false,
    val connectivityTestsCompleted: Boolean = false,
    val selectedBankId: String = "hdfc",
    val primarySimCarrierId: String = "",
    val ussdTestPassed: Boolean = false,
    val ussdReportedNotWorking: Boolean = false,
    val upi123TestPassed: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pureBlackEnabled: Boolean = false,
    val liquidGlassEnabled: Boolean = true,
    val ussdAccessibilityPrefEnabled: Boolean = false,
    val promptedNotificationPermissionOnce: Boolean = false,
    val selectedOperator: String = ""
) {
    val selectedBankDisplayName: String
        get() = BankCatalog.displayNameFor(selectedBankId)

    val isJioPrimarySim: Boolean
        get() = OperatorCatalog.isJio(primarySimCarrierId)

    val isUssdWaivedOrBlocked: Boolean
        get() = isJioPrimarySim || ussdReportedNotWorking

    val isScanQrUnlocked: Boolean
        get() = ussdTestPassed && !isUssdWaivedOrBlocked

    val isPayContactUnlocked: Boolean
        get() = upi123TestPassed
}

class UserPreferencesStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("user_settings", Context.MODE_PRIVATE)
    private val writeLock = Any()
    private val _settingsFlow = MutableStateFlow(readFromPrefs())
    val settingsFlow: StateFlow<UserSettings> = _settingsFlow.asStateFlow()

    private fun readFromPrefs(): UserSettings {
        val themeStr = prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        val themeMode = runCatching { ThemeMode.valueOf(themeStr) }.getOrDefault(ThemeMode.SYSTEM)
        return UserSettings(
            setupCompleted = prefs.getBoolean("setup_completed", false),
            connectivityTestsCompleted = prefs.getBoolean("tests_completed", false),
            selectedBankId = prefs.getString("selected_bank_id", "hdfc") ?: "hdfc",
            primarySimCarrierId = prefs.getString("primary_sim_id", "") ?: "",
            ussdTestPassed = prefs.getBoolean("ussd_test_passed", false),
            ussdReportedNotWorking = prefs.getBoolean("ussd_reported_issue", false),
            upi123TestPassed = prefs.getBoolean("upi123_test_passed", false),
            themeMode = themeMode,
            pureBlackEnabled = prefs.getBoolean("pure_black_enabled", false),
            liquidGlassEnabled = prefs.getBoolean("liquid_glass_enabled", true),
            ussdAccessibilityPrefEnabled = prefs.getBoolean("ussd_accessibility_pref", false),
            promptedNotificationPermissionOnce = prefs.getBoolean("prompted_notif_once", false),
            selectedOperator = prefs.getString("selected_operator", "") ?: ""
        )
    }

    fun update(transform: (UserSettings) -> UserSettings) {
        synchronized(writeLock) {
            val updated = transform(_settingsFlow.value)
            prefs.edit()
                .putBoolean("setup_completed", updated.setupCompleted)
                .putBoolean("tests_completed", updated.connectivityTestsCompleted)
                .putString("selected_bank_id", updated.selectedBankId)
                .putString("primary_sim_id", updated.primarySimCarrierId)
                .putBoolean("ussd_test_passed", updated.ussdTestPassed)
                .putBoolean("ussd_reported_issue", updated.ussdReportedNotWorking)
                .putBoolean("upi123_test_passed", updated.upi123TestPassed)
                .putString("theme_mode", updated.themeMode.name)
                .putBoolean("pure_black_enabled", updated.pureBlackEnabled)
                .putBoolean("liquid_glass_enabled", updated.liquidGlassEnabled)
                .putBoolean("ussd_accessibility_pref", updated.ussdAccessibilityPrefEnabled)
                .putBoolean("prompted_notif_once", updated.promptedNotificationPermissionOnce)
                .putString("selected_operator", updated.selectedOperator)
                .apply()
            _settingsFlow.value = updated
        }
    }

    fun getTestResults(): TestResults {
        val current = _settingsFlow.value
        return TestResults(
            ussdEnabled = current.ussdTestPassed,
            upi123Enabled = current.upi123TestPassed
        )
    }

    fun saveTestResults(ussd: Boolean, upi123: Boolean) {
        update { it.copy(ussdTestPassed = ussd, upi123TestPassed = upi123) }
    }

    data class TestResults(val ussdEnabled: Boolean, val upi123Enabled: Boolean)
}
