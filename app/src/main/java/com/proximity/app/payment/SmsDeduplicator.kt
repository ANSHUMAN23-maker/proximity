// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.payment

import com.proximity.app.constants.AppLimits
import java.util.concurrent.ConcurrentHashMap

class SmsDeduplicator(private val windowMillis: Long = AppLimits.SMS_DEDUP_WINDOW_MS) {
    private val seenSignatures = ConcurrentHashMap<String, Long>()

    @Synchronized
    fun tryClaim(sender: String?, body: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val iterator = seenSignatures.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (nowMillis - entry.value > windowMillis) {
                iterator.remove()
            }
        }
        val key = "${sender?.trim()?.lowercase().orEmpty()}|${body.trim().lowercase()}"
        val previous = seenSignatures[key]
        if (previous != null && nowMillis - previous <= windowMillis) {
            return false
        }
        seenSignatures[key] = nowMillis
        return true
    }

    @Synchronized
    fun clear() {
        seenSignatures.clear()
    }
}
