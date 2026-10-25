// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity
//
// This file contains the Material3 theme definitions for Proximity.
// It provides both dark and light color schemes that adapt based on system settings.

package com.proximity.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.proximity.app.data.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = ProximityTeal,
    secondary = ProximityGold,
    tertiary = ProximityTealLight,
    background = AbyssalCharcoal,
    surface = DeepCharcoal,
    surfaceVariant = CharcoalCard,
    surfaceContainer = CharcoalSurface,
    surfaceContainerHigh = CharcoalSurfaceHigh,
    surfaceContainerHighest = Color(0xFF334356),
    surfaceContainerLow = Color(0xFF131B26),
    surfaceContainerLowest = Color(0xFF0B0F17),
    onPrimary = OnPrimary,
    onSecondary = AbyssalCharcoal,
    onTertiary = AbyssalCharcoal,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = CharcoalOutline,
    outlineVariant = CharcoalOutlineStrong,
    error = StatusRed,
    onError = TextPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = ProximityTeal,
    secondary = ProximityGold,
    tertiary = ProximityTealDark,
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1F5F9),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF1F5F9),
    surfaceContainerHighest = Color(0xFFE2E8F0),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onTertiary = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = StatusRed,
    onError = Color.White
)

private fun ColorScheme.pureBlack(apply: Boolean): ColorScheme {
    return if (apply) {
        this.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainer = Color(0xFF0A0A0A),
            surfaceContainerLow = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerHigh = Color(0xFF141414),
            surfaceContainerHighest = Color(0xFF1E1E1E)
        )
    } else this
}

@Composable
fun ProximityTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    darkTheme: Boolean? = null,
    pureBlack: Boolean = false,
    liquidGlass: Boolean = true,
    content: @Composable () -> Unit
) {
    val systemInDark = isSystemInDarkTheme()
    val isDark = when {
        darkTheme != null -> darkTheme
        themeMode == ThemeMode.SYSTEM -> systemInDark
        themeMode == ThemeMode.DARK -> true
        themeMode == ThemeMode.LIGHT -> false
        else -> systemInDark
    }

    val baseScheme = if (isDark) DarkColorScheme.pureBlack(pureBlack) else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDark
        }
    }

    CompositionLocalProvider(
        LocalAccentTextColor provides accentTextColor(ProximityTeal, isDark),
        LocalGlassEnabled provides liquidGlass
    ) {
        MaterialTheme(
            colorScheme = baseScheme,
            typography = ProximityTypography,
            content = content
        )
    }
}
