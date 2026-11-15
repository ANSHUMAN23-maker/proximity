// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.proximity.app.theme.ColorTokens
import com.proximity.app.theme.LocalAppVisuals

fun Modifier.pressScale(
    enabled: Boolean = true,
    targetScale: Float = 0.96f,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) targetScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "PressScale"
    )

    val clickMod = if (onClick != null && enabled) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    } else {
        Modifier
    }

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            while (true) {
                awaitPointerEventScope {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    waitForUpOrCancellation()
                    isPressed = false
                }
            }
        }
        .then(clickMod)
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    shape: Shape = RoundedCornerShape(cornerRadius),
    elevatedOverlay: Boolean = false,
    elevation: Dp = 0.dp,
    customBorderBrush: Brush? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    val visuals = LocalAppVisuals.current

    val fillColor = if (!visuals.isLiquidGlassEnabled) {
        visuals.cardSurface
    } else if (visuals.isOledBlack) {
        Color(0xFF000000).copy(alpha = 0.82f)
    } else if (visuals.isDark) {
        if (elevatedOverlay) Color(0xFF101721).copy(alpha = 0.75f)
        else Color(0xFF090D14).copy(alpha = 0.68f)
    } else {
        if (elevatedOverlay) Color(0xFFFFFFFF).copy(alpha = 0.85f)
        else Color(0xFFFFFFFF).copy(alpha = 0.75f)
    }

    val rimBrush = customBorderBrush ?: remember(
        visuals.isLiquidGlassEnabled,
        visuals.isOledBlack,
        visuals.isDark,
        visuals.outline
    ) {
        if (!visuals.isLiquidGlassEnabled) {
            Brush.linearGradient(
                listOf(
                    visuals.outline.copy(alpha = 0.40f),
                    visuals.outline.copy(alpha = 0.40f)
                )
            )
        } else if (visuals.isOledBlack) {
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.28f),
                    ColorTokens.BrandTeal.copy(alpha = 0.15f),
                    Color.Transparent
                )
            )
        } else if (visuals.isDark) {
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.32f),
                    ColorTokens.SpecularLightTeal.copy(alpha = 0.18f),
                    Color.White.copy(alpha = 0.06f)
                )
            )
        } else {
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.90f),
                    Color(0xFFE2E8F0).copy(alpha = 0.60f),
                    Color.White.copy(alpha = 0.30f)
                )
            )
        }
    }

    val topSheenAlpha = if (!visuals.isLiquidGlassEnabled) {
        0f
    } else if (visuals.isDark) {
        0.07f
    } else {
        0.22f
    }

    Box(
        modifier = modifier
            .then(if (elevation > 0.dp) Modifier.shadow(elevation, shape, clip = false) else Modifier)
            .clip(shape)
            .border(width = 1.dp, brush = rimBrush, shape = shape),
        contentAlignment = contentAlignment
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(fillColor)
        )

        Box(
            modifier = Modifier
                .drawWithContent {
                    if (topSheenAlpha > 0f) {
                        val sheenHeight = size.height * 0.40f
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = topSheenAlpha),
                                    Color.Transparent
                                ),
                                startY = 0f,
                                endY = sheenHeight
                            ),
                            topLeft = Offset.Zero,
                            size = Size(size.width, sheenHeight)
                        )
                    }
                    drawContent()
                },
            contentAlignment = contentAlignment,
            content = content
        )
    }
}
