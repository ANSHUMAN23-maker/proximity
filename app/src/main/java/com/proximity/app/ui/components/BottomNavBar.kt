// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.ui.components

import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proximity.app.R
import com.proximity.app.theme.ColorTokens
import com.proximity.app.theme.LocalAppVisuals
import com.proximity.app.theme.rememberReducedMotionEnabled
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ShellNavTab(
    val index: Int,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int
) {
    HOME(0, R.string.nav_home, R.drawable.ic_home),
    HISTORY(1, R.string.nav_history, R.drawable.ic_history),
    SETTINGS(2, R.string.nav_settings, R.drawable.ic_settings)
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun BottomNavBar(
    activeTab: ShellNavTab,
    onSelectTab: (ShellNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val visuals = LocalAppVisuals.current
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotionEnabled()

    val currentActiveTab by rememberUpdatedState(activeTab)
    val currentOnSelectTab by rememberUpdatedState(onSelectTab)

    val puckFraction = remember { Animatable(activeTab.index.toFloat()) }
    val capsuleOffsetPx = remember { Animatable(0f) }

    var isPressedOrDragging by remember { mutableStateOf(false) }
    var dragSpeedFactor by remember { mutableFloatStateOf(0f) }
    val puckJobHolder = remember { object { var job: Job? = null } }

    LaunchedEffect(activeTab, reducedMotion) {
        if (!isPressedOrDragging) {
            puckJobHolder.job?.cancel()
            puckJobHolder.job = launch {
                if (reducedMotion) {
                    puckFraction.snapTo(activeTab.index.toFloat())
                } else {
                    puckFraction.animateTo(
                        targetValue = activeTab.index.toFloat(),
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
            }
        }
    }

    val baseScale by animateFloatAsState(
        targetValue = if (isPressedOrDragging) 1.10f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "PuckBloomScale"
    )

    val speedFactor by animateFloatAsState(
        targetValue = if (isPressedOrDragging) dragSpeedFactor.coerceIn(0f, 0.25f) else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "VelocityStretchFactor"
    )

    val stretchX = baseScale / (1f - speedFactor * 0.65f)
    val stretchY = baseScale * (1f - speedFactor * 0.20f)

    val elevation = if (visuals.isLiquidGlassEnabled) 12.dp else 10.dp
    val maxPullPx = with(density) { 3.5.dp.toPx() }
    val tabCount = ShellNavTab.entries.size

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 40.dp)
            .padding(bottom = 12.dp)
            .graphicsLayer {
                translationX = capsuleOffsetPx.value
            },
        contentAlignment = Alignment.Center
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp),
            shape = CircleShape,
            elevation = elevation
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp)
                    .pointerInput(Unit) {
                        val totalWidthPx = size.width.toFloat().coerceAtLeast(1f)
                        val slotWidthPx = totalWidthPx / tabCount.toFloat()
                        val maxTabIndex = tabCount - 1
                        val touchSlop = viewConfiguration.touchSlop

                        fun xToDiscreteSlot(x: Float): Int =
                            (x / slotWidthPx).toInt().coerceIn(0, maxTabIndex)

                        fun xToCenteredFraction(x: Float): Float =
                            ((x - slotWidthPx / 2f) / slotWidthPx).coerceIn(0f, maxTabIndex.toFloat())

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isPressedOrDragging = true
                            val velocityTracker = VelocityTracker().apply {
                                addPosition(down.uptimeMillis, down.position)
                            }

                            val startX = down.position.x
                            var didDrag = false
                            var lastHapticSlot = currentActiveTab.index
                            var currentSlotFloat = currentActiveTab.index.toFloat()
                            var wasCancelled = false

                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull()
                                if (change == null) {
                                    wasCancelled = true
                                    break
                                }
                                if (!change.pressed) {
                                    if (change.isConsumed) wasCancelled = true
                                    change.consume()
                                    break
                                }

                                if (!didDrag && abs(change.position.x - startX) > touchSlop) {
                                    didDrag = true
                                    puckJobHolder.job?.cancel()
                                }

                                if (didDrag) {
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    val vx = abs(velocityTracker.calculateVelocity().x)
                                    dragSpeedFactor = (vx / 4200f).coerceIn(0f, 0.25f)

                                    val posRatio = xToCenteredFraction(change.position.x)
                                    currentSlotFloat = posRatio

                                    val nearestSlot = posRatio.roundToInt().coerceIn(0, maxTabIndex)
                                    if (nearestSlot != lastHapticSlot) {
                                        lastHapticSlot = nearestSlot
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }

                                    val pullDelta = ((change.position.x - startX) / totalWidthPx)
                                        .coerceIn(-1f, 1f) * maxPullPx

                                    puckJobHolder.job?.cancel()
                                    puckJobHolder.job = scope.launch {
                                        puckFraction.snapTo(posRatio)
                                        capsuleOffsetPx.snapTo(pullDelta)
                                    }
                                    change.consume()
                                }
                            }

                            isPressedOrDragging = false
                            dragSpeedFactor = 0f

                            val snappedIndex = when {
                                wasCancelled -> currentActiveTab.index
                                didDrag -> currentSlotFloat.roundToInt().coerceIn(0, maxTabIndex)
                                else -> xToDiscreteSlot(startX)
                            }
                            val targetTab = ShellNavTab.entries[snappedIndex]

                            puckJobHolder.job?.cancel()
                            puckJobHolder.job = scope.launch {
                                if (reducedMotion) {
                                    capsuleOffsetPx.snapTo(0f)
                                    puckFraction.snapTo(snappedIndex.toFloat())
                                } else {
                                    launch {
                                        capsuleOffsetPx.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                    }
                                    puckFraction.animateTo(
                                        targetValue = snappedIndex.toFloat(),
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }

                            if (!wasCancelled && targetTab != currentActiveTab) {
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                currentOnSelectTab(targetTab)
                            }
                        }
                    }
            ) {
                val slotWidth = maxWidth / tabCount.toFloat()
                val slotWidthPx = with(density) { slotWidth.toPx() }
                val insetDp = 3.dp
                val insetPx = with(density) { insetDp.toPx() }
                val puckWidth = slotWidth - insetDp * 2

                val topAlpha = if (isPressedOrDragging) 0.34f else 0.22f
                val bottomAlpha = if (isPressedOrDragging) 0.16f else 0.10f
                val borderDp = if (isPressedOrDragging) 1.5.dp else 1.dp

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(puckWidth)
                        .height(48.dp)
                        .graphicsLayer {
                            translationX = slotWidthPx * puckFraction.value + insetPx
                            scaleX = stretchX
                            scaleY = stretchY
                        }
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    ColorTokens.BrandTeal.copy(alpha = topAlpha),
                                    ColorTokens.BrandTeal.copy(alpha = bottomAlpha)
                                )
                            )
                        )
                        .border(
                            width = borderDp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = if (isPressedOrDragging) 0.55f else 0.32f),
                                    ColorTokens.BrandTeal.copy(alpha = 0.45f)
                                )
                            ),
                            shape = CircleShape
                        )
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .selectableGroup(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShellNavTab.entries.forEach { tab ->
                        val distance = abs(puckFraction.value - tab.index.toFloat())
                        val proximity = (1f - distance).coerceIn(0f, 1f)
                        val iconScale = 1f + (0.10f * proximity)
                        val itemColor = lerp(
                            start = visuals.textSecondary,
                            stop = visuals.adaptiveTealLabel,
                            fraction = proximity
                        )
                        val labelWeight = if (proximity > 0.5f) FontWeight.Bold else FontWeight.Medium
                        val isSelectedTab = tab == activeTab

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .selectable(
                                    selected = isSelectedTab,
                                    role = Role.Tab,
                                    onClick = {
                                        if (tab != currentActiveTab) {
                                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                            currentOnSelectTab(tab)
                                        }
                                    }
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(id = tab.iconRes),
                                contentDescription = null,
                                tint = itemColor,
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    }
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(id = tab.labelRes),
                                fontSize = 10.sp,
                                fontWeight = labelWeight,
                                color = itemColor,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}
