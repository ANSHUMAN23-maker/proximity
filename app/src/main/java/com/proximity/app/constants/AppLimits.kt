// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.constants

object AppLimits {
    const val UPI123_IVR_NUMBER = "08045163666"
    const val UPI123_JIO_IVR_NUMBER = "08045163581"

    const val VERIFICATION_DEADLINE_MS = 10 * 60 * 1000L // 10 minutes DB deadline
    const val SMS_WINDOW_DURATION_MS = 10 * 60 * 1000L + 30_000L // 10m 30s SMS window
    const val QR_RETURN_GRACE_MS = 5_000L // 5s grace timer after dialer return
    const val MIN_UPI123_CALL_DURATION_MS = 5_000L // 5s minimum call duration
    const val SMS_DEDUP_WINDOW_MS = 60_000L // 60s SMS deduplication window
    const val QR_SCAN_COOLDOWN_MS = 2_000L // 2s duplicate scan cooldown
}
