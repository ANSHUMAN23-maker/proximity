// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

object ColorTokens {
    // Dark Mode -- Abyssal Charcoal & Midnight Slate Ramp
    val AbyssalCharcoal = Color(0xFF0D0D0D)
    val DarkMainBackground = Color(0xFF090D14)
    val DarkElevatedSurface = Color(0xFF101721)
    val DarkCardSurfaceVariant = Color(0xFF17202C)
    val DarkInputContainerSurface = Color(0xFF1F2A38)
    val DarkHighSurface = Color(0xFF283647)
    val DarkHighestSurface = Color(0xFF334356)
    val DarkOutline = Color(0xFF334356)
    val DarkStrongOutline = Color(0xFF475B75)
    val DarkTextPrimary = Color(0xFFF8FAFC)
    val DarkTextSecondary = Color(0xFF94A3B8)
    val DarkTextDisabled = Color(0xFF64748B)
    val DarkTextOnPrimary = Color(0xFF001F1A)

    // OLED Pure Black Mode Overrides
    val OledBlackBackground = Color(0xFF000000)
    val OledSurfaceContainer = Color(0xFF0A0A0A)
    val OledSurfaceContainerHigh = Color(0xFF141414)
    val OledSurfaceContainerHighest = Color(0xFF1E1E1E)

    // Light Mode -- Clean Slate Ramp
    val LightMainBackground = Color(0xFFF8FAFC)
    val LightSurfaceCard = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFF1F5F9)
    val LightSurfaceContainerHighest = Color(0xFFE2E8F0)
    val LightTextPrimary = Color(0xFF0F172A)
    val LightTextSecondary = Color(0xFF475569)
    val LightTextDisabled = Color(0xFF94A3B8)
    val LightOutline = Color(0xFFCBD5E1)
    val LightTealSelectedTint = Color(0xFFE6F5F2)

    // Brand & Semantic Status Accents
    val BrandTeal = Color(0xFF007A68)
    val PressedDarkTeal = Color(0xFF005F51)
    val SpecularLightTeal = Color(0xFF00A38D)
    val SelectedDarkTealTint = Color(0xFF122E2B)
    val SecondaryBrandGold = Color(0xFFE8B45A)
    val StatusSuccessGreen = Color(0xFF4ADE80)
    val StatusErrorRed = Color(0xFFF87171)
    val StatusPendingAmber = Color(0xFFFBBF24)

    // WCAG AA (>= 4.5:1) high-contrast ink tokens for light mode cards & badges
    val StatusSuccessTextLight = Color(0xFF15803D)
    val StatusPendingTextLight = Color(0xFFB45309)
    val StatusErrorTextLight = Color(0xFFB91C1C)

    // Carrier Badge Tokens
    val CarrierJioFill = Color(0xFF0A2885)
    val CarrierAirtelFill = Color(0xFFE10600)
    val CarrierViText = Color(0xFFE60000)
    val CarrierBsnlText = Color(0xFF003399)
    val CarrierLightBorder = Color(0xFFE0E0E0)

    fun adaptiveTealText(isDark: Boolean, base: Color = BrandTeal): Color {
        val baseArgb = base.toArgb()
        val blended = if (isDark) {
            ColorUtils.blendARGB(baseArgb, android.graphics.Color.WHITE, 0.35f)
        } else {
            ColorUtils.blendARGB(baseArgb, android.graphics.Color.BLACK, 0.45f)
        }
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(blended, hsl)
        hsl[2] = if (isDark) hsl[2].coerceAtLeast(0.72f) else hsl[2].coerceAtMost(0.32f)
        return Color(ColorUtils.HSLToColor(hsl))
    }
}
