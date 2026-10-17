package com.proximity.app.payment

import android.content.Context
import android.content.Intent
import android.util.Log
import com.proximity.app.helpers.TransactionDetector
import com.proximity.app.states.PaymentState
import com.proximity.app.ui.activities.UserAttestationActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PaymentWindowObserver(
    private val appContext: Context,
    private val paymentState: StateFlow<PaymentState>,
    private val scope: CoroutineScope,
    private val onDisarm: () -> Unit = { defaultDisarm(appContext) }
) {
    fun start() {
        scope.launch {
            paymentState.collect { state ->
                if (state.shouldDisarmSmsWindow()) onDisarm()

                if (state is PaymentState.Unverified) {
                    val intent = Intent(
                        appContext,
                        UserAttestationActivity::class.java
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("transaction_id", state.transactionId)
                        putExtra("amount", state.amount)
                        putExtra("upi_id", state.upiId)
                        putExtra("phone_number", state.phoneNumber)
                    }
                    appContext.startActivity(intent)
                }
            }
        }
    }

    companion object {
        private const val TAG = "PaymentWindowObserver"

        private fun defaultDisarm(context: Context) {
            TransactionDetector.getInstance(context).stopOperation()
            Log.d(TAG, "Payment cancelled - SMS operation window closed")
        }
    }
}

fun PaymentState.shouldDisarmSmsWindow(): Boolean = this is PaymentState.Cancelled
