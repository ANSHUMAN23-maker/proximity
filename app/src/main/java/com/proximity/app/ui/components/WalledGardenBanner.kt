// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proximity.app.R
import com.proximity.app.network.WalledGardenResult
import com.proximity.app.ui.activities.RechargeWebActivity
import com.proximity.app.ui.theme.ProximityBrandAccent
import com.proximity.app.ui.theme.ProximityMediumGray
import com.proximity.app.ui.theme.ProximityOutlineGray
import com.proximity.app.ui.theme.ProximityTextGray
import com.proximity.app.ui.theme.ProximityTextLightGray

@Composable
fun WalledGardenBanner(
    result: WalledGardenResult,
    operatorName: String,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    AnimatedVisibility(
        visible = result is WalledGardenResult.Detected || result is WalledGardenResult.NoNetwork,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
        ) + fadeIn(tween(280)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
        ) + fadeOut(tween(280)),
        modifier = modifier
    ) {
        when (result) {
            is WalledGardenResult.Detected -> {
                Box(
                    modifier = Modifier
                        .animateContentSize()
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(ProximityMediumGray)
                        .clickable(role = Role.Button) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            context.startActivity(
                                RechargeWebActivity.buildIntent(
                                    context,
                                    result.portalUrl,
                                    operatorName,
                                    result.network
                                )
                            )
                        }
                ) {
                    Row {
                        // Amber left accent bar
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(ProximityBrandAccent)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.recharge_banner_title),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ProximityBrandAccent
                                )
                                Text(
                                    text = stringResource(R.string.recharge_banner_body, operatorName),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = ProximityTextGray
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = ProximityBrandAccent,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
            is WalledGardenResult.NoNetwork -> {
                Box(
                    modifier = Modifier
                        .animateContentSize()
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(ProximityMediumGray)
                        .clickable(role = Role.Button) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenSettings()
                        }
                ) {
                    Row {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(ProximityOutlineGray)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.recharge_no_network_title),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ProximityTextLightGray
                                )
                                Text(
                                    text = stringResource(R.string.recharge_no_network_body),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = ProximityTextGray
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.WifiOff,
                                contentDescription = null,
                                tint = ProximityTextLightGray,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
            else -> Unit
        }
    }
}
