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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.proximity.app.ui.theme.LocalProximityAccentTheme
import android.provider.Settings
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AnimatedContourBackground(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = LocalProximityAccentTheme.current
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    val lineColor = if (isDark) {
        accent.primary.copy(alpha = 0.16f)
    } else {
        androidx.compose.ui.graphics.Color(0xFF94A3B8).copy(alpha = 0.35f)
    }

    // Reduced motion: disable animation when system animation scale is 0
    val animatorScale = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.TRANSITION_ANIMATION_SCALE,
        1f
    )
    val isReducedMotion = animatorScale == 0f

    val infiniteTransition = if (isReducedMotion) null else rememberInfiniteTransition(label = "contour_anim")
    val phaseState = infiniteTransition?.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    val phase: Float = phaseState?.value ?: 0f

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        if (width <= 0f || height <= 0f) return@Canvas

        val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)

        // Path 1: Top-Left diagonal wavy contour
        val path1 = Path().apply {
            val wave = sin(phase + 0.5f) * 20f
            val wave2 = cos(phase + 1.2f) * 20f
            moveTo(-width * 0.1f, height * 0.05f)
            cubicTo(
                width * 0.05f + wave, height * 0.28f - 70f,
                width * 0.30f - wave2, height * 0.22f + 70f,
                width * 0.38f, height * 0.55f
            )
            cubicTo(
                width * 0.45f + wave2, height * 0.82f - 70f,
                width * 0.55f - wave, height * 0.78f + 70f,
                width * 0.65f, height * 1.10f
            )
        }
        drawPath(path1, color = lineColor, style = stroke)

        // Path 2: Upper-Middle diagonal wavy contour
        val path2 = Path().apply {
            val wave = cos(phase + 0.8f) * 22f
            val wave2 = sin(phase + 1.7f) * 22f
            moveTo(-width * 0.1f, height * 0.25f)
            cubicTo(
                width * 0.10f - wave, height * 0.52f + 75f,
                width * 0.40f + wave2, height * 0.42f - 75f,
                width * 0.50f, height * 0.72f
            )
            cubicTo(
                width * 0.60f - wave2, height * 1.00f + 75f,
                width * 0.75f + wave, height * 0.90f - 75f,
                width * 0.85f, height * 1.10f
            )
        }
        drawPath(path2, color = lineColor, style = stroke)

        // Path 3: Center diagonal wavy contour
        val path3 = Path().apply {
            val wave = sin(phase + 1.2f) * 25f
            val wave2 = cos(phase + 0.4f) * 25f
            moveTo(-width * 0.1f, height * 0.48f)
            cubicTo(
                width * 0.15f + wave, height * 0.75f - 80f,
                width * 0.50f - wave2, height * 0.65f + 80f,
                width * 0.62f, height * 0.92f
            )
            cubicTo(
                width * 0.75f + wave2, height * 1.15f - 80f,
                width * 0.95f - wave, height * 1.02f + 80f,
                width * 1.10f, height * 1.15f
            )
        }
        drawPath(path3, color = lineColor, style = stroke)

        // Path 4: Upper-Right diagonal wavy contour
        val path4 = Path().apply {
            val wave = cos(phase + 1.5f) * 22f
            val wave2 = sin(phase + 0.9f) * 22f
            moveTo(width * 0.10f, -height * 0.1f)
            cubicTo(
                width * 0.25f - wave, height * 0.22f + 75f,
                width * 0.60f + wave2, height * 0.12f - 75f,
                width * 0.72f, height * 0.42f
            )
            cubicTo(
                width * 0.82f - wave2, height * 0.70f + 75f,
                width * 1.00f + wave, height * 0.60f - 75f,
                width * 1.10f, height * 0.82f
            )
        }
        drawPath(path4, color = lineColor, style = stroke)

        // Path 5: Top-Right corner diagonal wavy contour
        val path5 = Path().apply {
            val wave = sin(phase + 0.3f) * 20f
            val wave2 = cos(phase + 1.8f) * 20f
            moveTo(width * 0.45f, -height * 0.1f)
            cubicTo(
                width * 0.58f + wave, height * 0.20f - 70f,
                width * 0.85f - wave2, height * 0.10f + 70f,
                width * 0.95f, height * 0.35f
            )
            cubicTo(
                width * 1.02f + wave2, height * 0.52f - 70f,
                width * 1.08f - wave, height * 0.45f + 70f,
                width * 1.10f, height * 0.58f
            )
        }
        drawPath(path5, color = lineColor, style = stroke)
    }
}
