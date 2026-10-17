package com.proximity.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "Proximity_settings",
        Context.MODE_PRIVATE
    )

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<SavedSettings> = _settingsFlow.asStateFlow()

    data class SavedSettings(
        val bankId: String = "",
        val ussdTimeout: Int = 30,
        val smsDetectionEnabled: Boolean = true,
        val overlayEnabled: Boolean = true,
        val debugMode: Boolean = false,
        val setupCompleted: Boolean = false,
        val testConfigCompleted: Boolean = false,
        val selectedOperator: String = "unknown",
        val darkModeEnabled: Boolean = false
    )

    fun saveSettings(settings: SavedSettings) {
        prefs.edit {
            putString("bank_id", settings.bankId)
            putInt("ussd_timeout", settings.ussdTimeout)
            putBoolean("sms_detection_enabled", settings.smsDetectionEnabled)
            putBoolean("overlay_enabled", settings.overlayEnabled)
            putBoolean("debug_mode", settings.debugMode)
            putBoolean("setup_completed", settings.setupCompleted)
            putBoolean("test_config_completed", settings.testConfigCompleted)
            putBoolean("dark_mode_enabled", settings.darkModeEnabled)
        }

        if (settings.selectedOperator != "unknown") {
            context.getSharedPreferences("ProximityPrefs", Context.MODE_PRIVATE).edit {
                putString("selected_primary_sim", settings.selectedOperator)
            }
        }

        _settingsFlow.value = settings
    }

    private fun loadSettings(): SavedSettings {
        val simPrefs = context.getSharedPreferences("ProximityPrefs", Context.MODE_PRIVATE)
        return SavedSettings(
            bankId = simPrefs.getString("selected_bank", prefs.getString("bank_id", "")) ?: "",
            ussdTimeout = prefs.getInt("ussd_timeout", 30),
            smsDetectionEnabled = prefs.getBoolean("sms_detection_enabled", true),
            overlayEnabled = prefs.getBoolean("overlay_enabled", true),
            debugMode = prefs.getBoolean("debug_mode", false),
            setupCompleted = prefs.getBoolean("setup_completed", false),
            testConfigCompleted = prefs.getBoolean("test_config_completed", false),
            selectedOperator = simPrefs.getString("selected_primary_sim", "unknown") ?: "unknown",
            darkModeEnabled = prefs.getBoolean("dark_mode_enabled", false)
        )
    }

    fun clearAllData() {
        prefs.edit { clear() }
        context.getSharedPreferences("ProximityPrefs", Context.MODE_PRIVATE).edit { clear() }
        _settingsFlow.value = SavedSettings()
    }

    fun resetToDefaults() {
        context.getSharedPreferences("ProximityPrefs", Context.MODE_PRIVATE).edit { clear() }
        saveSettings(SavedSettings())
    }
}






