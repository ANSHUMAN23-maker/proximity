// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proximity.app.ui.theme.LocalProximityAccentTheme
import com.proximity.app.ui.theme.ProximityTheme

@Composable
fun SimOperatorLogoBadge(
    operatorKey: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val key = operatorKey.lowercase()
    when {
        key.contains("jio") -> {
            Surface(
                modifier = modifier.size(size),
                shape = CircleShape,
                color = Color(0xFF0A2885)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Jio",
                        color = Color.White,
                        fontSize = (size.value * 0.38f).sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        }
        key.contains("airtel") -> {
            Surface(
                modifier = modifier.size(size),
                shape = CircleShape,
                color = Color(0xFFE10600)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "airtel",
                        color = Color.White,
                        fontSize = (size.value * 0.30f).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        key.contains("voda") || key.contains("vi") || key.contains("idea") -> {
            Surface(
                modifier = modifier.size(size),
                shape = CircleShape,
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE0E0E0))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Vi",
                            color = Color(0xFFE60000),
                            fontSize = (size.value * 0.44f).sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
        key.contains("bsnl") -> {
            Surface(
                modifier = modifier.size(size),
                shape = CircleShape,
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE0E0E0))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "BSNL",
                        color = Color(0xFF003399),
                        fontSize = (size.value * 0.25f).sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
        else -> {
            Surface(
                modifier = modifier.size(size),
                shape = CircleShape,
                color = LocalProximityAccentTheme.current.primary.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.SimCard,
                        contentDescription = null,
                        tint = LocalProximityAccentTheme.current.primary,
                        modifier = Modifier.size(size * 0.55f)
                    )
                }
            }
        }
    }
}

@Preview(name = "Operator Badges - Light", showBackground = true)
@Composable
private fun SimOperatorLogoBadgePreview() {
    ProximityTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SimOperatorLogoBadge("jio")
            SimOperatorLogoBadge("airtel")
            SimOperatorLogoBadge("vodafone")
            SimOperatorLogoBadge("bsnl")
        }
    }
}
