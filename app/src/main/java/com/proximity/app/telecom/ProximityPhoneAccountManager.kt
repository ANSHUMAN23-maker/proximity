package com.proximity.app.telecom

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.net.Uri
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import com.proximity.app.R

class ProximityPhoneAccountManager(private val context: Context) {

    companion object {
        private const val TAG = "ProximityPhoneAccountManager"
        private const val ACCOUNT_ID = "proximity_upi123_account"
    }

    private val telecomManager: TelecomManager =
        context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

    val handle: PhoneAccountHandle = PhoneAccountHandle(
        ComponentName(context, ProximityConnectionService::class.java),
        ACCOUNT_ID
    )

    fun registerIfNeeded() {
        try {
            val existing = telecomManager.getPhoneAccount(handle)
            if (existing != null && existing.isEnabled) {
                Log.d(TAG, "Phone account already registered and enabled")
                return
            }

            val account = PhoneAccount.builder(handle, context.getString(R.string.app_name))
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
                .setAddress(Uri.fromParts("tel", "", null))
                .build()

            telecomManager.registerPhoneAccount(account)
            Log.d(TAG, "Phone account registered: $ACCOUNT_ID")
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot register phone account: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register phone account", e)
        }
    }
}
