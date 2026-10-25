package com.proximity.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

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
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val pureBlackEnabled: Boolean = false,
        val liquidGlassEnabled: Boolean = true
    ) {
        // Backward compatibility helper for existing callers
        val darkModeEnabled: Boolean
            get() = themeMode == ThemeMode.DARK
    }

    fun saveSettings(settings: SavedSettings) {
        prefs.edit {
            putString("bank_id", settings.bankId)
            putInt("ussd_timeout", settings.ussdTimeout)
            putBoolean("sms_detection_enabled", settings.smsDetectionEnabled)
            putBoolean("overlay_enabled", settings.overlayEnabled)
            putBoolean("debug_mode", settings.debugMode)
            putBoolean("setup_completed", settings.setupCompleted)
            putBoolean("test_config_completed", settings.testConfigCompleted)
            putString("theme_mode", settings.themeMode.name)
            putBoolean("pure_black_enabled", settings.pureBlackEnabled)
            putBoolean("liquid_glass_enabled", settings.liquidGlassEnabled)
            putBoolean("dark_mode_enabled", settings.themeMode == ThemeMode.DARK)
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
        val legacyDarkMode = prefs.getBoolean("dark_mode_enabled", false)
        val savedThemeModeStr = prefs.getString("theme_mode", null)
        val resolvedThemeMode = if (savedThemeModeStr != null) {
            try {
                ThemeMode.valueOf(savedThemeModeStr)
            } catch (e: Exception) {
                if (legacyDarkMode) ThemeMode.DARK else ThemeMode.SYSTEM
            }
        } else {
            if (legacyDarkMode) ThemeMode.DARK else ThemeMode.SYSTEM
        }

        return SavedSettings(
            bankId = simPrefs.getString("selected_bank", prefs.getString("bank_id", "")) ?: "",
            ussdTimeout = prefs.getInt("ussd_timeout", 30),
            smsDetectionEnabled = prefs.getBoolean("sms_detection_enabled", true),
            overlayEnabled = prefs.getBoolean("overlay_enabled", true),
            debugMode = prefs.getBoolean("debug_mode", false),
            setupCompleted = prefs.getBoolean("setup_completed", false),
            testConfigCompleted = prefs.getBoolean("test_config_completed", false),
            selectedOperator = simPrefs.getString("selected_primary_sim", "unknown") ?: "unknown",
            themeMode = resolvedThemeMode,
            pureBlackEnabled = prefs.getBoolean("pure_black_enabled", false),
            liquidGlassEnabled = prefs.getBoolean("liquid_glass_enabled", true)
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
