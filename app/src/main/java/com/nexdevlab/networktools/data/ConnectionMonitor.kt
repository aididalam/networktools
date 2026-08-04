package com.nexdevlab.networktools.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper

class ConnectionMonitor(context: Context) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var previous: ConnectionStateSnapshot? = null
    private var listener: ((ConnectionLogEntry?, ConnectionStateSnapshot) -> Unit)? = null
    private val delayedEvaluation = Runnable(::evaluate)

    fun start(initial: ConnectionStateSnapshot?, onState: (ConnectionLogEntry?, ConnectionStateSnapshot) -> Unit) {
        if (callback != null) return
        previous = initial
        listener = onState
        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = postEvaluation()
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = postEvaluation()
            override fun onLost(network: Network) {
                handler.removeCallbacks(delayedEvaluation)
                handler.postDelayed(delayedEvaluation, SwitchCoalescingMillis)
            }
        }.also(connectivityManager::registerDefaultNetworkCallback)
        handler.post(::evaluate)
    }

    fun stop() {
        handler.removeCallbacks(delayedEvaluation)
        callback?.let { runCatching { connectivityManager.unregisterNetworkCallback(it) } }
        callback = null
        listener = null
    }

    fun snapshot(): ConnectionStateSnapshot {
        val network = connectivityManager.activeNetwork ?: return ConnectionStateSnapshot(ConnectionKind.OFFLINE)
        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: return ConnectionStateSnapshot(ConnectionKind.OFFLINE)
        val kind = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> ConnectionKind.VPN
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionKind.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionKind.MOBILE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionKind.ETHERNET
            else -> ConnectionKind.OTHER
        }
        return ConnectionStateSnapshot(
            kind = kind,
            metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        )
    }

    private fun postEvaluation() {
        handler.removeCallbacks(delayedEvaluation)
        handler.post(delayedEvaluation)
    }

    private fun evaluate() {
        val current = snapshot()
        val entry = ConnectionLogEntry.transition(previous, current, System.currentTimeMillis())
        previous = current
        listener?.invoke(entry, current)
    }

    private companion object {
        const val SwitchCoalescingMillis = 350L
    }
}
