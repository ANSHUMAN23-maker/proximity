// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.ui.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import com.proximity.app.AppCore
import com.proximity.app.R
import com.proximity.app.data.PaymentRecord
import com.proximity.app.data.PaymentRecordStatus
import com.proximity.app.theme.AppTheme
import com.proximity.app.theme.AppVisualTokens
import com.proximity.app.theme.ColorTokens
import com.proximity.app.theme.LocalAppVisuals
import com.proximity.app.ui.components.AnimatedBackground
import com.proximity.app.ui.components.BrandedHeaderLockup
import com.proximity.app.ui.components.CurrencyDisplay
import com.proximity.app.ui.components.ErrorCrossGraphic
import com.proximity.app.ui.components.GlassCard
import com.proximity.app.ui.components.pressScale
import com.proximity.app.utils.CurrencyFormat
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

class TransactionHistoryScreen : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val app = application as AppCore

        setContent {
            val settings by app.services.preferencesStore.settingsFlow.collectAsState()
            AppTheme(settings = settings) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AnimatedBackground(modifier = Modifier.fillMaxSize())
                    TransactionHistoryContent()
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionHistoryContent() {
    val context = LocalContext.current
    val visuals = LocalAppVisuals.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as AppCore

    var records by remember { mutableStateOf<List<PaymentRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var reloadTrigger by remember { mutableIntStateOf(0) }

    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedRecord by remember { mutableStateOf<PaymentRecord?>(null) }

    BackHandler(enabled = searchExpanded) {
        searchExpanded = false
        searchQuery = ""
    }

    LaunchedEffect(reloadTrigger) {
        isLoading = true
        hasError = false
        app.services.paymentRecordStore.observeRecords()
            .catch {
                hasError = true
                isLoading = false
            }
            .collect { list ->
                records = list
                isLoading = false
            }
    }

    val filteredList = remember(records, searchQuery) {
        if (searchQuery.isBlank()) {
            records
        } else {
            val q = searchQuery.trim()
            records.filter { rec ->
                (rec.recipientName?.contains(q, ignoreCase = true) == true) ||
                    (rec.phoneNumber?.contains(q, ignoreCase = true) == true) ||
                    (rec.vpa?.contains(q, ignoreCase = true) == true) ||
                    rec.bankName.contains(q, ignoreCase = true) ||
                    rec.amountRupees.contains(q, ignoreCase = true)
            }
        }
    }

    val groupedByDate = remember(filteredList) {
        groupRecordsByDateLabel(context, filteredList)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(ColorTokens.BrandTeal)
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrandedHeaderLockup(compact = true, textColor = Color.White)

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                        .semantics { role = Role.Button }
                        .pressScale {
                            searchExpanded = !searchExpanded
                            if (!searchExpanded) searchQuery = ""
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_search),
                        contentDescription = stringResource(id = R.string.cd_search),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        AnimatedVisibility(visible = searchExpanded) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = stringResource(id = R.string.history_search_placeholder),
                            color = visuals.textSecondary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_search),
                            contentDescription = stringResource(id = R.string.cd_search),
                            tint = visuals.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_close),
                                    contentDescription = stringResource(id = R.string.cd_close),
                                    tint = visuals.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = ColorTokens.BrandTeal)
                }
            }

            hasError -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    ErrorCrossGraphic(sizeDp = 88.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(id = R.string.history_error_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = visuals.textPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { reloadTrigger++ },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorTokens.BrandTeal)
                    ) {
                        Text(text = stringResource(id = R.string.action_retry), color = Color.White)
                    }
                }
            }

            filteredList.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(ColorTokens.BrandTeal.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_history),
                            contentDescription = null,
                            tint = ColorTokens.BrandTeal,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (searchQuery.isBlank()) {
                            stringResource(id = R.string.history_empty_title)
                        } else {
                            stringResource(id = R.string.history_empty_search_title)
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = visuals.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(id = R.string.history_empty_subtitle),
                        fontSize = 13.sp,
                        color = visuals.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            else -> {
                val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = navBottomPadding + 104.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    groupedByDate.forEach { (dateHeader, itemsInGroup) ->
                        stickyHeader(key = "header_$dateHeader") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(visuals.mainBackground.copy(alpha = 0.94f))
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = dateHeader,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = visuals.adaptiveTealLabel,
                                    modifier = Modifier.semantics { heading() }
                                )
                            }
                        }

                        items(itemsInGroup, key = { it.id }) { rec ->
                            HistoryRecordRow(
                                record = rec,
                                onClick = { selectedRecord = rec }
                            )
                        }
                    }
                }
            }
        }
    }

    val modalRec = selectedRecord
    if (modalRec != null) {
        RecordDetailModal(
            record = modalRec,
            onDismiss = { selectedRecord = null },
            onDeleteConfirmed = {
                scope.launch {
                    app.services.paymentRecordStore.deleteById(modalRec.id)
                    selectedRecord = null
                }
            }
        )
    }
}

@Composable
private fun HistoryRecordRow(
    record: PaymentRecord,
    onClick: () -> Unit
) {
    val visuals = LocalAppVisuals.current
    val primaryLabel = record.recipientName?.takeIf { it.isNotBlank() }
        ?: record.vpa?.takeIf { it.isNotBlank() }
        ?: record.phoneNumber?.let { CurrencyFormat.formatPhoneForDisplay(it) }
        ?: record.bankName

    val initialChar = primaryLabel.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "P"
    val timeFormatted = remember(record.timestampMillis) {
        SimpleDateFormat("hh:mm a", Locale.US).format(Date(record.timestampMillis))
    }

    val (statusLabelRes, statusColor) = statusVisualsFor(record.status, visuals)

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { role = Role.Button }
            .pressScale(onClick = onClick),
        cornerRadius = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(ColorTokens.BrandTeal.copy(alpha = 0.16f))
                    .border(1.dp, ColorTokens.BrandTeal.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initialChar,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorTokens.BrandTeal
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = primaryLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = visuals.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = timeFormatted,
                    fontSize = 12.sp,
                    color = visuals.textSecondary
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(horizontalAlignment = Alignment.End) {
                CurrencyDisplay(
                    amountStr = record.amountRupees,
                    digitFontSize = 16.sp,
                    color = visuals.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(id = statusLabelRes),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordDetailModal(
    record: PaymentRecord,
    onDismiss: () -> Unit,
    onDeleteConfirmed: () -> Unit
) {
    val visuals = LocalAppVisuals.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val (statusLabelRes, statusColor) = statusVisualsFor(record.status, visuals)
    val explainerRes = statusExplainerResFor(record.status)
    val fullDateTime = remember(record.timestampMillis) {
        SimpleDateFormat("dd MMMM yyyy, hh:mm a", Locale.US).format(Date(record.timestampMillis))
    }

    Dialog(onDismissRequest = onDismiss) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            elevatedOverlay = true
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(visuals.outline.copy(alpha = 0.5f))
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.detail_title),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = visuals.textPrimary
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(visuals.inputSurface)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = stringResource(id = R.string.cd_close),
                            tint = visuals.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                CurrencyDisplay(
                    amountStr = record.amountRupees,
                    digitFontSize = 32.sp,
                    color = visuals.adaptiveTealLabel
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.18f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(id = statusLabelRes),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }

                if (explainerRes != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(id = explainerRes),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = visuals.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = visuals.outline.copy(alpha = 0.25f))
                Spacer(modifier = Modifier.height(12.dp))

                DetailCopyableItem(
                    label = stringResource(id = R.string.detail_label_bank_ref),
                    value = record.bankReferenceId ?: record.id
                )
                DetailCopyableItem(
                    label = stringResource(id = R.string.detail_label_bank),
                    value = record.bankName
                )
                if (!record.recipientName.isNullOrBlank()) {
                    DetailCopyableItem(
                        label = stringResource(id = R.string.detail_label_recipient),
                        value = record.recipientName
                    )
                }
                if (!record.phoneNumber.isNullOrBlank()) {
                    DetailCopyableItem(
                        label = stringResource(id = R.string.detail_label_phone_number),
                        value = CurrencyFormat.formatPhoneForDisplay(record.phoneNumber)
                    )
                }
                if (!record.vpa.isNullOrBlank()) {
                    DetailCopyableItem(
                        label = stringResource(id = R.string.detail_label_upi_id),
                        value = record.vpa
                    )
                }
                DetailCopyableItem(
                    label = stringResource(id = R.string.detail_label_date_time),
                    value = fullDateTime
                )

                if (!record.bankConfirmationExcerpt.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(visuals.inputSurface)
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = stringResource(id = R.string.detail_bank_confirmation),
                                fontSize = 11.sp,
                                color = visuals.textSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = record.bankConfirmationExcerpt,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                color = visuals.adaptiveTealLabel
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_trash_bin),
                        contentDescription = null,
                        tint = visuals.statusErrorText,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.detail_delete_transaction),
                        color = visuals.statusErrorText,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = visuals.highSurface,
            title = {
                Text(
                    text = stringResource(id = R.string.detail_delete_confirm_title),
                    fontWeight = FontWeight.Bold,
                    color = visuals.textPrimary
                )
            },
            text = {
                Text(
                    text = stringResource(id = R.string.detail_delete_confirm_body),
                    color = visuals.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteConfirmed()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorTokens.StatusErrorRed)
                ) {
                    Text(text = stringResource(id = R.string.action_delete), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(text = stringResource(id = R.string.action_cancel), color = visuals.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun DetailCopyableItem(label: String, value: String) {
    val context = LocalContext.current
    val visuals = LocalAppVisuals.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = visuals.textSecondary
            )
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = visuals.textPrimary
            )
        }
        IconButton(
            onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText(label, value))
                Toast.makeText(context, R.string.toast_copied_to_clipboard, Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_copy),
                contentDescription = stringResource(id = R.string.cd_copy),
                tint = visuals.adaptiveTealLabel,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun statusVisualsFor(
    status: PaymentRecordStatus,
    visuals: AppVisualTokens
): Pair<Int, Color> = when (status) {
    PaymentRecordStatus.SUCCESS -> R.string.status_label_success to visuals.statusSuccessText
    PaymentRecordStatus.USER_CONFIRMED -> R.string.status_label_user_confirmed to visuals.statusSuccessText
    PaymentRecordStatus.PENDING -> R.string.status_label_pending to visuals.statusPendingText
    PaymentRecordStatus.UNVERIFIED -> R.string.status_label_unverified to visuals.statusPendingText
    PaymentRecordStatus.NEEDS_REVIEW -> R.string.status_label_needs_review to visuals.statusPendingText
    PaymentRecordStatus.CANCELLED -> R.string.status_label_cancelled to visuals.statusErrorText
    PaymentRecordStatus.FAILED -> R.string.status_label_failed to visuals.statusErrorText
}

private fun statusExplainerResFor(status: PaymentRecordStatus): Int? = when (status) {
    PaymentRecordStatus.SUCCESS -> null
    PaymentRecordStatus.USER_CONFIRMED -> R.string.status_explainer_user_confirmed
    PaymentRecordStatus.PENDING -> R.string.status_explainer_pending
    PaymentRecordStatus.UNVERIFIED -> R.string.status_explainer_unverified
    PaymentRecordStatus.NEEDS_REVIEW -> R.string.status_explainer_needs_review
    PaymentRecordStatus.CANCELLED -> R.string.status_explainer_cancelled
    PaymentRecordStatus.FAILED -> R.string.status_explainer_failed
}

private fun groupRecordsByDateLabel(
    context: Context,
    list: List<PaymentRecord>
): Map<String, List<PaymentRecord>> {
    val result = LinkedHashMap<String, MutableList<PaymentRecord>>()
    val zoneId = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zoneId).toLocalDate()
    val dateFormatter = SimpleDateFormat("dd MMM", Locale.US)

    for (rec in list) {
        val recDate = Instant.ofEpochMilli(rec.timestampMillis).atZone(zoneId).toLocalDate()
        val dayDiff = ChronoUnit.DAYS.between(recDate, today)

        val label = when {
            dayDiff == 0L -> context.getString(R.string.history_group_today)
            dayDiff == 1L -> context.getString(R.string.history_group_yesterday)
            dayDiff in 2L..6L -> context.getString(R.string.history_group_this_week)
            else -> dateFormatter.format(Date(rec.timestampMillis))
        }
        result.getOrPut(label) { mutableListOf() }.add(rec)
    }
    return result
}
