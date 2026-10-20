// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity
//
// Draft Animated Splash Screen Template
// This file is in the /splashscreen workspace folder and does not impact main app compilation.

package com.proximity.app.splashscreen.draft

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draft Animated Splash Screen Composable
 *
 * Features:
 * 1. Animated Logo Scale & Alpha entrance.
 * 2. Radial pulsing brand glow rings.
 * 3. Soft animated background contour waves.
 * 4. Tagline fade-in with smooth exit trigger.
 */
@Composable
fun AnimatedSplashScreenDraft(
    onSplashFinished: () -> Unit = {}
) {
    // Entrance Animatable states
    val scale = remember { Animatable(0.4f) }
    val alpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }

    // Ambient background infinite animation
    val infiniteTransition = rememberInfiniteTransition(label = "splash_ambient")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    // Sequence entrance animation
    LaunchedEffect(Unit) {
        // Animate logo scale and alpha in parallel
        alpha.animateTo(1f, animationSpec = tween(600))
        scale.animateTo(1f, animationSpec = tween(700, easing = FastOutSlowInEasing))
        textAlpha.animateTo(1f, animationSpec = tween(500))

        // Hold for splash display duration
        delay(1800)

        // Trigger finish callback to transition to main app
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D14)),
        contentAlignment = Alignment.Center
    ) {
        // Background Contour Line Art
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w > 0f && h > 0f) {
                val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                val lineColor = Color(0xFF008774).copy(alpha = 0.12f)

                // Wavy background path
                val p = Path().apply {
                    val startY = h * 0.35f + sin(phase) * 20f
                    val endY = h * 0.65f + cos(phase) * 20f
                    moveTo(-20f, startY)
                    cubicTo(w * 0.35f, startY - 80f, w * 0.65f, endY + 80f, w + 20f, endY)
                }
                drawPath(p, color = lineColor, style = stroke)
            }
        }

        // Ambient Pulse Glowing Ring
        Box(
            modifier = Modifier
                .size(160.dp * pulseScale)
                .clip(CircleShape)
                .background(Color(0xFF008774).copy(alpha = 0.10f))
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .scale(scale.value)
                .alpha(alpha.value)
        ) {
            // Proximity Brand Logo Badge
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF008774)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "P",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Proximity",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Offline Payments, Simplified",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(textAlpha.value)
            )
        }
    }
}
