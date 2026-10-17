// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.ui.components

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.proximity.app.ui.theme.ProximityMediumGray

fun Modifier.shimmer(): Modifier = composed {
    val shimmerColors = listOf(
        ProximityMediumGray.copy(alpha = 0.4f),
        ProximityMediumGray.copy(alpha = 0.8f),
        ProximityMediumGray.copy(alpha = 0.4f),
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnimation = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translation"
    )

    background(
        brush = Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(-1000f + translateAnimation.value, 0f),
            end = Offset(translateAnimation.value, 0f)
        )
    )
}
