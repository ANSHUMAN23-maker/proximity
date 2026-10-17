// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity
//
// This file defines the Proximity Design System color palette.
// It provides both light and dark theme color values that adapt
// based on the current theme context.

package com.proximity.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Proximity Dark Theme Design System
 * Deep charcoal backgrounds with #008774 (spec teal) accent
 * Adapted from Stitch spec for dark mode
 */



// Backgrounds - Rich Midnight Slate ramp (replacing dull flat gray with high-contrast slate tones)
val AbyssalCharcoal = Color(0xFF090D14)       // Main background - rich deep midnight slate
val DeepCharcoal = Color(0xFF101721)          // Elevated surfaces - dark slate
val CharcoalCard = Color(0xFF17202C)          // Standard card / tile background
val CharcoalSurface = Color(0xFF1F2A38)       // Input backgrounds, container surface
val CharcoalSurfaceHigh = Color(0xFF283647)   // Modal overlays, dialogs, dropdowns
val CharcoalOutline = Color(0xFF334356)       // Dividers, borders, text field outlines
val CharcoalOutlineStrong = Color(0xFF475B75) // Focus rings, selected state borders

// Brand - Spec teal primary, Gold secondary (payment success)
val ProximityTeal = Color(0xFF008774)         // Primary actions, FAB, selected tabs (spec teal)
val ProximityTealDark = Color(0xFF006D5D)     // Pressed states
val ProximityTealLight = Color(0xFF00A38D)    // Hover, ripple, focus
val ProximityTealTint = Color(0xFF122E2B)     // Rich dark teal tint for selected states
val ProximityGold = Color(0xFFE8B45A)         // Gold - payment success, warning

// Text - tuned for rich dark slate surfaces (crisp & high-contrast)
val TextPrimary = Color(0xFFF8FAFC)           // Crisp white - primary text
val TextSecondary = Color(0xFF94A3B8)         // Slate gray - secondary text
val TextDisabled = Color(0xFF64748B)          // Muted slate - disabled text
val OnPrimary = Color(0xFF001F1A)             // On ProximityTeal (dark)

// Status - unchanged (critical for payment UX)
val StatusGreen = Color(0xFF4ADE80)           // Success - bright, unambiguous
val StatusRed = Color(0xFFF87171)             // Destructive / error
val StatusAmber = Color(0xFFFBBF24)           // Pending / review

// Accent theme definition
data class ProximityAccentTheme(
    val primary: Color,
    val primaryDark: Color,
    val accent: Color,
    val accentLight: Color
)

val TealAccentTheme = ProximityAccentTheme(
    primary = ProximityTeal,
    primaryDark = ProximityTealDark,
    accent = ProximityTeal,
    accentLight = ProximityTealLight
)

val LocalProximityAccentTheme = compositionLocalOf { TealAccentTheme }

// Legacy aliases - DEPRECATED. Use MaterialTheme.colorScheme or canonical palette values directly.
// These aliases exist for backward compatibility only and will be removed in a future release.

@Deprecated("Use MaterialTheme.colorScheme.background or AbyssalCharcoal directly")
val ProximityBlack: Color
    @Composable get() = MaterialTheme.colorScheme.background

@Deprecated("Use MaterialTheme.colorScheme.surface or DeepCharcoal directly")
val ProximityDarkGray: Color
    @Composable get() = MaterialTheme.colorScheme.surface

@Deprecated("Use MaterialTheme.colorScheme.surfaceVariant or CharcoalSurface directly")
val ProximitySurfaceDim: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceVariant

@Deprecated("Use MaterialTheme.colorScheme.surfaceVariant or CharcoalSurface directly")
val ProximityMediumGray: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceVariant

@Deprecated("Use MaterialTheme.colorScheme.outline or CharcoalOutline directly")
val ProximityLightGray: Color
    @Composable get() = MaterialTheme.colorScheme.outline

@Deprecated("Use MaterialTheme.colorScheme.outlineVariant or CharcoalOutlineStrong directly")
val ProximityOutlineGray: Color
    @Composable get() = MaterialTheme.colorScheme.outlineVariant

@Deprecated("Use MaterialTheme.colorScheme.primary or ProximityTeal directly")
val ProximityBrandPrimary: Color
    @Composable get() = MaterialTheme.colorScheme.primary

@Deprecated("Use MaterialTheme.colorScheme.secondary or ProximityTealLight directly")
val ProximityBrandAccent: Color
    @Composable get() = MaterialTheme.colorScheme.secondary

@Deprecated("Use StatusGreen directly")
val ProximityAccentGreen = StatusGreen

@Deprecated("Use MaterialTheme.colorScheme.secondary or ProximityTealLight directly")
val ProximityAccentBlue: Color
    @Composable get() = MaterialTheme.colorScheme.secondary

@Deprecated("Use MaterialTheme.colorScheme.onSurface or TextPrimary directly")
val ProximityTextWhite: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface

@Deprecated("Use MaterialTheme.colorScheme.onSurface or TextPrimary directly")
val ProximityWhite: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface

@Deprecated("Use MaterialTheme.colorScheme.onSurfaceVariant or TextSecondary directly")
val ProximityTextLightGray: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

@Deprecated("Use MaterialTheme.colorScheme.onSurfaceVariant or TextSecondary directly")
val ProximityTextGray: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

@Deprecated("Use MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) or TextSecondary with alpha directly")
val ProximityTextPale: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
val OverlayBlack = Color(0xCC0D0D0D)

@Deprecated("Use StatusGreen directly")
val ProximityStatusSuccess = StatusGreen

@Deprecated("Use StatusRed directly")
val ProximityStatusError = StatusRed

@Deprecated("Use StatusAmber directly")
val ProximityStatusWarning = StatusAmber

@Deprecated("Use TextDisabled directly")
val ProximityDisabledGray = TextDisabled

@Deprecated("Use StatusGreen directly")
val ProximityAccentGreenBright = StatusGreen

// Additional legacy aliases - DEPRECATED
@Deprecated("Use MaterialTheme.colorScheme.background or AbyssalCharcoal directly")
val InkBlack: Color
    @Composable get() = MaterialTheme.colorScheme.background

@Deprecated("Use MaterialTheme.colorScheme.surface or DeepCharcoal directly")
val InkDark: Color
    @Composable get() = MaterialTheme.colorScheme.surface

@Deprecated("Use MaterialTheme.colorScheme.surfaceVariant or CharcoalSurface directly")
val InkCard: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceVariant

@Deprecated("Use MaterialTheme.colorScheme.outline or CharcoalOutline directly")
val InkDivider: Color
    @Composable get() = MaterialTheme.colorScheme.outline

@Deprecated("Use ProximityTeal directly")
val ElectricIndigo = ProximityTeal

@Deprecated("Use ProximityTealLight directly")
val IndigoSoft = ProximityTealLight

@Deprecated("Use ProximityGold directly")
val BrandGold = ProximityGold

@Deprecated("Use TealAccentTheme directly")
val BlueAccentTheme = TealAccentTheme

// Status color mapping
fun statusColor(status: String): Color {
    return when (status.uppercase()) {
        "SUCCESS", "SUCCESSFUL", "COMPLETED", "USER_ATTESTED", "USER_CONFIRMED" -> StatusGreen
        "UNVERIFIED", "NEEDS_REVIEW", "PENDING" -> StatusAmber
        "CANCELLED", "FAILED", "DECLINED", "ERROR" -> StatusRed
        else -> StatusAmber
    }
}

// Convenience semantic aliases
val PrimaryTeal = ProximityTeal
val PrimaryTealDark = ProximityTealDark
val PrimaryTealLight = ProximityTealLight
val PrimaryTealTint = ProximityTealTint
