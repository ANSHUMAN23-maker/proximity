// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.proximity.app.ui.theme.LocalProximityAccentTheme
import com.proximity.app.ui.theme.ProximityTheme
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class ConfettiParticle(
    val angle: Float,
    val speed: Float,
    val color: Color,
    val size: Float,
    val isSquare: Boolean
)

@Composable
fun SmoothCheckmarkAnimation(
    modifier: Modifier = Modifier,
    circleColor: Color = LocalProximityAccentTheme.current.primary,
    checkmarkColor: Color = Color.White
) {
    val progress = remember { Animatable(0f) }
    val burstProgress = remember { Animatable(0f) }

    val particleColors = listOf(
        Color(0xFF00E676), // Bright Emerald
        Color(0xFFFFD54F), // Gold
        Color(0xFFFF5252), // Coral Pink
        Color(0xFF7C4DFF), // Purple
        Color(0xFF00E5FF), // Cyan
        Color(0xFFFF4081)  // Pink
    )

    val particles = remember {
        List(22) { index ->
            ConfettiParticle(
                angle = (index * (360f / 22f) + Random.nextFloat() * 12f) * (Math.PI.toFloat() / 180f),
                speed = 110f + Random.nextFloat() * 110f,
                color = particleColors[index % particleColors.size],
                size = 4f + Random.nextFloat() * 5f,
                isSquare = index % 2 == 0
            )
        }
    }

    LaunchedEffect(Unit) {
        launch {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
            )
        }
        launch {
            burstProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 950, easing = FastOutSlowInEasing)
            )
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(170.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val circleRadius = 44.dp.toPx()

            // 1. Draw Colour Bomb / Confetti Explosion
            if (burstProgress.value > 0f) {
                val pVal = burstProgress.value
                val alpha = (1f - pVal).coerceIn(0f, 1f)

                particles.forEach { particle ->
                    val distance = circleRadius + (particle.speed * pVal)
                    val px = center.x + cos(particle.angle) * distance
                    val py = center.y + sin(particle.angle) * distance + (pVal * pVal * 25f)

                    if (particle.isSquare) {
                        drawRect(
                            color = particle.color.copy(alpha = alpha),
                            topLeft = Offset(px - particle.size, py - particle.size),
                            size = Size(particle.size * 2, particle.size * 2)
                        )
                    } else {
                        drawCircle(
                            color = particle.color.copy(alpha = alpha),
                            radius = particle.size,
                            center = Offset(px, py)
                        )
                    }
                }
            }

            // 2. Solid Circle Background
            drawCircle(
                color = circleColor,
                radius = circleRadius,
                center = center
            )

            // 3. Draw Animated Pure White Checkmark inside the solid circle
            val strokeWidth = 5.dp.toPx()
            val path = Path()

            val startX = center.x - circleRadius * 0.35f
            val startY = center.y
            val midX = center.x - circleRadius * 0.05f
            val midY = center.y + circleRadius * 0.32f
            val endX = center.x + circleRadius * 0.38f
            val endY = center.y - circleRadius * 0.28f

            if (progress.value > 0f) {
                path.moveTo(startX, startY)
                val currentMidX = startX + (midX - startX) * (progress.value.coerceAtMost(0.5f) * 2)
                val currentMidY = startY + (midY - startY) * (progress.value.coerceAtMost(0.5f) * 2)
                path.lineTo(currentMidX, currentMidY)

                if (progress.value > 0.5f) {
                    val progress2 = (progress.value - 0.5f) * 2
                    val currentEndX = midX + (endX - midX) * progress2
                    val currentEndY = midY + (endY - midY) * progress2
                    path.lineTo(currentEndX, currentEndY)
                }
            }

            drawPath(
                path = path,
                color = checkmarkColor,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

@Preview(name = "Checkmark Burst - Dark", showBackground = true, backgroundColor = 0xFF090D14)
@Preview(name = "Checkmark Burst - Light", showBackground = true, backgroundColor = 0xFFF8FAFC)
@Composable
private fun SmoothCheckmarkAnimationPreview() {
    ProximityTheme {
        SmoothCheckmarkAnimation()
    }
}
