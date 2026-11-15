// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.payment

import com.proximity.app.constants.AppLimits

class PaymentWindowMonitor {
    @Volatile
    private var windowOpenedAtMillis: Long = 0L

    @Volatile
    private var windowClosesAtMillis: Long = 0L

    @Synchronized
    fun armWindow(nowMillis: Long = System.currentTimeMillis()) {
        windowOpenedAtMillis = nowMillis
        windowClosesAtMillis = nowMillis + AppLimits.SMS_WINDOW_DURATION_MS
    }

    @Synchronized
    fun disarmWindow() {
        windowOpenedAtMillis = 0L
        windowClosesAtMillis = 0L
    }

    @Synchronized
    fun isWindowOpen(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return windowClosesAtMillis > 0L && nowMillis in windowOpenedAtMillis..windowClosesAtMillis
    }
}
