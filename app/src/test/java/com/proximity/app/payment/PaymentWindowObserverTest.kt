// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.payment

import com.proximity.app.states.PaymentState
import com.proximity.app.states.TimeoutType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The SMS operation window must close when a payment is cancelled — left
 * armed, it lets a later unrelated debit be adopted onto the cancelled row
 * and reported as a success. User attestation is removed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PaymentWindowObserverTest {

    @Test
    fun `a cancelled payment closes the sms window`() {
        assertTrue(
            PaymentState.Cancelled(
                phoneNumber = "9876543210",
                amount = "500",
                transactionId = "txn-1",
                reason = "Cancelled by user"
            ).shouldDisarmSmsWindow()
        )
    }

    @Test
    fun `a timeout leaves the window open for its remaining grace`() {
        assertFalse(
            PaymentState.Timeout(
                timeoutType = TimeoutType.BANK_VERIFICATION,
                phoneNumber = "9876543210",
                amount = "500",
                transactionId = "txn-1"
            ).shouldDisarmSmsWindow()
        )
    }

    @Test
    fun `settled and in-progress states never close the window`() {
        val states = listOf(
            PaymentState.Idle,
            PaymentState.Initiating(
                phoneNumber = "9876543210",
                amount = "500",
                transactionId = "txn-1"
            ),
            PaymentState.InProgress(
                step = "On the line with the UPI service",
                progress = 0.5f,
                phoneNumber = "9876543210",
                amount = "500",
                transactionId = "txn-1"
            ),
            PaymentState.Success(
                transactionId = "txn-1",
                phoneNumber = "9876543210",
                amount = "500"
            ),
            PaymentState.Failed(
                error = "declined",
                phoneNumber = "9876543210",
                amount = "500",
                transactionId = "txn-1"
            )
        )
        for (state in states) {
            assertFalse(
                "$state must not close the SMS window",
                state.shouldDisarmSmsWindow()
            )
        }
    }

    @Test
    fun `unverified state never launches user attestation activity`() = runTest {
        val app = RuntimeEnvironment.getApplication()
        val shadowApp = shadowOf(app)
        while (shadowApp.nextStartedActivity != null) {}

        val flow = MutableStateFlow<PaymentState>(PaymentState.Idle)
        val observer = PaymentWindowObserver(
            appContext = app,
            paymentState = flow,
            scope = backgroundScope,
            onDisarm = {}
        )
        observer.start()
        runCurrent()

        flow.value = PaymentState.Unverified(
            transactionId = "txn-1",
            phoneNumber = "9876543210",
            amount = "500"
        )
        runCurrent()

        assertNull("No attestation activity should ever be started", shadowApp.nextStartedActivity)
    }

    @Test
    fun `observer calls onDisarm when payment is cancelled`() = runTest {
        val app = RuntimeEnvironment.getApplication()
        var disarmed = false

        val flow = MutableStateFlow<PaymentState>(PaymentState.Idle)
        val observer = PaymentWindowObserver(
            appContext = app,
            paymentState = flow,
            scope = backgroundScope,
            onDisarm = { disarmed = true }
        )
        observer.start()
        runCurrent()

        flow.value = PaymentState.Cancelled(
            phoneNumber = "9876543210",
            amount = "500",
            transactionId = "txn-1"
        )
        runCurrent()

        assertTrue("onDisarm callback must be invoked on Cancelled state", disarmed)
    }
}
