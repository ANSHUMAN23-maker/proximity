// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity
//
// This file contains the Material3 theme definitions for Proximity.
// It provides both dark and light color schemes that adapt based on system settings.

package com.proximity.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onTertiary = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF475569),
    error = StatusRed,
    onError = Color.White
)

@Composable
fun ProximityTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ProximityTypography,
        content = content
    )
}
