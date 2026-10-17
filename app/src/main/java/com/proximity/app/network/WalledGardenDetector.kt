package com.proximity.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class WalledGardenDetector(private val context: Context) {

    companion object {
        private const val TAG = "WalledGardenDetector"

        /**
         * Android's standard captive-portal probe URL.
         * Must be HTTP (not HTTPS) so that ISP transparent proxies can
         * intercept and redirect it. Declared safe in network_security_config.xml.
         */
        private const val PROBE_URL = "http://connectivitycheck.gstatic.com/generate_204"
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS    = 5_000

        /** Maximum number of redirect hops to follow manually. */
        private const val MAX_REDIRECT_HOPS = 3
    }

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Runs the captive-portal probe on [Dispatchers.IO].
     * Returns a [WalledGardenResult] — never throws.
     */
    suspend fun check(): WalledGardenResult = withContext(Dispatchers.IO) {
        try {
            val cellularNetwork = findCellularNetwork()
                ?: return@withContext WalledGardenResult.NoNetwork.also {
                    Log.d(TAG, "No active cellular network found")
                }

            val portalUrl = probe(cellularNetwork)
                ?: return@withContext WalledGardenResult.Connected.also {
                    Log.d(TAG, "Cellular internet is healthy (204 received)")
                }

            Log.i(TAG, "Walled garden detected — portal URL: $portalUrl")
            WalledGardenResult.Detected(portalUrl, cellularNetwork)

        } catch (e: Exception) {
            Log.w(TAG, "Probe failed unexpectedly: ${e.message}")
            WalledGardenResult.NoNetwork
        }
    }

    /**
     * Returns the first active network that uses the cellular transport,
     * or null if no such network exists. Prioritizes the active data network
     * over dormant secondary SIM networks.
     */
    fun findCellularNetwork(): Network? {
        val isCellular = { net: Network? ->
            net?.let { connectivityManager.getNetworkCapabilities(it) }?.run {
                hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            } == true
        }

        // Try the active network first
        val active = connectivityManager.activeNetwork
        if (isCellular(active)) return active

        // Dual-SIM fallback: find the explicit active data subscription
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? android.telephony.SubscriptionManager
            if (context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                val dataSubId = android.telephony.SubscriptionManager.getDefaultDataSubscriptionId()
                if (dataSubId != android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    val networks = connectivityManager.allNetworks.filter { isCellular(it) }
                    // Unfortunately getNetworkForSubscriptionId is hidden or requires reflection in some APIs,
                    // or doesn't exist. Android 30+ added TelephonyManager.createForSubscriptionId(dataSubId)
                    // We will just fall back to the first cellular network that has internet if we can't tie it easily.
                    // Wait, let's just stick to the filter, it is the safest cross-platform method without complex reflection.
                    val internetNet = networks.firstOrNull {
                        connectivityManager.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                    }
                    if (internetNet != null) return internetNet
                    return networks.firstOrNull()
                }
            }
        }

        // Fall back to scanning all networks, prioritizing those that claim internet
        @Suppress("DEPRECATION")
        return connectivityManager.allNetworks
            .filter { isCellular(it) }
            .sortedByDescending { net ->
                connectivityManager.getNetworkCapabilities(net)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            }
            .firstOrNull()
    }

    /**
     * Sends the HTTP probe through [network] without following redirects.
     *
     * @return The resolved portal URL if a redirect was detected, null if 204.
     */
    private suspend fun probe(network: Network): String? = kotlinx.coroutines.withTimeout(10_000L) {
        var currentUrl = PROBE_URL
        repeat(MAX_REDIRECT_HOPS) { hop ->
            val url = URL(currentUrl)
            // Open connection through the specific cellular network interface
            val connection = network.openConnection(url) as HttpURLConnection
            connection.apply {
                instanceFollowRedirects = false // We handle redirects manually
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("User-Agent", "ProximityApp/1.0 CaptiveNetworkSupport")
            }

            try {
                val responseCode = connection.responseCode
                Log.d(TAG, "Hop $hop → $currentUrl → HTTP $responseCode")

                when (responseCode) {
                    204 -> return@withTimeout null // Healthy internet

                    301, 302, 307, 308 -> {
                        val location = connection.getHeaderField("Location")
                        if (location.isNullOrBlank()) {
                            Log.w(TAG, "Redirect with no Location header — treating as no network")
                            return@withTimeout null
                        }
                        // Resolve potentially relative Location header against current URL
                        val resolvedUrl = URL(URL(currentUrl), location).toString()

                        // If this is the last hop, return the final URL
                        if (hop == MAX_REDIRECT_HOPS - 1) return@withTimeout resolvedUrl
                        currentUrl = resolvedUrl // Follow the chain
                    }

                    200 -> {
                        // Some operators (like Jio) return 200 OK with an HTML portal page
                        // instead of a 302. Verify it's actually an HTML page.
                        val contentType = connection.contentType ?: ""
                        if (contentType.contains("text/html", ignoreCase = true)) {
                            Log.d(TAG, "HTTP 200 with HTML body — assuming Walled Garden portal")
                            return@withTimeout connection.url.toString()
                        }

                        // Sniff body for keywords if not strictly text/html
                        val bodyStart = runCatching {
                            val reader = connection.inputStream.bufferedReader()
                            val buffer = CharArray(512)
                            val readCount = reader.read(buffer)
                            if (readCount > 0) String(buffer, 0, readCount).lowercase() else ""
                        }.getOrNull() ?: ""

                        if (bodyStart.contains("<form") || bodyStart.contains("recharge") || bodyStart.contains("portal") || bodyStart.contains("meta http-equiv=\"refresh\"")) {
                            Log.d(TAG, "HTTP 200 sniffed portal keywords — assuming Walled Garden portal")
                            return@withTimeout connection.url.toString()
                        }

                        Log.d(TAG, "Unexpected HTTP 200 with content-type $contentType and clean body — treating as healthy")
                        return@withTimeout null
                    }

                    else -> {
                        Log.d(TAG, "Unexpected HTTP $responseCode — treating as no network")
                        return@withTimeout null
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
        currentUrl
    }
}
