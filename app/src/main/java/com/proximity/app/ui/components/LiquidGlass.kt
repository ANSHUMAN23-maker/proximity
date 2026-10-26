// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.proximity.app.ui.theme.GlassTokens
import com.proximity.app.ui.theme.LocalGlassEnabled

/**
 * Applies a dimensional Liquid Glass effect with specular edge reflection and
 * smooth translucent frosting to any Composable container.
 *
 * When [isGlassEnabled] is true:
 * - Employs theme-aware specular perimeter gradients (Light Glass, Dark Glass, OLED Pure Black).
 * - Paints a subtle top-down specular sheen gradient to simulate optical refraction.
 * - Renders translucent frosted background fill.
 *
 * When [isGlassEnabled] is false:
 * - Falls back cleanly to Material 3 standard card styling with solid fill and outline.
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(20.dp),
    isGlassEnabled: Boolean = LocalGlassEnabled.current,
    blurRadius: Dp = GlassTokens.BlurRadius,
    elevation: Dp = 0.dp,
    borderWidth: Dp = 1.dp,
    customSurfaceFill: Color? = null,
    customBorderBrush: Brush? = null
): Modifier {
    if (!isGlassEnabled) {
        val outlineColor = MaterialTheme.colorScheme.outline
        val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
        return this
            .then(if (elevation > 0.dp) Modifier.shadow(elevation, shape) else Modifier)
            .clip(shape)
            .border(1.dp, outlineColor.copy(alpha = 0.40f), shape)
            .background(surfaceColor)
    }

    val isDark = isSystemInDarkTheme() || MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val isOled = isDark && MaterialTheme.colorScheme.surface == Color.Black

    val surfaceFill = customSurfaceFill ?: GlassTokens.resolveSurfaceFill(isDark = isDark, isPureBlack = isOled)
    val borderBrush = customBorderBrush ?: GlassTokens.resolveBorderBrush(isDark = isDark, isPureBlack = isOled)

    val specularSheenAlpha = if (isDark) 0.07f else 0.22f

    return this
        .then(if (elevation > 0.dp) Modifier.shadow(elevation, shape) else Modifier)
        .clip(shape)
        .border(borderWidth, borderBrush, shape)
        .background(surfaceFill)
        .drawBehind {
            // Subtle top specular sheen gradient for realistic optical refraction
            drawRect(
                brush = Brush.verticalGradient(
                    0.0f to Color.White.copy(alpha = specularSheenAlpha),
                    0.4f to Color.Transparent
                )
            )
        }
}

/**
 * Reusable GlassCard container composable conforming to the Proximity Liquid Glass design system.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    isGlassEnabled: Boolean = LocalGlassEnabled.current,
    elevation: Dp = 0.dp,
    borderWidth: Dp = 1.dp,
    customSurfaceFill: Color? = null,
    customBorderBrush: Brush? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.liquidGlass(
            shape = shape,
            isGlassEnabled = isGlassEnabled,
            elevation = elevation,
            borderWidth = borderWidth,
            customSurfaceFill = customSurfaceFill,
            customBorderBrush = customBorderBrush
        ),
        content = content
    )
}
