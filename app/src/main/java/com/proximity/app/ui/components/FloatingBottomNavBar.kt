// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.ui.components

import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.proximity.app.MainActivity
import com.proximity.app.R
import com.proximity.app.ui.activities.SettingsActivity
import com.proximity.app.ui.activities.TransactionHistoryActivity
import com.proximity.app.ui.theme.LocalGlassEnabled
import com.proximity.app.ui.theme.LocalProximityAccentTheme
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

enum class NavigationTab {
    HOME,
    HISTORY,
    SETTINGS
}

/**
 * Shared floating taskbar navigation component for Home, Transaction History, and Settings.
 * Features a draggable liquid indicator puck that blooms (grows 28% larger) when held/dragged,
 * exhibits velocity-dependent organic liquid stretch, pulls the capsule organically, and glides
 * between tabs with physics-based springs. Fully adapts to both Liquid Glass and Normal themes.
 */
@Composable
fun FloatingBottomNavBar(
    currentTab: NavigationTab,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isGlass = LocalGlassEnabled.current
    val accent = LocalProximityAccentTheme.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val animationScope = rememberCoroutineScope()

    val targetIndex = when (currentTab) {
        NavigationTab.HOME -> 0
        NavigationTab.HISTORY -> 1
        NavigationTab.SETTINGS -> 2
    }

    val offsetAnimation = remember { Animatable(0f) }

    fun navigateToTab(tab: NavigationTab) {
        if (tab == currentTab) return
        val targetClass = when (tab) {
            NavigationTab.HOME -> MainActivity::class.java
            NavigationTab.HISTORY -> TransactionHistoryActivity::class.java
            NavigationTab.SETTINGS -> SettingsActivity::class.java
        }
        val intent = Intent(context, targetClass).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        context.startActivity(intent)
        if (context is Activity && context !is MainActivity) {
            context.finish()
        }
        if (context is Activity) {
            if (Build.VERSION.SDK_INT >= 34) {
                context.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
                context.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
            } else {
                @Suppress("DEPRECATION")
                context.overridePendingTransition(0, 0)
            }
        }
    }

    // Holder to resolve self-reference in drag callbacks
    class DragAnimHolder {
        var instance: DampedDragAnimation? = null
    }
    val holder = remember { DragAnimHolder() }

    var tabWidthPx by remember { mutableIntStateOf(0) }

    val dampedDragAnimation = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = targetIndex.toFloat(),
            valueRangeStart = 0f,
            valueRangeEnd = 2f,
            visibilityThreshold = 0.001f,
            initialScale = 1.0f,
            pressedScale = 1.28f, // 28% expansion matching Kyant fluid jelly demo
            onDragStarted = {},
            onDragStopped = {
                val currentVal = holder.instance?.value ?: targetIndex.toFloat()
                val newIndex = currentVal.roundToInt().coerceIn(0, 2)
                val selectedTab = when (newIndex) {
                    0 -> NavigationTab.HOME
                    1 -> NavigationTab.HISTORY
                    else -> NavigationTab.SETTINGS
                }
                if (selectedTab != currentTab) {
                    holder.instance?.animateToValue(newIndex.toFloat())
                    navigateToTab(selectedTab)
                } else {
                    holder.instance?.animateToValue(newIndex.toFloat())
                }
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDrag = { _, dragX ->
                if (tabWidthPx > 0) {
                    holder.instance?.updateValue(
                        (holder.instance?.value ?: (targetIndex.toFloat() + dragX / tabWidthPx)).coerceIn(0f, 2f)
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragX)
                    }
                }
            }
        ).also { holder.instance = it }
    }

    // Keep position in sync if currentTab changes externally
    LaunchedEffect(currentTab) {
        if (dampedDragAnimation.value != targetIndex.toFloat()) {
            dampedDragAnimation.animateToValue(targetIndex.toFloat())
        }
    }

    // Haptic tick when the puck crosses a tab threshold during drag
    var lastHapticIndex by remember { mutableIntStateOf(targetIndex) }
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { dampedDragAnimation.value.roundToInt().coerceIn(0, 2) }
            .collect { hoveredIndex ->
                if (hoveredIndex != lastHapticIndex) {
                    lastHapticIndex = hoveredIndex
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 64.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val totalWidthPx = with(density) { maxWidth.toPx() }
            val panelOffset by remember(density, totalWidthPx) {
                derivedStateOf {
                    val fraction = if (totalWidthPx > 0f) (offsetAnimation.value / totalWidthPx).coerceIn(-1f, 1f) else 0f
                    with(density) {
                        3.5f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .graphicsLayer {
                        translationX = panelOffset
                    }
                    .then(
                        if (isGlass) {
                            Modifier.liquidGlass(
                                shape = CircleShape,
                                isGlassEnabled = true,
                                elevation = 12.dp
                            )
                        } else {
                            Modifier
                                .shadow(8.dp, CircleShape)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                                    shape = CircleShape
                                )
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f),
                                    shape = CircleShape
                                )
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    val tabWidth = maxWidth / 3
                    val currentTabWidthPx = with(density) { tabWidth.toPx() }.roundToInt()
                    if (tabWidthPx != currentTabWidthPx) {
                        tabWidthPx = currentTabWidthPx
                    }

                    val progress = dampedDragAnimation.pressProgress
                    val speed = abs(dampedDragAnimation.velocity / 10f).coerceIn(0f, 0.25f)
                    val stretchX = dampedDragAnimation.scaleX / (1f - speed * 0.75f)
                    val stretchY = dampedDragAnimation.scaleY * (1f - speed * 0.25f)

                    // 1. Navigation Tabs Row (Base layer: icons and labels, clickable for direct tab hops)
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FloatingNavItem(
                            iconRes = R.drawable.ic_home,
                            label = "Home",
                            tabIndex = 0,
                            dampedAnimation = dampedDragAnimation,
                            onClick = {
                                dampedDragAnimation.animateToValue(0f)
                                navigateToTab(NavigationTab.HOME)
                            }
                        )
                        FloatingNavItem(
                            iconRes = R.drawable.ic_history,
                            label = "History",
                            tabIndex = 1,
                            dampedAnimation = dampedDragAnimation,
                            onClick = {
                                dampedDragAnimation.animateToValue(1f)
                                navigateToTab(NavigationTab.HISTORY)
                            }
                        )
                        FloatingNavItem(
                            iconRes = R.drawable.ic_settings,
                            label = "Settings",
                            tabIndex = 2,
                            dampedAnimation = dampedDragAnimation,
                            onClick = {
                                dampedDragAnimation.animateToValue(2f)
                                navigateToTab(NavigationTab.SETTINGS)
                            }
                        )
                    }

                    // 2. Liquid Indicator Puck (Top layer: intercepts drag/hold on the active tab)
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                translationX = (tabWidth * dampedDragAnimation.value).toPx()
                                scaleX = stretchX
                                scaleY = stretchY
                            }
                            .then(dampedDragAnimation.modifier)
                            .width(tabWidth)
                            .height(42.dp)
                            .padding(horizontal = 4.dp)
                            .clip(CircleShape)
                            .background(
                                if (isGlass) {
                                    Brush.linearGradient(
                                        colors = listOf(
                                            accent.primary.copy(alpha = 0.22f + 0.14f * progress),
                                            accent.primary.copy(alpha = 0.10f + 0.08f * progress)
                                        )
                                    )
                                } else {
                                    SolidColor(
                                        accent.primary.copy(alpha = 0.16f + 0.14f * progress)
                                    )
                                }
                            )
                            .border(
                                width = (1.dp + 0.5.dp * progress),
                                brush = if (isGlass) {
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.35f * progress),
                                            accent.primary.copy(alpha = 0.35f + 0.25f * progress),
                                            accent.primary.copy(alpha = 0.20f + 0.15f * progress)
                                        )
                                    )
                                } else {
                                    SolidColor(
                                        accent.primary.copy(alpha = 0.35f + 0.20f * progress)
                                    )
                                },
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.FloatingNavItem(
    iconRes: Int,
    label: String,
    tabIndex: Int,
    dampedAnimation: DampedDragAnimation,
    onClick: () -> Unit
) {
    val accent = LocalProximityAccentTheme.current

    // Proximity to the current puck position (1.0 = puck directly over this tab, 0.0 = distant)
    val activeDistance = abs(dampedAnimation.value - tabIndex.toFloat()).coerceIn(0f, 1f)
    val proximity = 1f - activeDistance

    // Dynamic color: smoothly illuminates in accent color as the puck glides over
    val targetColor = lerpColor(
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
        accent.accent,
        proximity
    )
    val color by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 150),
        label = "navColor"
    )

    // Continuous dynamic zoom: icon grows when the puck passes over it or when pressed
    val dynamicIconScale = lerp(
        1.0f,
        1.16f,
        proximity * (0.6f + 0.4f * dampedAnimation.pressProgress)
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .clip(CircleShape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                tint = color,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = dynamicIconScale
                        scaleY = dynamicIconScale
                    }
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (proximity > 0.5f) FontWeight.Bold else FontWeight.Medium,
                color = color
            )
        }
    }
}
