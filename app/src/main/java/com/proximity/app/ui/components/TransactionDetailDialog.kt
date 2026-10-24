// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.proximity.app.R
import com.proximity.app.data.Transaction
import com.proximity.app.ui.theme.ProximityStatusError
import com.proximity.app.ui.theme.LocalProximityAccentTheme
import com.proximity.app.ui.theme.statusColor
import com.proximity.app.utils.CurrencyFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransactionDetailDialog(
    transaction: Transaction,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val accent = LocalProximityAccentTheme.current
    val statusColor = statusColor(transaction.status)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.detail_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.detail_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Large amount
                Text(
                    text = stringResource(R.string.amount_rupees, CurrencyFormat.inr(transaction.amount)),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Status pill
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = when (transaction.status.uppercase()) {
                            "USER_ATTESTED", "USER_CONFIRMED" -> stringResource(R.string.status_label_user_confirmed)
                            "SUCCESS", "SUCCESSFUL", "COMPLETED" -> stringResource(R.string.status_label_success)
                            "UNVERIFIED" -> stringResource(R.string.status_label_unverified)
                            "NEEDS_REVIEW" -> stringResource(R.string.status_label_needs_review)
                            "CANCELLED" -> stringResource(R.string.status_label_cancelled)
                            "FAILED", "DECLINED" -> stringResource(R.string.status_label_failed)
                            else -> stringResource(R.string.status_label_pending)
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                }

                // Non-success outcomes get a plain-language explanation
                statusExplainerText(transaction.status)?.let { explainer ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = explainer,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Detail card container
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (!transaction.bankRef.isNullOrEmpty()) {
                            DetailRow(
                                label = stringResource(R.string.label_bank_reference),
                                value = transaction.bankRef,
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(transaction.bankRef))
                                    Toast.makeText(context, "Bank reference copied", Toast.LENGTH_SHORT).show()
                                }
                            )
                            DetailDivider()
                        }

                        DetailRow(
                            label = stringResource(R.string.label_bank),
                            value = transaction.bankName,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(transaction.bankName))
                                Toast.makeText(context, "Bank copied", Toast.LENGTH_SHORT).show()
                            }
                        )

                        if (!transaction.recipientName.isNullOrEmpty()) {
                            DetailDivider()
                            DetailRow(
                                label = stringResource(R.string.detail_label_recipient),
                                value = transaction.recipientName,
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(transaction.recipientName))
                                    Toast.makeText(context, "Recipient copied", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        if (!transaction.phoneNumber.isNullOrEmpty()) {
                            DetailDivider()
                            DetailRow(
                                label = stringResource(R.string.detail_label_phone_number),
                                value = transaction.phoneNumber,
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(transaction.phoneNumber))
                                    Toast.makeText(context, "Phone number copied", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        if (!transaction.upiId.isNullOrEmpty()) {
                            DetailDivider()
                            DetailRow(
                                label = stringResource(R.string.label_upi_id),
                                value = transaction.upiId,
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(transaction.upiId))
                                    Toast.makeText(context, "UPI ID copied", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        DetailDivider()

                        DetailRow(
                            label = stringResource(R.string.label_date_time),
                            value = formatFullDate(transaction.timestamp),
                            onCopy = {
                                clipboardManager.setText(
                                    AnnotatedString(formatFullDate(transaction.timestamp))
                                )
                                Toast.makeText(context, "Date & time copied", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                if (transaction.smsExcerpt.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.detail_bank_confirmation),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = transaction.smsExcerpt,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                if (onDelete != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ProximityStatusError.copy(alpha = 0.1f))
                            .clickable { showDeleteConfirm = true }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.detail_delete_transaction),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ProximityStatusError
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = {
                Text(
                    stringResource(R.string.detail_delete_confirm_title),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    "This removes the record from your history on this device. " +
                        "It cannot be undone and does not affect the actual payment.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text(
                        stringResource(R.string.action_delete),
                        color = ProximityStatusError,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(
                        stringResource(R.string.action_cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = onCopy,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DetailDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 2.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    )
}

/** Localised plain-language meaning of a non-success lifecycle status. */
@androidx.compose.runtime.Composable
private fun statusExplainerText(status: String): String? = when (status.uppercase()) {
    "USER_ATTESTED", "USER_CONFIRMED" -> androidx.compose.ui.res.stringResource(com.proximity.app.R.string.status_explainer_user_confirmed)
    "PENDING" -> androidx.compose.ui.res.stringResource(com.proximity.app.R.string.status_explainer_pending)
    "UNVERIFIED" -> androidx.compose.ui.res.stringResource(com.proximity.app.R.string.status_explainer_unverified)
    "NEEDS_REVIEW" -> androidx.compose.ui.res.stringResource(com.proximity.app.R.string.status_explainer_needs_review)
    "CANCELLED" -> androidx.compose.ui.res.stringResource(com.proximity.app.R.string.status_explainer_cancelled)
    "FAILED" -> androidx.compose.ui.res.stringResource(com.proximity.app.R.string.status_explainer_failed)
    else -> null
}

private fun formatFullDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd MMMM yyyy, hh:mm a", Locale("en", "IN"))
    return formatter.format(Date(timestamp))
}
