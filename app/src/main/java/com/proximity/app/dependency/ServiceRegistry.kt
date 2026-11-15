// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.dependency

import android.content.Context
import com.proximity.app.accessibility.UssdDialogReaderService
import com.proximity.app.data.LocalVaultDatabase
import com.proximity.app.data.RoomPaymentRecordStore
import com.proximity.app.data.UserPreferencesStore
import com.proximity.app.payment.PaymentLifecycleManager
import com.proximity.app.receivers.PaymentAlertPublisher
import com.proximity.app.telephony.CallAccountRegistrar
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.random.Random

class ServiceRegistry(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + defaultDispatcher)

    val preferencesStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { UserPreferencesStore(appContext) }
    val paymentRecordStore = RoomPaymentRecordStore(appContext, ioDispatcher)

    val paymentLifecycleManager = PaymentLifecycleManager(
        recordStore = paymentRecordStore,
        scope = appScope,
        onDisableUssdAccessibility = { UssdDialogReaderService.disableService() },
        onPostPaymentNotification = { record ->
            PaymentAlertPublisher.publishOutcomeNotification(appContext, record)
        }
    )

    val processGreetingIndex: Int = Random.nextInt(9)

    fun initializeBackgroundServices() {
        appScope.launch(ioDispatcher) {
            runCatching {
                LocalVaultDatabase.getInstance(appContext, ioDispatcher)
                    .paymentRecordDao()
                    .expireStalePendingRecords(System.currentTimeMillis())
                paymentLifecycleManager.hydratePendingSessionIfNeeded()
            }
            PaymentAlertPublisher.ensureChannel(appContext)
            CallAccountRegistrar.registerPhoneAccountIfSupported(appContext)
        }
    }

    fun shutdown() {
        appScope.cancel()
    }
}
