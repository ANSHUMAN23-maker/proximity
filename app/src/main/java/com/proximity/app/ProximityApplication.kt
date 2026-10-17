// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app

import android.app.Application
import android.content.Context
import android.os.StrictMode
import android.util.Log
import com.proximity.app.data.SettingsRepository
import com.proximity.app.di.AppContainer
import com.proximity.app.payment.PaymentSessionManager
import com.proximity.app.telephony.CallStateCoordinator
import kotlinx.coroutines.CoroutineScope

class ProximityApplication : Application() {

    /** The manual composition root; see [AppContainer]. */
    val container: AppContainer by lazy { AppContainer(applicationContext) }

    // Delegating accessors so existing call sites
    // (ProximityApplication.from(context)?.paymentSessionManager, etc.) keep
    // working while construction lives in the container.
    val appScope: CoroutineScope get() = container.appScope
    val settingsRepository: SettingsRepository get() = container.settingsRepository
    val callStateCoordinator: CallStateCoordinator get() = container.callStateCoordinator
    val paymentSessionManager: PaymentSessionManager get() = container.paymentSessionManager

    companion object {
        private const val TAG = "ProximityApplication"

        /** Convenience accessor for receivers/services that only hold a Context. */
        fun from(context: Context): ProximityApplication? =
            context.applicationContext as? ProximityApplication
    }

    override fun onCreate() {
        super.onCreate()
        // Debug-only tripwire: the first database materialisation (SQLCipher
        // open, Keystore unwrap, upgrade migration) must never run on the
        // main thread â€” it once did, from this very method. penaltyLog keeps
        // debug builds usable while making any regression loudly visible.
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
        }
        // Discard any PENDING rows whose deadline passed while the app was
        // dead. The store resolves lazily on IO (see LazyTransactionStore),
        // so this no longer opens the database on the main thread.
        paymentSessionManager.reconcileStalePending()
        // Close the SMS operation window on cancellation from the process
        // scope, so it still happens after the overlay service has stopped.
        container.paymentWindowObserver.start()
        // Register as a phone account so TelecomManager.placeCall() routes
        // UPI123 calls through ProximityConnectionService, keeping the system
        // dialer from ever appearing during a payment.
        container.phoneAccountManager.registerIfNeeded()
        Log.d(TAG, "ProximityApplication initialized")
    }
}
