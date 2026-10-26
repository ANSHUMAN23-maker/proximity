// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.ui.components

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proximity.app.MainActivity
import com.proximity.app.R
import com.proximity.app.ui.activities.SettingsActivity
import com.proximity.app.ui.activities.TransactionHistoryActivity
import com.proximity.app.ui.theme.LocalGlassEnabled
import com.proximity.app.ui.theme.LocalProximityAccentTheme

enum class NavigationTab {
    HOME,
    HISTORY,
    SETTINGS
}

/**
 * Shared floating taskbar navigation component for Home, Transaction History, and Settings.
 * Styled with Liquid Glass and adaptive theme specular lighting.
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun FloatingBottomNavBar(
    currentTab: NavigationTab,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isGlass = LocalGlassEnabled.current
    val accent = LocalProximityAccentTheme.current

    val targetIndex = when (currentTab) {
        NavigationTab.HOME -> 0
        NavigationTab.HISTORY -> 1
        NavigationTab.SETTINGS -> 2
    }

    val animatedIndex by animateFloatAsState(
        targetValue = targetIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "tabIndicatorPuck"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 64.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .liquidGlass(
                    shape = CircleShape,
                    isGlassEnabled = isGlass,
                    elevation = 12.dp
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

                // Gliding Liquid Pill Indicator Puck
                Box(
                    modifier = Modifier
                        .offset(x = tabWidth * animatedIndex)
                        .width(tabWidth)
                        .height(42.dp)
                        .padding(horizontal = 4.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    accent.primary.copy(alpha = if (isGlass) 0.22f else 0.15f),
                                    accent.primary.copy(alpha = if (isGlass) 0.10f else 0.06f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = accent.primary.copy(alpha = if (isGlass) 0.35f else 0.22f),
                            shape = CircleShape
                        )
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FloatingNavItem(
                        iconRes = R.drawable.ic_home,
                        label = "Home",
                        selected = currentTab == NavigationTab.HOME,
                        onClick = {
                            if (currentTab != NavigationTab.HOME) {
                                val intent = Intent(context, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                }
                                context.startActivity(intent)
                                if (context is Activity && context !is MainActivity) {
                                    context.finish()
                                }
                            }
                        }
                    )
                    FloatingNavItem(
                        iconRes = R.drawable.ic_history,
                        label = "History",
                        selected = currentTab == NavigationTab.HISTORY,
                        onClick = {
                            if (currentTab != NavigationTab.HISTORY) {
                                val intent = Intent(context, TransactionHistoryActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                }
                                context.startActivity(intent)
                                if (context is Activity && context !is MainActivity) {
                                    context.finish()
                                }
                            }
                        }
                    )
                    FloatingNavItem(
                        iconRes = R.drawable.ic_settings,
                        label = "Settings",
                        selected = currentTab == NavigationTab.SETTINGS,
                        onClick = {
                            if (currentTab != NavigationTab.SETTINGS) {
                                val intent = Intent(context, SettingsActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                }
                                context.startActivity(intent)
                                if (context is Activity && context !is MainActivity) {
                                    context.finish()
                                }
                            }
                        }
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
    selected: Boolean,
    onClick: () -> Unit
) {
    val accent = LocalProximityAccentTheme.current
    val color by animateColorAsState(
        targetValue = if (selected) accent.accent else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 200),
        label = "navColor"
    )

    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.10f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "navIconScale"
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .clip(CircleShape)
            .pressScale(onClick = onClick),
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
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = color
            )
        }
    }
}
