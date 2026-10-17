package com.proximity.app.telephony

import android.content.Context
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

sealed interface DeviceCallState {
    data object Idle : DeviceCallState
    data object Ringing : DeviceCallState
    data object OffHook : DeviceCallState
}

sealed interface CallSessionEvent {
    data class Started(val timestampMs: Long) : CallSessionEvent

    data class Ended(val durationMs: Long) : CallSessionEvent
}

interface CallStateSource {
    val callState: StateFlow<DeviceCallState>
    val sessionEvents: SharedFlow<CallSessionEvent>
    fun acquire(tag: String)
    fun release(tag: String)
}

class CallStateCoordinator(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis
) : CallStateSource {

    companion object {
        private const val TAG = "CallStateCoordinator"
    }

    private val appContext = context.applicationContext
    private val telephonyManager =
        appContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private val _callState = MutableStateFlow<DeviceCallState>(DeviceCallState.Idle)
    override val callState: StateFlow<DeviceCallState> = _callState.asStateFlow()

    private val _sessionEvents = MutableSharedFlow<CallSessionEvent>(extraBufferCapacity = 16)
    override val sessionEvents: SharedFlow<CallSessionEvent> = _sessionEvents.asSharedFlow()

    private val refCount = AtomicInteger(0)
    private val registrationLock = Any()

    private var registered = false

    @Volatile
    private var offHookSinceMs = 0L

    private var telephonyCallback: TelephonyCallback? = null

    @Suppress("DEPRECATION")
    private var phoneStateListener: PhoneStateListener? = null

    override fun acquire(tag: String) {
        refCount.incrementAndGet()
        synchronized(registrationLock) {
            if (refCount.get() > 0 && !registered) {
                register(tag)
            }
        }
        Log.d(TAG, "acquire($tag) refCount=${refCount.get()}")
    }

    override fun release(tag: String) {
        val remaining = refCount.decrementAndGet()
        if (remaining <= 0) {
            refCount.set(0)
            synchronized(registrationLock) {
                if (registered) {
                    unregister(tag)
                }
            }
        }
        Log.d(TAG, "release($tag) refCount=${refCount.get()}")
    }

    private fun register(tag: String) {
        synchronized(registrationLock) {
            if (registered) return
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                        override fun onCallStateChanged(state: Int) {
                            handleStateChange(state)
                        }
                    }
                    telephonyManager.registerTelephonyCallback(
                        ContextCompat.getMainExecutor(appContext),
                        callback
                    )
                    telephonyCallback = callback
                } else {
                    @Suppress("DEPRECATION")
                    val listener = object : PhoneStateListener() {
                        @Deprecated("Deprecated in Java")
                        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                            handleStateChange(state)
                        }
                    }
                    @Suppress("DEPRECATION")
                    telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
                    phoneStateListener = listener
                }
                registered = true
                handleStateChange(currentCallStateCompat())
                Log.d(TAG, "Telephony listener registered ($tag)")
            } catch (e: SecurityException) {
                Log.e(TAG, "Cannot register telephony listener: ${e.message}")
                telephonyCallback = null
                phoneStateListener = null
                registered = false
            }
        }
    }

    private fun unregister(tag: String) {
        synchronized(registrationLock) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    telephonyCallback?.let { telephonyManager.unregisterTelephonyCallback(it) }
                } else {
                    @Suppress("DEPRECATION")
                    phoneStateListener?.let {
                        telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
                    }
                }
                Log.d(TAG, "Telephony listener unregistered ($tag)")
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering telephony listener", e)
            } finally {
                telephonyCallback = null
                phoneStateListener = null
                registered = false
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun currentCallStateCompat(): Int = try {
        telephonyManager.callState
    } catch (e: SecurityException) {
        TelephonyManager.CALL_STATE_IDLE
    }

    internal fun handleStateChange(state: Int) {
        val newState = when (state) {
            TelephonyManager.CALL_STATE_RINGING -> DeviceCallState.Ringing
            TelephonyManager.CALL_STATE_OFFHOOK -> DeviceCallState.OffHook
            else -> DeviceCallState.Idle
        }
        val previous = _callState.value
        if (previous == newState) return
        _callState.value = newState
        Log.d(TAG, "Call state: $previous -> $newState")

        when {
            newState is DeviceCallState.OffHook && previous !is DeviceCallState.OffHook -> {
                offHookSinceMs = clock()
                _sessionEvents.tryEmit(CallSessionEvent.Started(offHookSinceMs))
            }
            newState is DeviceCallState.Idle && previous is DeviceCallState.OffHook -> {
                val duration = (clock() - offHookSinceMs).coerceAtLeast(0L)
                _sessionEvents.tryEmit(CallSessionEvent.Ended(duration))
            }
        }
    }
}
