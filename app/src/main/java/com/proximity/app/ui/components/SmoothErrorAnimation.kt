// SPDX-License-Identifier: Apache-2.0
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun SmoothErrorAnimation(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFFF5252)
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(100.dp)) {
            val strokeWidth = 12.dp.toPx()
            
            // Draw circle background
            drawCircle(
                color = color.copy(alpha = 0.2f),
                radius = size.width / 2,
                center = Offset(size.width / 2, size.height / 2)
            )

            // Draw X mark
            val p1StartX = size.width * 0.35f
            val p1StartY = size.height * 0.35f
            val p1EndX = size.width * 0.65f
            val p1EndY = size.height * 0.65f

            val p2StartX = size.width * 0.65f
            val p2StartY = size.height * 0.35f
            val p2EndX = size.width * 0.35f
            val p2EndY = size.height * 0.65f

            if (progress.value > 0f) {
                // Line 1
                val l1 = (progress.value * 2).coerceAtMost(1f)
                if (l1 > 0f) {
                    val currX = p1StartX + (p1EndX - p1StartX) * l1
                    val currY = p1StartY + (p1EndY - p1StartY) * l1
                    drawLine(
                        color = color,
                        start = Offset(p1StartX, p1StartY),
                        end = Offset(currX, currY),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
                
                // Line 2
                val l2 = ((progress.value - 0.5f) * 2).coerceAtLeast(0f).coerceAtMost(1f)
                if (l2 > 0f) {
                    val currX = p2StartX + (p2EndX - p2StartX) * l2
                    val currY = p2StartY + (p2EndY - p2StartY) * l2
                    drawLine(
                        color = color,
                        start = Offset(p2StartX, p2StartY),
                        end = Offset(currX, currY),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}
