package com.proximity.app.receivers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.proximity.app.R
import com.proximity.app.data.TransactionStatus
import com.proximity.app.utils.CurrencyFormat

object PaymentResultNotifier {

    private const val TAG = "PaymentResultNotifier"
    private const val CHANNEL_ID = "payment_results"

    fun notifyResult(context: Context, resultIntent: Intent) {
        try {
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.notif_channel_payment_results),
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = context.getString(R.string.notif_channel_payment_results_desc)
                    }
                )
            }

            val status = resultIntent.getStringExtra("status") ?: ""
            val amount = resultIntent.getStringExtra("amount") ?: ""
            val bank = resultIntent.getStringExtra("bank_name") ?: ""

            val title = context.getString(statusTitleRes(status))
            val iconRes = statusIconRes(status)

            // Fixed UTF-8 corruption: Using clean Rupee symbol and standard em-dash
            val formattedAmount = "₹${CurrencyFormat.inr(amount)}"
            val text = listOf(formattedAmount, bank)
                .filter { it.isNotBlank() }
                .joinToString(" — ")

            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val contentIntent = PendingIntent.getActivity(
                context,
                (resultIntent.getStringExtra("transaction_id") ?: "").hashCode(),
                resultIntent,
                pendingIntentFlags
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(iconRes)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            manager.notify((resultIntent.getStringExtra("transaction_id") ?: "").hashCode(), notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not post payment result notification: ${e.message}")
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Could not post payment result notification: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error posting notification: ${e.message}", e)
        }
    }

    private fun statusTitleRes(status: String): Int = when (status.uppercase()) {
        TransactionStatus.FAILED -> R.string.payment_status_failed
        TransactionStatus.NEEDS_REVIEW -> R.string.payment_status_needs_review
        TransactionStatus.UNVERIFIED -> R.string.payment_status_unverified
        else -> R.string.payment_status_success
    }

    private fun statusIconRes(status: String): Int = when (status.uppercase()) {
        TransactionStatus.FAILED -> R.drawable.ic_error
        TransactionStatus.NEEDS_REVIEW, TransactionStatus.UNVERIFIED -> R.drawable.ic_unverified
        else -> R.drawable.ic_check_circle
    }
}
