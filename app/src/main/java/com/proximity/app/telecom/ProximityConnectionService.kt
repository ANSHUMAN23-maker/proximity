

package com.proximity.app.telecom

import android.net.Uri
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import android.util.Log

class ProximityConnectionService : ConnectionService() {

    companion object {
        private const val TAG = "ProximityConnectionService"
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Log.d(TAG, "onCreateOutgoingConnection: ${request?.address}")

        val connection = object : Connection() {
            override fun onDisconnect() {
                Log.d(TAG, "Connection onDisconnect")
                setDisconnected(android.telecom.DisconnectCause(android.telecom.DisconnectCause.LOCAL))
                destroy()
            }

            override fun onAbort() {
                Log.d(TAG, "Connection onAbort")
                setDisconnected(android.telecom.DisconnectCause(android.telecom.DisconnectCause.LOCAL))
                destroy()
            }
        }

        connection.setAddress(
            request?.address ?: Uri.fromParts("tel", "", null),
            android.telecom.TelecomManager.PRESENTATION_ALLOWED
        )
        connection.setDialing()
        return connection
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Log.e(TAG, "Outgoing connection failed for: ${request?.address}")
    }
}
