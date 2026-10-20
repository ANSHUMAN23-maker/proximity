package com.proximity.app.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UssdTransactionParserTest {

    @Test
    fun testParseSuccessfulUssdWithRefNoAndAmount() {
        val ussdText = "Your payment of Rs. 250.00 to merchant@upi (Ref No 412345678901) was successful."
        val result = UssdTransactionParser.parse(ussdText)

        assertTrue(result.isSuccess)
        assertEquals("250.00", result.amount)
        assertEquals("merchant@upi", result.recipient)
        assertEquals("412345678901", result.refId)
    }

    @Test
    fun testParseSuccessfulUssdRupeeSymbol() {
        val ussdText = "₹500 paid successfully to 9876543210@upi. Txn ID: 987654321"
        val result = UssdTransactionParser.parse(ussdText)

        assertTrue(result.isSuccess)
        assertEquals("500", result.amount)
        assertEquals("9876543210@upi", result.recipient)
        assertEquals("987654321", result.refId)
    }

    @Test
    fun testParseFailedUssdMessage() {
        val ussdText = "Transaction failed due to incorrect UPI PIN. Please try again."
        val result = UssdTransactionParser.parse(ussdText)

        assertFalse(result.isSuccess)
        assertNull(result.amount)
        assertNull(result.recipient)
        assertNull(result.refId)
    }

    @Test
    fun testParseNullAndEmptyString() {
        val resultNull = UssdTransactionParser.parse(null)
        assertFalse(resultNull.isSuccess)

        val resultEmpty = UssdTransactionParser.parse("   ")
        assertFalse(resultEmpty.isSuccess)
    }
}
