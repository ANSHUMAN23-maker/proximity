// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.payment

import android.content.Context
import android.util.Log
import com.proximity.app.helpers.TransactionDetector
import com.proximity.app.states.PaymentState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Observes [paymentState] to disarm SMS detection window when a payment is cancelled.
 * User attestation is removed per product specification.
 */
class PaymentWindowObserver(
    private val appContext: Context,
    private val paymentState: StateFlow<PaymentState>,
    private val scope: CoroutineScope,
    private val onDisarm: () -> Unit = { defaultDisarm(appContext) }
) {
    fun start() {
        scope.launch {
            paymentState.collect { state ->
                if (state.shouldDisarmSmsWindow()) {
                    onDisarm()
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
