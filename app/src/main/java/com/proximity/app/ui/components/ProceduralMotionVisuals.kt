// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.proximity.app.theme.ColorTokens
import com.proximity.app.theme.rememberReducedMotionEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class PaymentVisualizerStage {
    CONNECTING,
    CALL_ACTIVE,
    WAITING_FOR_SMS,
    ENDING
}

@Composable
fun PaymentStageVisualizer(
    stage: PaymentVisualizerStage,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 156.dp
) {
    val reducedMotion = rememberReducedMotionEnabled()

    // Only animate the active stage so idle/other stage clocks do not tick in the background,
    // and defer State reads into Canvas draw scope to avoid per-frame recompositions.
    val phaseState = when {
        reducedMotion || stage == PaymentVisualizerStage.ENDING ->
            remember(stage, reducedMotion) { mutableFloatStateOf(0f) }
        stage == PaymentVisualizerStage.CONNECTING -> {
            val infinite = rememberInfiniteTransition(label = "ConnectingSonar")
            infinite.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "SonarRing"
            )
        }
        stage == PaymentVisualizerStage.CALL_ACTIVE -> {
            val infinite = rememberInfiniteTransition(label = "CallActiveBars")
            infinite.animateFloat(
                initialValue = 0f,
                targetValue = (2.0 * PI).toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1400, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "AcousticBars"
            )
        }
        else -> {
            val infinite = rememberInfiniteTransition(label = "WaitingSmsOrbit")
            infinite.animateFloat(
                initialValue = 0f,
                targetValue = (2.0 * PI).toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2400, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "OrbitParticles"
            )
        }
    }

    Canvas(modifier = modifier.size(sizeDp)) {
        val phase = phaseState.value
        val center = Offset(size.width / 2f, size.height / 2f)
        val teal = ColorTokens.BrandTeal
        val lightTeal = ColorTokens.SpecularLightTeal

        when (stage) {
            PaymentVisualizerStage.CONNECTING -> {
                val coreRadius = 28.dp.toPx()
                val maxRingRadius = 72.dp.toPx()
                for (ringIndex in 0..1) {
                    val p = (phase + ringIndex * 0.5f) % 1f
                    val r = coreRadius + (maxRingRadius - coreRadius) * p
                    val alpha = (1f - p) * 0.55f
                    drawCircle(
                        color = teal.copy(alpha = alpha),
                        radius = r,
                        center = center,
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(lightTeal, teal),
                        center = center,
                        radius = coreRadius
                    ),
                    radius = coreRadius,
                    center = center
                )
            }

            PaymentVisualizerStage.CALL_ACTIVE -> {
                val barWidth = 6.dp.toPx()
                val spacing = 10.dp.toPx()
                val totalWidth = 5 * barWidth + 4 * spacing
                val startX = center.x - totalWidth / 2f
                val minH = 18.dp.toPx()
                val maxH = 68.dp.toPx()

                for (i in 0 until 5) {
                    val wave = (sin(phase + i * 0.95f) + 1f) / 2f
                    val barH = minH + (maxH - minH) * wave
                    val x = startX + i * (barWidth + spacing)
                    val y = center.y - barH / 2f
                    drawRoundRect(
                        color = if (i == 2) lightTeal else teal,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barH),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }
            }

            PaymentVisualizerStage.WAITING_FOR_SMS -> {
                val orbitRadius = 45.dp.toPx()
                drawCircle(
                    color = teal.copy(alpha = 0.24f),
                    radius = orbitRadius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(
                    color = teal.copy(alpha = 0.25f),
                    radius = 14.dp.toPx(),
                    center = center
                )
                for (k in 0..1) {
                    val theta = phase + k * PI.toFloat()
                    val px = center.x + orbitRadius * cos(theta)
                    val py = center.y + orbitRadius * sin(theta)
                    drawCircle(
                        color = lightTeal,
                        radius = 6.dp.toPx(),
                        center = Offset(px, py)
                    )
                }
            }

            PaymentVisualizerStage.ENDING -> {
                drawCircle(
                    color = teal,
                    radius = 32.dp.toPx(),
                    center = center,
                    style = Stroke(width = 4.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun PulsingCircularProgress(
    modifier: Modifier = Modifier,
    sizeDp: Dp = 156.dp,
    pulseColor: Color = ColorTokens.BrandTeal,
    accentColor: Color = ColorTokens.SpecularLightTeal,
    pulseDurationMillis: Int = 1500
) {
    val reducedMotion = rememberReducedMotionEnabled()

    val pulseState = if (reducedMotion) {
        remember { mutableFloatStateOf(1f) }
    } else {
        val infinite = rememberInfiniteTransition(label = "PulsingCircularProgress")
        infinite.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = pulseDurationMillis,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "PulseScale"
        )
    }

    Canvas(modifier = modifier.size(sizeDp)) {
        val scale = pulseState.value
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension / 2f

        // Outer static ring (subtle)
        drawCircle(
            color = pulseColor.copy(alpha = 0.12f),
            radius = maxRadius * 0.92f,
            center = center,
            style = Stroke(width = 3.dp.toPx())
        )

        // Inner pulsing ring
        val pulseRadius = maxRadius * 0.55f * scale
        val pulseAlpha = (1f - (scale - 0.85f).coerceIn(0f, 0.3f) / 0.3f) * 0.9f
        drawCircle(
            color = pulseColor.copy(alpha = pulseAlpha),
            radius = pulseRadius,
            center = center,
            style = Stroke(width = 5.dp.toPx())
        )

        // Glow accent that follows the pulse
        val glowRadius = maxRadius * 0.72f * scale
        val glowAlpha = (1f - (scale - 0.85f).coerceIn(0f, 0.3f) / 0.3f) * 0.35f
        drawCircle(
            color = accentColor.copy(alpha = glowAlpha),
            radius = glowRadius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )

        // Core dot
        drawCircle(
            color = pulseColor,
            radius = maxRadius * 0.12f,
            center = center
        )
    }
}

private data class ConfettiParticle(
    val angleRad: Float,
    val speedFactor: Float,
    val color: Color,
    val isSquare: Boolean
)

@Composable
fun CheckmarkConfettiBurst(
    modifier: Modifier = Modifier,
    sizeDp: Dp = 170.dp
) {
    val view = LocalView.current
    val checkProgress = remember { Animatable(0f) }
    val confettiProgress = remember { Animatable(0f) }

    val confettiColors = remember {
        listOf(
            Color(0xFF00E676),
            Color(0xFFFFD54F),
            Color(0xFFFF5252),
            Color(0xFF7C4DFF),
            Color(0xFF00E5FF),
            Color(0xFFFF4081)
        )
    }

    val particles = remember {
        List(22) { idx ->
            val angle = (2.0 * PI * idx / 22.0).toFloat() + (idx % 3) * 0.08f
            val speed = 0.72f + (idx % 5) * 0.11f
            ConfettiParticle(
                angleRad = angle,
                speedFactor = speed,
                color = confettiColors[idx % confettiColors.size],
                isSquare = idx % 2 == 0
            )
        }
    }

    val reducedMotion = rememberReducedMotionEnabled()

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) {
            checkProgress.snapTo(1f)
            confettiProgress.snapTo(1f)
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            launch {
                checkProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
                )
            }
            launch {
                confettiProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 950, easing = FastOutSlowInEasing)
                )
            }
            launch {
                delay(400)
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            }
        }
    }

    val fullCheckPath = remember { Path() }
    val partialCheckPath = remember { Path() }
    val pathMeasure = remember { PathMeasure() }

    Canvas(modifier = modifier.size(sizeDp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val circleRadius = 44.dp.toPx()
        val maxBurstRadius = 78.dp.toPx()
        val gravityDrop = 24.dp.toPx()

        val cp = confettiProgress.value
        if (cp > 0f && cp < 1f) {
            val particleAlpha = (1f - cp).coerceIn(0f, 1f)
            val pSize = 6.dp.toPx()
            particles.forEach { spec ->
                val dist = circleRadius * 0.8f + (maxBurstRadius - circleRadius * 0.5f) * cp * spec.speedFactor
                val px = center.x + cos(spec.angleRad) * dist
                val py = center.y + sin(spec.angleRad) * dist + (gravityDrop * cp * cp)
                if (spec.isSquare) {
                    drawRect(
                        color = spec.color.copy(alpha = particleAlpha),
                        topLeft = Offset(px - pSize / 2f, py - pSize / 2f),
                        size = Size(pSize, pSize)
                    )
                } else {
                    drawCircle(
                        color = spec.color.copy(alpha = particleAlpha),
                        radius = pSize / 2f,
                        center = Offset(px, py)
                    )
                }
            }
        }

        drawCircle(
            color = ColorTokens.BrandTeal,
            radius = circleRadius,
            center = center
        )

        fullCheckPath.reset()
        fullCheckPath.moveTo(center.x - circleRadius * 0.38f, center.y + circleRadius * 0.02f)
        fullCheckPath.lineTo(center.x - circleRadius * 0.08f, center.y + circleRadius * 0.30f)
        fullCheckPath.lineTo(center.x + circleRadius * 0.42f, center.y - circleRadius * 0.26f)

        pathMeasure.setPath(fullCheckPath, false)
        partialCheckPath.reset()
        pathMeasure.getSegment(0f, pathMeasure.length * checkProgress.value, partialCheckPath, true)

        drawPath(
            path = partialCheckPath,
            color = Color.White,
            style = Stroke(
                width = 5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@Composable
fun ErrorCrossGraphic(
    modifier: Modifier = Modifier,
    sizeDp: Dp = 100.dp
) {
    val reducedMotion = rememberReducedMotionEnabled()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) {
            progress.snapTo(1f)
        } else {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
            )
        }
    }

    Canvas(modifier = modifier.size(sizeDp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f
        val errorRed = Color(0xFFFF5252)

        drawCircle(
            color = errorRed.copy(alpha = 0.20f),
            radius = radius,
            center = center
        )

        val offset = radius * 0.42f
        val strokePx = 12.dp.toPx()

        val p1 = (progress.value * 2f).coerceIn(0f, 1f)
        if (p1 > 0f) {
            val start = Offset(center.x - offset, center.y - offset)
            val end = Offset(start.x + (offset * 2f) * p1, start.y + (offset * 2f) * p1)
            drawLine(errorRed, start, end, strokePx, StrokeCap.Round)
        }

        val p2 = ((progress.value - 0.5f) * 2f).coerceIn(0f, 1f)
        if (p2 > 0f) {
            val start = Offset(center.x + offset, center.y - offset)
            val end = Offset(start.x - (offset * 2f) * p2, start.y + (offset * 2f) * p2)
            drawLine(errorRed, start, end, strokePx, StrokeCap.Round)
        }
    }
}

@Composable
fun PendingClockGraphic(
    modifier: Modifier = Modifier,
    sizeDp: Dp = 100.dp
) {
    val reducedMotion = rememberReducedMotionEnabled()
    val haloScaleState = if (reducedMotion) {
        remember { mutableFloatStateOf(1f) }
    } else {
        val infinite = rememberInfiniteTransition(label = "PendingClock")
        infinite.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "AmberHalo"
        )
    }
    val minuteSweepState = if (reducedMotion) {
        remember { mutableFloatStateOf(0f) }
    } else {
        val infinite = rememberInfiniteTransition(label = "PendingClockSweep")
        infinite.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 6000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "MinuteHand"
        )
    }

    Canvas(modifier = modifier.size(sizeDp)) {
        val haloScale = haloScaleState.value
        val minuteSweepAngle = minuteSweepState.value
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = size.minDimension * 0.40f
        val amber = ColorTokens.StatusPendingAmber

        drawCircle(
            color = amber.copy(alpha = 0.18f),
            radius = baseRadius * 1.18f * haloScale,
            center = center
        )

        drawCircle(
            color = amber,
            radius = baseRadius,
            center = center,
            style = Stroke(width = 4.dp.toPx())
        )

        drawLine(
            color = amber,
            start = center,
            end = Offset(center.x, center.y - baseRadius * 0.45f),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )

        val mx = center.x + sin(minuteSweepAngle) * (baseRadius * 0.68f)
        val my = center.y - cos(minuteSweepAngle) * (baseRadius * 0.68f)
        drawLine(
            color = amber,
            start = center,
            end = Offset(mx, my),
            strokeWidth = 3.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun RipplePulseOrb(
    modifier: Modifier = Modifier,
    sizeDp: Dp = 80.dp
) {
    val reducedMotion = rememberReducedMotionEnabled()
    val rippleScaleState = if (reducedMotion) {
        remember { mutableFloatStateOf(0.85f) }
    } else {
        val infinite = rememberInfiniteTransition(label = "SetupPulse")
        infinite.animateFloat(
            initialValue = 0.5f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "RippleScale"
        )
    }

    Canvas(modifier = modifier.size(sizeDp)) {
        val rippleScale = rippleScaleState.value
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension / 2f
        val norm = ((rippleScale - 0.5f) / 0.7f).coerceIn(0f, 1f)

        drawCircle(
            color = ColorTokens.SpecularLightTeal.copy(alpha = (1f - norm) * 0.65f),
            radius = maxRadius * rippleScale,
            center = center,
            style = Stroke(width = 3.dp.toPx())
        )

        drawCircle(
            color = ColorTokens.BrandTeal,
            radius = maxRadius * 0.42f,
            center = center
        )
    }
}
