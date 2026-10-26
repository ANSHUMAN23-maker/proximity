package com.proximity.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Liquid Glass design tokens for Proximity.
 * Provides curated surface fills, specular rim gradients, blur radii, and shadows
 * for Dark Glass, Light Glass, and OLED Pure Black Glass.
 */
object GlassTokens {
    /** Hardware blur radius for Android 12+ (API 31+) RenderEffect */
    val BlurRadius: Dp = 16.dp

    // ==========================================
    // Dark Glass (Midnight Slate)
    // ==========================================
    val DarkSurfaceFill = Color(0xFF090D14).copy(alpha = 0.68f)
    val DarkSurfaceFillElevated = Color(0xFF101721).copy(alpha = 0.75f)
    
    val DarkSpecularBorderBrush: Brush
        get() = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.32f),
                ProximityTealLight.copy(alpha = 0.18f),
                Color.White.copy(alpha = 0.06f)
            )
        )

    val DarkBorderStroke = 1.dp

    // ==========================================
    // Light Glass (Clean White Slate)
    // ==========================================
    val LightSurfaceFill = Color.White.copy(alpha = 0.75f)
    val LightSurfaceFillElevated = Color.White.copy(alpha = 0.85f)
    
    val LightSpecularBorderBrush: Brush
        get() = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.90f),
                Color(0xFFE2E8F0).copy(alpha = 0.60f),
                Color.White.copy(alpha = 0.30f)
            )
        )

    val LightBorderStroke = 1.dp

    // ==========================================
    // OLED Pure Black Glass
    // ==========================================
    val OledSurfaceFill = Color.Black.copy(alpha = 0.82f)
    val OledSpecularBorderBrush: Brush
        get() = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.28f),
                ProximityTeal.copy(alpha = 0.15f),
                Color.Transparent
            )
        )

    // ==========================================
    // Dynamic Resolvers
    // ==========================================
    @Composable
    fun resolveSurfaceFill(isDark: Boolean, isPureBlack: Boolean): Color {
        return when {
            isDark && isPureBlack -> OledSurfaceFill
            isDark -> DarkSurfaceFill
            else -> LightSurfaceFill
        }
    }

    @Composable
    fun resolveBorderBrush(isDark: Boolean, isPureBlack: Boolean): Brush {
        return when {
            isDark && isPureBlack -> OledSpecularBorderBrush
            isDark -> DarkSpecularBorderBrush
            else -> LightSpecularBorderBrush
        }
    }
}
