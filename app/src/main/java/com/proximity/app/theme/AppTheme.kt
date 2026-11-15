// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.theme

import android.app.Activity
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.proximity.app.data.UserSettings

@Immutable
data class AppVisualTokens(
    val isDark: Boolean,
    val isOledBlack: Boolean,
    val isLiquidGlassEnabled: Boolean,
    val mainBackground: Color,
    val elevatedSurface: Color,
    val cardSurface: Color,
    val inputSurface: Color,
    val highSurface: Color,
    val highestSurface: Color,
    val outline: Color,
    val strongOutline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val brandTeal: Color,
    val adaptiveTealLabel: Color,
    val selectedTint: Color,
    val statusSuccessText: Color,
    val statusPendingText: Color,
    val statusErrorText: Color
)

@Composable
fun rememberReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
}

val LocalAppVisuals = staticCompositionLocalOf {
    AppVisualTokens(
        isDark = true,
        isOledBlack = false,
        isLiquidGlassEnabled = true,
        mainBackground = ColorTokens.DarkMainBackground,
        elevatedSurface = ColorTokens.DarkElevatedSurface,
        cardSurface = ColorTokens.DarkCardSurfaceVariant,
        inputSurface = ColorTokens.DarkInputContainerSurface,
        highSurface = ColorTokens.DarkHighSurface,
        highestSurface = ColorTokens.DarkHighestSurface,
        outline = ColorTokens.DarkOutline,
        strongOutline = ColorTokens.DarkStrongOutline,
        textPrimary = ColorTokens.DarkTextPrimary,
        textSecondary = ColorTokens.DarkTextSecondary,
        textDisabled = ColorTokens.DarkTextDisabled,
        brandTeal = ColorTokens.BrandTeal,
        adaptiveTealLabel = ColorTokens.adaptiveTealText(isDark = true),
        selectedTint = ColorTokens.SelectedDarkTealTint,
        statusSuccessText = ColorTokens.StatusSuccessGreen,
        statusPendingText = ColorTokens.StatusPendingAmber,
        statusErrorText = ColorTokens.StatusErrorRed
    )
}

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun AppTheme(
    settings: UserSettings = UserSettings(),
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val resolvedDark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val useOled = resolvedDark && settings.pureBlackEnabled

    val visuals = remember(resolvedDark, useOled, settings.liquidGlassEnabled) {
        if (!resolvedDark) {
            AppVisualTokens(
                isDark = false,
                isOledBlack = false,
                isLiquidGlassEnabled = settings.liquidGlassEnabled,
                mainBackground = ColorTokens.LightMainBackground,
                elevatedSurface = ColorTokens.LightSurfaceCard,
                cardSurface = ColorTokens.LightSurfaceVariant,
                inputSurface = ColorTokens.LightSurfaceVariant,
                highSurface = ColorTokens.LightSurfaceCard,
                highestSurface = ColorTokens.LightSurfaceContainerHighest,
                outline = ColorTokens.LightOutline,
                strongOutline = ColorTokens.BrandTeal,
                textPrimary = ColorTokens.LightTextPrimary,
                textSecondary = ColorTokens.LightTextSecondary,
                textDisabled = ColorTokens.LightTextDisabled,
                brandTeal = ColorTokens.BrandTeal,
                adaptiveTealLabel = ColorTokens.adaptiveTealText(isDark = false),
                selectedTint = ColorTokens.LightTealSelectedTint,
                statusSuccessText = ColorTokens.StatusSuccessTextLight,
                statusPendingText = ColorTokens.StatusPendingTextLight,
                statusErrorText = ColorTokens.StatusErrorTextLight
            )
        } else if (useOled) {
            AppVisualTokens(
                isDark = true,
                isOledBlack = true,
                isLiquidGlassEnabled = settings.liquidGlassEnabled,
                mainBackground = ColorTokens.OledBlackBackground,
                elevatedSurface = ColorTokens.OledSurfaceContainer,
                cardSurface = ColorTokens.OledSurfaceContainer,
                inputSurface = ColorTokens.OledSurfaceContainerHigh,
                highSurface = ColorTokens.OledSurfaceContainerHigh,
                highestSurface = ColorTokens.OledSurfaceContainerHighest,
                outline = ColorTokens.DarkOutline,
                strongOutline = ColorTokens.DarkStrongOutline,
                textPrimary = ColorTokens.DarkTextPrimary,
                textSecondary = ColorTokens.DarkTextSecondary,
                textDisabled = ColorTokens.DarkTextDisabled,
                brandTeal = ColorTokens.BrandTeal,
                adaptiveTealLabel = ColorTokens.adaptiveTealText(isDark = true),
                selectedTint = ColorTokens.SelectedDarkTealTint,
                statusSuccessText = ColorTokens.StatusSuccessGreen,
                statusPendingText = ColorTokens.StatusPendingAmber,
                statusErrorText = ColorTokens.StatusErrorRed
            )
        } else {
            AppVisualTokens(
                isDark = true,
                isOledBlack = false,
                isLiquidGlassEnabled = settings.liquidGlassEnabled,
                mainBackground = ColorTokens.DarkMainBackground,
                elevatedSurface = ColorTokens.DarkElevatedSurface,
                cardSurface = ColorTokens.DarkCardSurfaceVariant,
                inputSurface = ColorTokens.DarkInputContainerSurface,
                highSurface = ColorTokens.DarkHighSurface,
                highestSurface = ColorTokens.DarkHighestSurface,
                outline = ColorTokens.DarkOutline,
                strongOutline = ColorTokens.DarkStrongOutline,
                textPrimary = ColorTokens.DarkTextPrimary,
                textSecondary = ColorTokens.DarkTextSecondary,
                textDisabled = ColorTokens.DarkTextDisabled,
                brandTeal = ColorTokens.BrandTeal,
                adaptiveTealLabel = ColorTokens.adaptiveTealText(isDark = true),
                selectedTint = ColorTokens.SelectedDarkTealTint,
                statusSuccessText = ColorTokens.StatusSuccessGreen,
                statusPendingText = ColorTokens.StatusPendingAmber,
                statusErrorText = ColorTokens.StatusErrorRed
            )
        }
    }

    val colorScheme: ColorScheme = remember(resolvedDark, visuals) {
        if (resolvedDark) {
            darkColorScheme(
                primary = ColorTokens.BrandTeal,
                onPrimary = Color.White,
                secondary = ColorTokens.SecondaryBrandGold,
                background = visuals.mainBackground,
                onBackground = visuals.textPrimary,
                surface = visuals.elevatedSurface,
                onSurface = visuals.textPrimary,
                surfaceVariant = visuals.cardSurface,
                onSurfaceVariant = visuals.textSecondary,
                outline = visuals.outline,
                error = ColorTokens.StatusErrorRed
            )
        } else {
            lightColorScheme(
                primary = ColorTokens.BrandTeal,
                onPrimary = Color.White,
                secondary = ColorTokens.SecondaryBrandGold,
                background = visuals.mainBackground,
                onBackground = visuals.textPrimary,
                surface = visuals.elevatedSurface,
                onSurface = visuals.textPrimary,
                surfaceVariant = visuals.cardSurface,
                onSurfaceVariant = visuals.textSecondary,
                outline = visuals.outline,
                error = ColorTokens.StatusErrorTextLight
            )
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            window.decorView.setBackgroundColor(visuals.mainBackground.toArgb())
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !resolvedDark
            insetsController.isAppearanceLightNavigationBars = !resolvedDark
        }
    }

    CompositionLocalProvider(LocalAppVisuals provides visuals) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
