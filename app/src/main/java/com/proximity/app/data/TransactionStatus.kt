package com.proximity.app.data

object TransactionStatus {
    const val PENDING = "PENDING"
    const val SUCCESS = "SUCCESS"
    const val FAILED = "FAILED"
    const val CANCELLED = "CANCELLED"
    const val UNVERIFIED = "UNVERIFIED"
    const val USER_ATTESTED = "USER_ATTESTED"
    const val USER_CONFIRMED = "USER_CONFIRMED"
    const val NEEDS_REVIEW = "NEEDS_REVIEW"
}

object TransactionSource {
    const val SMS = "SMS"
    const val NOTIFICATION = "NOTIFICATION"
    const val MANUAL = "MANUAL"
    const val QR = "QR"
}
