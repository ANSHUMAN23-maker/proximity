// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.telephony

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat

class CallRouterService : ConnectionService() {

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val conn = object : Connection() {
            override fun onAnswer() {
                setActive()
            }

            override fun onDisconnect() {
                setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
                destroy()
                if (activeConnection === this) activeConnection = null
            }

            override fun onAbort() {
                setDisconnected(DisconnectCause(DisconnectCause.CANCELED))
                destroy()
                if (activeConnection === this) activeConnection = null
            }
        }
        conn.setAddress(request?.address, TelecomManager.PRESENTATION_ALLOWED)
        conn.setDialing()
        activeConnection = conn
        return conn
    }

    companion object {
        @Volatile
        private var activeConnection: Connection? = null

        fun disconnectActiveConnection(): Boolean {
            val conn = activeConnection ?: return false
            return try {
                conn.onDisconnect()
                activeConnection = null
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}

enum class CallDialOutcome {
    TELECOM_SERVICE,
    CALL_INTENT_FALLBACK,
    FAILED
}

object CallAccountRegistrar {
    private const val ACCOUNT_ID = "proximity_upi123_call_account"

    fun getPhoneAccountHandle(context: Context): PhoneAccountHandle {
        val componentName = ComponentName(context.applicationContext, CallRouterService::class.java)
        return PhoneAccountHandle(componentName, ACCOUNT_ID)
    }

    /**
     * Clean up any previously registered custom PhoneAccount so Telecom never
     * routes UPI 123Pay tel: URIs into a dummy local ConnectionService instead of the SIM.
     */
    fun registerPhoneAccountIfSupported(context: Context) {
        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return
            telecomManager.unregisterPhoneAccount(getPhoneAccountHandle(context))
        } catch (_: Exception) {
        }
    }

    fun isDialableTelUri(dtmfUriString: String): Boolean {
        val trimmed = dtmfUriString.trim()
        return trimmed.startsWith("tel:", ignoreCase = true) && trimmed.length > 4
    }

    /**
     * Places a cellular PSTN call for UPI 123Pay with DTMF tones via TelecomManager.placeCall,
     * falling back to an ACTION_CALL activity intent if TelecomManager is unavailable or rejects.
     */
    fun initiateUpi123Call(context: Context, dtmfUriString: String): CallDialOutcome {
        if (!isDialableTelUri(dtmfUriString)) {
            return CallDialOutcome.FAILED
        }
        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasCallPermission) {
            return CallDialOutcome.FAILED
        }

        val uri = Uri.parse(dtmfUriString.trim())
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        if (telecomManager != null) {
            try {
                val extras = buildTelecomCallExtras(context, telecomManager)
                @Suppress("MissingPermission")
                telecomManager.placeCall(uri, extras)
                return CallDialOutcome.TELECOM_SERVICE
            } catch (_: SecurityException) {
                // Fall through to ACTION_CALL intent fallback below.
            } catch (_: Exception) {
                // Fall through to ACTION_CALL intent fallback below.
            }
        }

        return try {
            val callIntent = Intent(Intent.ACTION_CALL, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(callIntent)
            CallDialOutcome.CALL_INTENT_FALLBACK
        } catch (_: SecurityException) {
            CallDialOutcome.FAILED
        } catch (_: Exception) {
            CallDialOutcome.FAILED
        }
    }

    fun placeUpi123Call(context: Context, dtmfUriString: String): Boolean =
        initiateUpi123Call(context, dtmfUriString) != CallDialOutcome.FAILED

    private fun buildTelecomCallExtras(context: Context, telecomManager: TelecomManager): Bundle {
        val extras = Bundle()
        val hasPhoneState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPhoneState) {
            return extras
        }
        return try {
            @Suppress("MissingPermission")
            val defaultOutgoing = telecomManager.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL)
            if (defaultOutgoing != null) {
                extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, defaultOutgoing)
            }
            extras
        } catch (_: SecurityException) {
            Bundle()
        } catch (_: Exception) {
            Bundle()
        }
    }
}
