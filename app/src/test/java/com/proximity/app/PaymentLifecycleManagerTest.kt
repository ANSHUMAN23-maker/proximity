@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.proximity.app

import com.proximity.app.data.PaymentChannel
import com.proximity.app.data.PaymentRecord
import com.proximity.app.data.PaymentRecordStatus
import com.proximity.app.data.PaymentRecordStore
import com.proximity.app.payment.PaymentLifecycleManager
import com.proximity.app.payment.PaymentState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentLifecycleManagerTest {

    private class FakePaymentRecordStore : PaymentRecordStore {
        private val recordsById = linkedMapOf<String, PaymentRecord>()
        private val recordsFlow = MutableStateFlow<List<PaymentRecord>>(emptyList())

        override fun observeRecords(): Flow<List<PaymentRecord>> = recordsFlow.asStateFlow()

        override suspend fun getById(id: String): PaymentRecord? = recordsById[id]

        override suspend fun getLatestPending(): PaymentRecord? =
            recordsById.values
                .filter { it.status == PaymentRecordStatus.PENDING }
                .maxByOrNull { it.timestampMillis }

        override suspend fun upsert(record: PaymentRecord) {
            recordsById[record.id] = record
            publish()
        }

        override suspend fun expireStalePending(nowMillis: Long) {
            var changed = false
            recordsById.entries.toList().forEach { (id, record) ->
                if (record.status == PaymentRecordStatus.PENDING &&
                    record.verificationDeadlineMillis <= nowMillis
                ) {
                    recordsById[id] = record.copy(status = PaymentRecordStatus.UNVERIFIED)
                    changed = true
                }
            }
            if (changed) publish()
        }

        override suspend fun deleteById(id: String) {
            if (recordsById.remove(id) != null) publish()
        }

        private fun publish() {
            recordsFlow.value = recordsById.values.sortedByDescending { it.timestampMillis }
        }
    }

    @Test
    fun `open amount QR session accepts bank debit SMS and sets detected amount`() = runTest {
        val store = FakePaymentRecordStore()
        var disabledUssdCount = 0
        val notified = mutableListOf<PaymentRecord>()
        val manager = PaymentLifecycleManager(
            recordStore = store,
            scope = backgroundScope,
            onDisableUssdAccessibility = { disabledUssdCount++ },
            onPostPaymentNotification = { notified += it }
        )

        val now = 1_000_000L
        val session = manager.startNewSession(
            channel = PaymentChannel.USSD_99,
            recipientName = "Local Store",
            phoneNumber = null,
            vpa = "store@sbi",
            amountRupees = "0.00",
            configuredBankName = "SBI",
            nowMillis = now
        )
        assertTrue(manager.isSmsWindowOpen(now + 100L))
        assertTrue(manager.isUssdPaymentInFlight())

        val matched = manager.ingestBankSms(
            sender = "AD-SBIUPI",
            body = "Acct XX123 debited by Rs 325.50 via UPI Ref 123456789012.",
            nowMillis = now + 500L
        )
        assertNotNull(matched)
        assertEquals(PaymentRecordStatus.SUCCESS, matched!!.status)
        assertEquals("325.50", matched.amountRupees)
        assertEquals("123456789012", matched.bankReferenceId)
        assertTrue(manager.state.value is PaymentState.Success)
        assertEquals(session.transactionId, (manager.state.value as PaymentState.Success).transaction.id)
        assertEquals(1, disabledUssdCount)
        assertEquals(1, notified.size)
        assertFalse(manager.isSmsWindowOpen(now + 1_000L))
        assertFalse(manager.isUssdPaymentInFlight())
    }

    @Test
    fun `amount mismatch in bank SMS marks transaction NEEDS_REVIEW`() = runTest {
        val store = FakePaymentRecordStore()
        val manager = PaymentLifecycleManager(
            recordStore = store,
            scope = backgroundScope
        )
        val now = 2_000_000L
        manager.startNewSession(
            channel = PaymentChannel.UPI_123PAY,
            recipientName = "Asha",
            phoneNumber = "9876543210",
            vpa = null,
            amountRupees = "250.00",
            configuredBankName = "HDFC Bank",
            nowMillis = now
        )

        val matched = manager.ingestBankSms(
            sender = "AD-HDFCBK",
            body = "Acct XX556 debited for Rs 500.00. UPI Ref No 631512345678.",
            nowMillis = now + 1_000L
        )
        assertNotNull(matched)
        assertEquals(PaymentRecordStatus.NEEDS_REVIEW, matched!!.status)
        assertTrue(manager.state.value is PaymentState.NeedsReview)
    }

    @Test
    fun `amount mismatch in USSD confirmation marks transaction NEEDS_REVIEW`() = runTest {
        val store = FakePaymentRecordStore()
        val manager = PaymentLifecycleManager(
            recordStore = store,
            scope = backgroundScope
        )
        val now = 3_000_000L
        manager.startNewSession(
            channel = PaymentChannel.USSD_99,
            recipientName = "Vendor",
            phoneNumber = null,
            vpa = "vendor@upi",
            amountRupees = "100.00",
            configuredBankName = "ICICI Bank",
            nowMillis = now
        )

        val record = manager.ingestUssdAccessibilityEvent(
            listOf("Transaction Successful | Amount: Rs 200.00 | UPI Ref ID: 432198765432")
        )
        assertNotNull(record)
        assertEquals(PaymentRecordStatus.NEEDS_REVIEW, record!!.status)
        assertTrue(manager.state.value is PaymentState.NeedsReview)
    }

    @Test
    fun `hydratePendingSessionIfNeeded restores unexpired session and expires stale one`() = runTest {
        val store = FakePaymentRecordStore()
        val now = 10_000_000L
        store.upsert(
            PaymentRecord(
                id = "txn-fresh",
                recipientName = "Shop",
                phoneNumber = null,
                vpa = "shop@okaxis",
                amountRupees = "150.00",
                bankName = "Axis Bank",
                bankReferenceId = null,
                status = PaymentRecordStatus.PENDING,
                channel = PaymentChannel.USSD_99,
                timestampMillis = now - 5_000L,
                verificationDeadlineMillis = now + 60_000L,
                bankConfirmationExcerpt = null
            )
        )
        val manager = PaymentLifecycleManager(store, backgroundScope)
        manager.hydratePendingSessionIfNeeded(nowMillis = now)

        val hydratedState = manager.state.value
        assertTrue(hydratedState is PaymentState.WaitingForVerification)
        assertEquals("txn-fresh", (hydratedState as PaymentState.WaitingForVerification).session.transactionId)
        assertTrue(manager.isSmsWindowOpen(now + 1_000L))

        val staleStore = FakePaymentRecordStore()
        staleStore.upsert(
            PaymentRecord(
                id = "txn-stale",
                recipientName = "Old",
                phoneNumber = null,
                vpa = "old@upi",
                amountRupees = "75.00",
                bankName = "SBI",
                bankReferenceId = null,
                status = PaymentRecordStatus.PENDING,
                channel = PaymentChannel.UPI_123PAY,
                timestampMillis = now - 300_000L,
                verificationDeadlineMillis = now - 1_000L,
                bankConfirmationExcerpt = null
            )
        )
        val staleManager = PaymentLifecycleManager(staleStore, backgroundScope)
        staleManager.hydratePendingSessionIfNeeded(nowMillis = now)
        assertEquals(PaymentState.Idle, staleManager.state.value)
        assertEquals(PaymentRecordStatus.UNVERIFIED, staleStore.getById("txn-stale")?.status)
    }

    @Test
    fun `qr grace timeout transitions WaitingForVerification to Unverified and disarms window`() = runTest {
        val store = FakePaymentRecordStore()
        val manager = PaymentLifecycleManager(store, backgroundScope)
        val now = 5_000_000L
        val session = manager.startNewSession(
            channel = PaymentChannel.USSD_99,
            recipientName = "Kirana",
            phoneNumber = null,
            vpa = "kirana@upi",
            amountRupees = "40.00",
            configuredBankName = "SBI",
            nowMillis = now
        )

        manager.transitionToWaitingForVerification(startQrGraceTimer = true)
        assertTrue(manager.state.value is PaymentState.WaitingForVerification)

        advanceTimeBy(PaymentLifecycleManager.QR_RETURN_GRACE_MS + 100L)
        runCurrent()

        val state = manager.state.value
        assertTrue(state is PaymentState.Unverified)
        assertEquals(session.transactionId, (state as PaymentState.Unverified).transaction.id)
        assertEquals(PaymentRecordStatus.UNVERIFIED, store.getById(session.transactionId)?.status)
        assertFalse(manager.isUssdPaymentInFlight())
    }

    @Test
    fun `onUpi123CallEnded cancels short calls and waits for longer calls`() = runTest {
        val store = FakePaymentRecordStore()
        val manager = PaymentLifecycleManager(store, backgroundScope)

        val shortSession = manager.startNewSession(
            channel = PaymentChannel.UPI_123PAY,
            recipientName = "Short",
            phoneNumber = "9999988888",
            vpa = null,
            amountRupees = "50.00",
            configuredBankName = "SBI"
        )
        manager.markCallOrUssdInProgress()
        manager.onUpi123CallEnded(PaymentLifecycleManager.MIN_UPI123_CALL_DURATION_MS - 1_000L)
        assertTrue(manager.state.value is PaymentState.Cancelled)
        assertEquals(PaymentRecordStatus.CANCELLED, store.getById(shortSession.transactionId)?.status)

        val longSession = manager.startNewSession(
            channel = PaymentChannel.UPI_123PAY,
            recipientName = "Long",
            phoneNumber = "9999988888",
            vpa = null,
            amountRupees = "60.00",
            configuredBankName = "SBI"
        )
        manager.markCallOrUssdInProgress()
        manager.onUpi123CallEnded(PaymentLifecycleManager.MIN_UPI123_CALL_DURATION_MS + 1_000L)
        val state = manager.state.value
        assertTrue(state is PaymentState.WaitingForVerification)
        assertEquals(longSession.transactionId, (state as PaymentState.WaitingForVerification).session.transactionId)
    }
}
