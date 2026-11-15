// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.proximity.app.R
import com.proximity.app.theme.ColorTokens
import com.proximity.app.theme.LocalAppVisuals

@Composable
fun PermissionDialog(
    title: String,
    body: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val visuals = LocalAppVisuals.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = visuals.highSurface,
        title = {
            Text(
                text = title,
                color = visuals.textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = body,
                color = visuals.textSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = ColorTokens.BrandTeal)
            ) {
                Text(text = stringResource(id = R.string.action_allow), color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.action_cancel), color = visuals.textSecondary)
            }
        }
    )
}
