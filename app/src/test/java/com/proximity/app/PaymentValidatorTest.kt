// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app

import com.proximity.app.payment.Upi123DialStringBuilder
import com.proximity.app.payment.Upi123InputValidator
import com.proximity.app.telephony.CallAccountRegistrar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentValidatorTest {

    @Test
    fun `validate accepts 10-digit Indian phone and whole rupees 1 to 4999`() {
        val res = Upi123InputValidator.validate("9876543210", "500")
        assertTrue(res is Upi123InputValidator.ValidationResult.Valid)
        val valid = res as Upi123InputValidator.ValidationResult.Valid
        assertEquals("9876543210", valid.cleanPhone)
        assertEquals(500, valid.wholeRupees)
        assertEquals("tel:08045163666,,1,9876543210,,500,,1", valid.dtmfUriString)
        assertEquals(valid.dtmfUriString, Upi123DialStringBuilder.buildUri("9876543210", 500))
    }

    @Test
    fun `validate rejects invalid phone numbers`() {
        assertFalse(Upi123InputValidator.isValidIndianMobile("0123456789"))
        assertEquals(
            Upi123InputValidator.ValidationResult.Invalid(Upi123InputValidator.ValidationReason.INVALID_PHONE),
            Upi123InputValidator.validate("0123456789", "100")
        )
    }

    @Test
    fun `validate rejects decimals, below minimum, and above 4999 cap`() {
        assertEquals(
            Upi123InputValidator.ValidationResult.Invalid(Upi123InputValidator.ValidationReason.DECIMALS_NOT_ALLOWED),
            Upi123InputValidator.validate("9876543210", "250.75")
        )
        assertEquals(
            Upi123InputValidator.ValidationResult.Invalid(Upi123InputValidator.ValidationReason.BELOW_MINIMUM),
            Upi123InputValidator.validate("9876543210", "0")
        )
        assertEquals(
            Upi123InputValidator.ValidationResult.Invalid(Upi123InputValidator.ValidationReason.ABOVE_MAX_CAP),
            Upi123InputValidator.validate("9876543210", "5000")
        )
    }

    @Test
    fun `validate normalizes formatted phone and currency inputs`() {
        val res = Upi123InputValidator.validate("+91 98765 43210", " \u20B91,250 ")
        assertTrue(res is Upi123InputValidator.ValidationResult.Valid)
        val valid = res as Upi123InputValidator.ValidationResult.Valid
        assertEquals("9876543210", valid.cleanPhone)
        assertEquals(1250, valid.wholeRupees)
        assertEquals("tel:08045163666,,1,9876543210,,1250,,1", valid.dtmfUriString)
        assertTrue(CallAccountRegistrar.isDialableTelUri(valid.dtmfUriString))
    }

    @Test
    fun `isDialableTelUri rejects blank or non-tel strings`() {
        assertFalse(CallAccountRegistrar.isDialableTelUri(""))
        assertFalse(CallAccountRegistrar.isDialableTelUri("tel:"))
        assertFalse(CallAccountRegistrar.isDialableTelUri("https://example.com"))
    }
}
