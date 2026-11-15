// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.payment

object Upi123DialStringBuilder {
    fun buildUri(phone10Digits: String, wholeRupees: Int, isJio: Boolean = false): String =
        Upi123InputValidator.buildDtmfUriString(phone10Digits, wholeRupees, isJio = isJio)
}
