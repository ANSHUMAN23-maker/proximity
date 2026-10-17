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
import com.proximity.app.ui.theme.ProximityAccentGreen
import com.proximity.app.ui.theme.ProximityDarkGray
import com.proximity.app.ui.theme.ProximityLightGray
import com.proximity.app.ui.theme.ProximityTextGray
import com.proximity.app.ui.theme.ProximityTextLightGray
import kotlinx.coroutines.delay

@Composable
fun Upi123ProgressDialog(
    isVisible: Boolean,
    showConfigurationOptions: Boolean = false,
    onConfigured: () -> Unit = {},
    onNotConfigured: () -> Unit = {},
    onDismiss: () -> Unit = {}
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
            Upi123ProgressDialogContent(
                showConfigurationOptions = showConfigurationOptions,
                onConfigured = onConfigured,
                onNotConfigured = onNotConfigured,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun Upi123ProgressDialogContent(
    showConfigurationOptions: Boolean = false,
    onConfigured: () -> Unit = {},
    onNotConfigured: () -> Unit = {},
    onDismiss: () -> Unit = {}
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
                        stringResource(R.string.upi123_dlg_setup_complete_title)
                    } else {
                        stringResource(R.string.upi123_dlg_setting_up_title)
                    },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (showConfigurationOptions) {
                        stringResource(R.string.upi123_dlg_setup_complete_message)
                    } else {
                        stringResource(R.string.upi123_dlg_ivr_triggered_message)
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
                                text = stringResource(R.string.upi123_dlg_not_yet),
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
                                containerColor = ProximityAccentGreen
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.upi123_dlg_yes),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.upi123_dlg_configuring),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Light,
                        color = ProximityTextGray,
                        textAlign = TextAlign.Center
                    )

                    // Shortcut for users who already have UPI 123 set up: after a
                    // short delay, offer a way to confirm without waiting for the
                    // whole call flow. This only records the test result â€” it never
                    // touches the ongoing IVR call.
                    var showAlreadySetUp by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(3000)
                        showAlreadySetUp = true
                    }
                    if (showAlreadySetUp) {
                        Spacer(modifier = Modifier.height(20.dp))
                        OutlinedButton(
                            onClick = onConfigured,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, ProximityAccentGreen),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = ProximityAccentGreen
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.upi123_dlg_already_set_up),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
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
                    contentDescription = stringResource(R.string.upi123_dlg_close),
                    tint = ProximityTextLightGray,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}


