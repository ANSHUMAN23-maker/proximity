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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.proximity.app.ui.theme.LocalProximityAccentTheme
import kotlin.math.cos
import kotlin.math.sin

enum class PaymentDisplayPhase {
    CONNECTING,
    CALL_ACTIVE,
    WAITING_FOR_SMS,
    ENDING
}

/**
 * Expressive, theme-reactive phase visualizer for offline transactions.
 * Renders distinct visual choreography for each phase of the offline payment process.
 */
@Composable
fun PaymentPhaseVisualizer(
    phase: PaymentDisplayPhase,
    modifier: Modifier = Modifier
) {
    val accent = LocalProximityAccentTheme.current.primary

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (phase) {
            PaymentDisplayPhase.CONNECTING -> ConnectingSonarBeacon(accent = accent)
            PaymentDisplayPhase.CALL_ACTIVE -> CallActiveAcousticWaveform(accent = accent)
            PaymentDisplayPhase.WAITING_FOR_SMS -> WaitingSmsRadarShimmer(accent = accent)
            PaymentDisplayPhase.ENDING -> EndingSettleCircle(accent = accent)
        }
    }
}

@Composable
private fun ConnectingSonarBeacon(accent: Color) {
    val transition = rememberInfiniteTransition(label = "sonarTransition")
    val pulse1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse1"
    )
    val pulse2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, 700, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse2"
    )

    Canvas(modifier = Modifier.size(150.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val maxRadius = size.width / 2

        // Wave 1
        drawCircle(
            color = accent.copy(alpha = (1f - pulse1).coerceIn(0f, 0.6f)),
            center = center,
            radius = maxRadius * pulse1,
            style = Stroke(width = 3.dp.toPx())
        )
        // Wave 2
        drawCircle(
            color = accent.copy(alpha = (1f - pulse2).coerceIn(0f, 0.6f)),
            center = center,
            radius = maxRadius * pulse2,
            style = Stroke(width = 3.dp.toPx())
        )
        // Core Beacon Orb
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent, accent.copy(alpha = 0.6f)),
                center = center,
                radius = 28.dp.toPx()
            ),
            center = center,
            radius = 28.dp.toPx()
        )
    }
}

@Composable
private fun CallActiveAcousticWaveform(accent: Color) {
    val transition = rememberInfiniteTransition(label = "waveformTransition")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "waveTime"
    )

    Canvas(modifier = Modifier.size(150.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val barCount = 5
        val barWidth = 6.dp.toPx()
        val spacing = 10.dp.toPx()
        val totalWidth = (barCount * barWidth) + ((barCount - 1) * spacing)
        val startX = center.x - (totalWidth / 2)

        for (i in 0 until barCount) {
            val phaseOffset = i * 0.9f
            val heightFactor = (sin(time + phaseOffset) * 0.5f + 0.5f).coerceIn(0.2f, 1f)
            val barHeight = 60.dp.toPx() * heightFactor
            val x = startX + (i * (barWidth + spacing)) + (barWidth / 2)

            drawLine(
                brush = Brush.verticalGradient(
                    colors = listOf(accent, accent.copy(alpha = 0.4f)),
                    startY = center.y - (barHeight / 2),
                    endY = center.y + (barHeight / 2)
                ),
                start = Offset(x, center.y - (barHeight / 2)),
                end = Offset(x, center.y + (barHeight / 2)),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun WaitingSmsRadarShimmer(accent: Color) {
    val transition = rememberInfiniteTransition(label = "radarTransition")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "radarAngle"
    )

    Canvas(modifier = Modifier.size(150.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = 45.dp.toPx()

        // Base Ring
        drawCircle(
            color = accent.copy(alpha = 0.2f),
            center = center,
            radius = radius,
            style = Stroke(width = 2.dp.toPx())
        )

        // Orbiting Particle
        val rad = Math.toRadians(angle.toDouble()).toFloat()
        val px = center.x + radius * cos(rad)
        val py = center.y + radius * sin(rad)

        drawCircle(
            color = accent,
            center = Offset(px, py),
            radius = 5.dp.toPx()
        )
        // Orbiting Particle 180 deg opposite
        val pxOpposite = center.x + radius * cos(rad + Math.PI.toFloat())
        val pyOpposite = center.y + radius * sin(rad + Math.PI.toFloat())

        drawCircle(
            color = accent.copy(alpha = 0.6f),
            center = Offset(pxOpposite, pyOpposite),
            radius = 3.5.dp.toPx()
        )
    }
}

@Composable
private fun EndingSettleCircle(accent: Color) {
    Canvas(modifier = Modifier.size(150.dp)) {
        drawCircle(
            color = accent.copy(alpha = 0.5f),
            center = Offset(size.width / 2, size.height / 2),
            radius = 32.dp.toPx(),
            style = Stroke(width = 4.dp.toPx())
        )
    }
}
