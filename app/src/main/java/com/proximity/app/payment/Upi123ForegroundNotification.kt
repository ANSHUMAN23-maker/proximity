// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.payment

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.proximity.app.R
import com.proximity.app.ui.payment.PaymentProgressScreen

/**
 * Posts a full-screen-intent notification when UPI123 payment processing starts.
 * This brings the user back to the payment screen even if the native dialer is in the
 * foreground, without requiring SYSTEM_ALERT_WINDOW (which Android GO devices often block).
 */
object Upi123ForegroundNotification {
    private const val CHANNEL_ID = "proximity_upi123_processing"
    private const val NOTIFICATION_ID = 1001

    fun show(context: Context, phone10: String, amountRupees: String) {
        try {
            ensureChannel(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val intent = Intent(context, PaymentProgressScreen::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(PaymentProgressScreen.EXTRA_PHONE_10, phone10)
                putExtra(PaymentProgressScreen.EXTRA_AMOUNT_RUPEES, amountRupees)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_phone_call)
                .setContentTitle(context.getString(R.string.upi123_notification_title))
                .setContentText(
                    context.getString(
                        R.string.upi123_notification_body,
                        phone10,
                        amountRupees
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(false)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .build()

            nm.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Full-screen intent may be denied by the user; heads-up still shows.
        }
    }

    fun cancel(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            nm.cancel(NOTIFICATION_ID)
        } catch (_: SecurityException) {
        }
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.upi123_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.upi123_notification_channel_desc)
            enableVibration(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }
}
