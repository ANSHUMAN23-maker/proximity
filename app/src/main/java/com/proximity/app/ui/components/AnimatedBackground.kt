// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.proximity.app.theme.ColorTokens
import com.proximity.app.theme.LocalAppVisuals
import com.proximity.app.theme.rememberReducedMotionEnabled
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AnimatedBackground(modifier: Modifier = Modifier) {
    val visuals = LocalAppVisuals.current
    val reducedMotion = rememberReducedMotionEnabled()

    val phaseState = if (reducedMotion) {
        remember { mutableFloatStateOf(0f) }
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "ContourWaves")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 18_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "ContourPhase"
        )
    }

    val waveColor = if (visuals.isDark) {
        ColorTokens.BrandTeal.copy(alpha = 0.16f)
    } else {
        Color(0xFF94A3B8).copy(alpha = 0.35f)
    }

    val path = remember { Path() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(visuals.mainBackground)
    ) {
        val phase = phaseState.value
        val w = size.width
        val h = size.height
        val strokePx = 1.5.dp.toPx()

        for (i in 0 until 5) {
            val bandRatio = (i + 1) / 6f
            val phaseOffset = phase + i * 0.85f
            val osc1 = sin(phaseOffset) * 23f
            val osc2 = cos(phaseOffset * 0.9f + i) * 22f

            val startY = h * (bandRatio - 0.14f) + osc1
            val cp1X = w * 0.33f
            val cp1Y = h * (bandRatio + 0.08f) + osc2
            val cp2X = w * 0.68f
            val cp2Y = h * (bandRatio - 0.06f) - osc1
            val endY = h * (bandRatio + 0.15f) + osc2

            path.reset()
            path.moveTo(-20f, startY)
            path.cubicTo(cp1X, cp1Y, cp2X, cp2Y, w + 20f, endY)

            drawPath(
                path = path,
                color = waveColor,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
    }
}
