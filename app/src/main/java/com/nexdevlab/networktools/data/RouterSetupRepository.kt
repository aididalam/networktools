package com.nexdevlab.networktools.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address

class RouterSetupRepository(context: Context) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    fun detectRouter(): DetectedRouter {
        val network = connectivityManager.activeNetwork
            ?: throw IllegalStateException("Connect to a Wi-Fi or Ethernet network")
        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: throw IllegalStateException("Active network details are unavailable")
        val connectionLabel = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> throw IllegalStateException("Router detection requires Wi-Fi or Ethernet")
        }
        val properties = connectivityManager.getLinkProperties(network)
            ?: throw IllegalStateException("Active network details are unavailable")
        val gateway = properties.routes
            .filter { it.isDefaultRoute && it.gateway != null }
            .sortedByDescending { it.gateway is Inet4Address }
            .firstNotNullOfOrNull { route ->
                val displayed = route.gateway?.hostAddress ?: return@firstNotNullOfOrNull null
                runCatching { RouterAddress.parse(displayed, RouterAddressSource.DETECTED) }.getOrNull()
            }
            ?: throw IllegalStateException("No private router gateway was found")
        return DetectedRouter(gateway, properties.interfaceName, connectionLabel)
    }
}
