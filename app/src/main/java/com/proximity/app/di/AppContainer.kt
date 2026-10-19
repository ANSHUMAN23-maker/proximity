// Copyright © 2026 Proximity. All rights reserved.
// Proprietary and confidential — No license granted.
// See LICENSE file for full terms.

package com.proximity.app.di

import android.content.Context
import com.proximity.app.data.SettingsRepository
import com.proximity.app.data.Transaction
import com.proximity.app.payment.PaymentSessionManager
import com.proximity.app.payment.PaymentTransactionStore
import com.proximity.app.payment.PaymentWindowObserver
import com.proximity.app.payment.sms.SimpleTransaction
import com.proximity.app.repository.TransactionRepository
import com.proximity.app.telecom.ProximityPhoneAccountManager
import com.proximity.app.telephony.CallStateCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    val walledGardenDetector: com.proximity.app.network.WalledGardenDetector by lazy {
        com.proximity.app.network.WalledGardenDetector(appContext)
    }

    val phoneAccountManager: ProximityPhoneAccountManager by lazy {
        ProximityPhoneAccountManager(appContext)
    }
    val callStateCoordinator: CallStateCoordinator by lazy { CallStateCoordinator(appContext) }

    val paymentSessionManager: PaymentSessionManager by lazy {
        PaymentSessionManager(
            store = LazyTransactionStore(appContext),
            coordinator = callStateCoordinator,
            scope = appScope
        )
    }

    val paymentWindowObserver: PaymentWindowObserver by lazy {
        PaymentWindowObserver(
            appContext = appContext,
            paymentState = paymentSessionManager.paymentState,
            scope = appScope
        )
    }
}

private class LazyTransactionStore(private val appContext: Context) : PaymentTransactionStore {

    private suspend fun repo(): TransactionRepository =
        withContext(Dispatchers.IO) { TransactionRepository.getInstance(appContext) }

    override suspend fun insertPending(transaction: Transaction) =
        repo().insertPending(transaction)

    override suspend fun transitionStatus(
        transactionId: String,
        expectedStatus: String,
        newStatus: String
    ): Int = repo().transitionStatus(transactionId, expectedStatus, newStatus)

    override suspend fun confirmTransaction(
        transactionId: String,
        status: String,
        parsed: SimpleTransaction,
        verifiedAt: Long
    ): Int = repo().confirmTransaction(
        transactionId = transactionId,
        status = status,
        parsed = parsed,
        verifiedAt = verifiedAt
    )

    override suspend fun deleteStalePending(now: Long): Int = repo().deleteStalePending(now)

    override suspend fun deletePending(transactionId: String): Int =
        repo().deletePending(transactionId)
}
