// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.receivers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.proximity.app.R
import com.proximity.app.data.PaymentRecord
import com.proximity.app.data.PaymentRecordStatus
import com.proximity.app.ui.payment.PaymentResultScreen
import com.proximity.app.utils.CurrencyFormat

object PaymentAlertPublisher {
    const val CHANNEL_ID = "proximity_payment_results"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_payment_results),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_payment_results_desc)
            enableVibration(true)
        }
        nm.createNotificationChannel(channel)
    }

    fun publishOutcomeNotification(context: Context, record: PaymentRecord) {
        try {
            ensureChannel(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val (titleRes, iconRes) = when (record.status) {
                PaymentRecordStatus.SUCCESS, PaymentRecordStatus.USER_CONFIRMED ->
                    R.string.payment_status_success to R.drawable.ic_check_circle
                PaymentRecordStatus.FAILED, PaymentRecordStatus.CANCELLED ->
                    R.string.payment_status_failed to R.drawable.ic_error
                PaymentRecordStatus.NEEDS_REVIEW ->
                    R.string.payment_status_needs_review to R.drawable.ic_unverified
                PaymentRecordStatus.UNVERIFIED, PaymentRecordStatus.PENDING ->
                    R.string.payment_status_unverified to R.drawable.ic_unverified
            }

            val formattedAmount = CurrencyFormat.formatWithSymbol(record.amountRupees)
            val contentText = "$formattedAmount \u2022 ${record.bankName}"

            val resultIntent = Intent(context, PaymentResultScreen::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(PaymentResultScreen.EXTRA_PAYMENT_RECORD, record)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                record.id.hashCode(),
                resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(iconRes)
                .setContentTitle(context.getString(titleRes))
                .setContentText(contentText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            nm.notify(record.id.hashCode(), notification)
        } catch (_: SecurityException) {
        }
    }
}
