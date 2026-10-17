// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*


import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.proximity.app.ui.components.*
import com.proximity.app.R
import com.proximity.app.ui.theme.ProximityDarkGray
import com.proximity.app.ui.theme.ProximityLightGray
import com.proximity.app.ui.theme.ProximityOutlineGray
import com.proximity.app.ui.theme.ProximityTextGray
import com.proximity.app.ui.theme.ProximityTextLightGray
import com.proximity.app.ui.theme.ProximityTextPale
import com.proximity.app.ui.theme.LocalProximityAccentTheme
import kotlinx.coroutines.delay

@Composable
fun UssdProgressDialog(
    isVisible: Boolean,
    progressMessage: String = stringResource(R.string.ussd_dlg_initializing),
    showConfigurationOptions: Boolean = false,
    onConfigured: () -> Unit = {},
    onNotConfigured: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onDoesNotWork: () -> Unit = {}
) {
    if (isVisible) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            UssdProgressDialogContent(
                progressMessage = progressMessage,
                showConfigurationOptions = showConfigurationOptions,
                onConfigured = onConfigured,
                onNotConfigured = onNotConfigured,
                onDismiss = onDismiss,
                onDoesNotWork = onDoesNotWork
            )
        }
    }
}

@Composable
private fun UssdProgressDialogContent(
    progressMessage: String,
    showConfigurationOptions: Boolean = false,
    onConfigured: () -> Unit = {},
    onNotConfigured: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onDoesNotWork: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = ProximityDarkGray),
        border = BorderStroke(1.dp, ProximityLightGray)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
                    .padding(top = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SmoothPulseAnimation(modifier = Modifier.size(80.dp))

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = if (showConfigurationOptions) {
                        stringResource(R.string.ussd_dlg_setup_complete_title)
                    } else {
                        stringResource(R.string.ussd_dlg_setting_up_title)
                    },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (showConfigurationOptions) {
                        stringResource(R.string.ussd_dlg_setup_complete_body)
                    } else {
                        stringResource(R.string.ussd_dlg_setting_up_body)
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = ProximityTextLightGray,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

                if (showConfigurationOptions) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = onNotConfigured,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ProximityLightGray
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.ussd_dlg_not_yet),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }

                        Button(
                            onClick = onConfigured,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LocalProximityAccentTheme.current.accent
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.ussd_dlg_yes),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Text(
                        text = progressMessage,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Light,
                        color = ProximityTextGray,
                        textAlign = TextAlign.Center
                    )

                    // After a short delay, offer two shortcuts: confirm it's
                    // already set up, or report it isn't working. Delayed so
                    // neither tempts an action before USSD has had time to
                    // respond (mirrors the UPI 123 dialog's delayed shortcut).
                    // Neither touches the ongoing call.
                    var showShortcuts by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(3000)
                        showShortcuts = true
                    }
                    if (showShortcuts) {
                        Spacer(modifier = Modifier.height(20.dp))

                        OutlinedButton(
                            onClick = onConfigured,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, LocalProximityAccentTheme.current.accent),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = LocalProximityAccentTheme.current.accent
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.ussd_dlg_already_set_up),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = onDoesNotWork,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, ProximityOutlineGray),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = ProximityTextPale
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.ussd_dlg_does_not_work),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.ussd_dlg_cd_close),
                    tint = ProximityTextLightGray,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}


