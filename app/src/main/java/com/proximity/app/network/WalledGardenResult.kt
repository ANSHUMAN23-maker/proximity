package com.proximity.app.network

import android.net.Network

sealed class WalledGardenResult {

    object Connected : WalledGardenResult()

    object NoNetwork : WalledGardenResult()

    data class Detected(
        val portalUrl: String,
        val network: Network
    ) : WalledGardenResult()
}
