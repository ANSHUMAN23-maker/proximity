package com.proximity.app.constants

object AppConstants {

    const val USSD_SESSION_TIMEOUT = 30000L

    const val MIN_AMOUNT_VALUE = 1.0
    const val MAX_AMOUNT_VALUE = 100000.0

    const val DEFAULT_UPI_SERVICE_NUMBER = "08045163666"
    const val UPI123PAY_MAX_AMOUNT = 4999.0

    const val PREFS_NAME = "ProximityPrefs"
    const val KEY_SETUP_COMPLETED = "setup_completed"
    const val KEY_TEST_COMPLETED = "test_configuration_completed"
    const val KEY_SELECTED_BANK = "selected_bank"

    const val KEY_NOTIFICATIONS_ASKED = "notifications_permission_asked"

    const val PHONE_NUMBER_PATTERN = "^[1-9][0-9]{9}$"
}
