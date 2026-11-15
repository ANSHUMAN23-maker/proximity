// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.telephony

import android.content.Context
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MonitoredCallState {
    IDLE,
    RINGING,
    OFF_HOOK
}

class CallMonitor(private val context: Context) {
    private val _callState = MutableStateFlow(MonitoredCallState.IDLE)
    val callState: StateFlow<MonitoredCallState> = _callState.asStateFlow()

    @Volatile
    private var offHookTimestampMillis: Long = 0L

    private var telephonyCallback: Any? = null
    private var legacyListener: PhoneStateListener? = null

    fun startMonitoring(onCallEndedWithDuration: (Long) -> Unit) {
        stopMonitoring()
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
        offHookTimestampMillis = 0L
        _callState.value = MonitoredCallState.IDLE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    handleRawStateChange(state, onCallEndedWithDuration)
                }
            }
            telephonyCallback = cb
            try {
                tm.registerTelephonyCallback(context.mainExecutor, cb)
            } catch (_: SecurityException) {
            }
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    handleRawStateChange(state, onCallEndedWithDuration)
                }
            }
            legacyListener = listener
            try {
                @Suppress("DEPRECATION")
                tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            } catch (_: SecurityException) {
            }
        }
    }

    private fun handleRawStateChange(rawState: Int, onCallEndedWithDuration: (Long) -> Unit) {
        when (rawState) {
            TelephonyManager.CALL_STATE_RINGING -> {
                _callState.value = MonitoredCallState.RINGING
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (offHookTimestampMillis == 0L) {
                    offHookTimestampMillis = System.currentTimeMillis()
                }
                _callState.value = MonitoredCallState.OFF_HOOK
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                val previous = _callState.value
                _callState.value = MonitoredCallState.IDLE
                if ((previous == MonitoredCallState.OFF_HOOK || previous == MonitoredCallState.RINGING) &&
                    offHookTimestampMillis > 0L
                ) {
                    val duration = System.currentTimeMillis() - offHookTimestampMillis
                    offHookTimestampMillis = 0L
                    onCallEndedWithDuration(duration)
                } else {
                    offHookTimestampMillis = 0L
                }
            }
        }
    }

    fun stopMonitoring() {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (telephonyCallback as? TelephonyCallback)?.let { tm.unregisterTelephonyCallback(it) }
            } else {
                @Suppress("DEPRECATION")
                legacyListener?.let { tm.listen(it, PhoneStateListener.LISTEN_NONE) }
            }
        } catch (_: Exception) {
        }
        telephonyCallback = null
        legacyListener = null
        offHookTimestampMillis = 0L
        _callState.value = MonitoredCallState.IDLE
    }

    @Suppress("MissingPermission")
    fun terminateActiveCall(): Boolean {
        if (CallRouterService.disconnectActiveConnection()) {
            return true
        }
        return try {
            val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            @Suppress("DEPRECATION")
            telecom?.endCall() ?: false
        } catch (_: Exception) {
            false
        }
    }
}
