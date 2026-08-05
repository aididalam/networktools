package com.nexdevlab.networktools.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SpeedNetworkState(
    val network: Network?,
    val label: String,
    val connected: Boolean,
    val metered: Boolean,
)

internal data class SpeedServerOptionResult(
    val options: List<SpeedEndpointChoice>,
    val regionalFallback: Boolean,
)

class SpeedTestRepository(context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    fun networkState(): SpeedNetworkState {
        val network = connectivity.activeNetwork
        val capabilities = network?.let(connectivity::getNetworkCapabilities)
        val connected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return SpeedNetworkState(
            network = network,
            label = capabilities.networkLabel(),
            connected = connected,
            metered = connectivity.isActiveNetworkMetered,
        )
    }

    suspend fun run(
        choice: SpeedServerChoice,
        preferredMachine: String?,
        onServer: (SpeedTestServer) -> Unit,
        onProgress: (SpeedProgress) -> Unit,
    ): SpeedTestResult {
        val initial = networkState()
        val network = initial.network ?: throw IOException("Connect to the internet before starting a speed test")
        if (!initial.connected) throw IOException("The active network does not have validated internet access")
        if (choice.mode == SpeedServerMode.LOCAL) {
            return runNearestEdge(network, initial, onServer, onProgress)
        }
        val client = MlabNdt7Client(network)
        try {
            val discovered = client.discover(choice).servers
            val servers = if (preferredMachine == null) {
                client.rankByLatency(discovered)
            } else {
                listOf(
                    discovered.firstOrNull { it.machine == preferredMachine }
                        ?: throw IOException("The selected server is no longer available. Choose Automatic or select it again."),
                )
            }
            var lastFailure: Exception? = null
            servers.forEach { server ->
                var transferred = false
                try {
                    onServer(server)
                    val run = client.run(
                        server = server,
                        networkIsCurrent = { connectivity.activeNetwork == network },
                        onProgress = {
                            if (it.bytes > 0) transferred = true
                            onProgress(it)
                        },
                    )
                    val latency = run.rttSamplesMillis.filter { it.isFinite() && it >= 0 }.minOrNull()
                    return SpeedTestResult(
                        downloadMbps = run.download.megabitsPerSecond,
                        uploadMbps = run.upload.megabitsPerSecond,
                        latencyMillis = latency,
                        jitterMillis = calculateJitter(run.rttSamplesMillis),
                        serverCity = server.city,
                        serverCountryCode = server.countryCode,
                        serverMachine = server.machine,
                        networkType = initial.label,
                        completedAtMillis = System.currentTimeMillis(),
                    )
                } catch (failure: Exception) {
                    lastFailure = failure
                    if (transferred) throw failure
                }
            }
            throw lastFailure ?: IOException("No available server could complete the test")
        } finally {
            withContext(Dispatchers.IO) { client.close() }
        }
    }

    internal suspend fun discoverServerOptions(choice: SpeedServerChoice): SpeedServerOptionResult {
        if (choice.mode == SpeedServerMode.LOCAL) {
            return SpeedServerOptionResult(listOf(SpeedEndpointChoice.AUTOMATIC), regionalFallback = false)
        }
        val state = networkState()
        val network = state.network ?: throw IOException("Connect to the internet to find available servers")
        if (!state.connected) throw IOException("The active network does not have validated internet access")
        val client = MlabNdt7Client(network)
        return try {
            val discovery = client.discover(choice)
            SpeedServerOptionResult(
                options = speedEndpointChoices(discovery.servers),
                regionalFallback = discovery.regionalFallback,
            )
        } finally {
            withContext(Dispatchers.IO) { client.close() }
        }
    }

    internal suspend fun resolveLocalEdge(): LocalEdgeInfo? {
        val state = networkState()
        val network = state.network ?: return null
        if (!state.connected) return null
        val client = CloudflareSpeedClient(network)
        return try {
            client.resolveEdge()
        } catch (_: Exception) {
            null
        } finally {
            withContext(Dispatchers.IO) { client.close() }
        }
    }

    private suspend fun runNearestEdge(
        network: Network,
        initial: SpeedNetworkState,
        onServer: (SpeedTestServer) -> Unit,
        onProgress: (SpeedProgress) -> Unit,
    ): SpeedTestResult {
        val client = CloudflareSpeedClient(network)
        try {
            val edge = client.resolveEdge()
            val server = edge.asServer()
            onServer(server)
            val run = client.run(
                networkIsCurrent = { connectivity.activeNetwork == network },
                onProgress = onProgress,
            )
            return SpeedTestResult(
                downloadMbps = run.download.megabitsPerSecond,
                uploadMbps = run.upload.megabitsPerSecond,
                latencyMillis = percentile(run.rttSamplesMillis, 0.5),
                jitterMillis = calculateJitter(run.rttSamplesMillis),
                serverCity = server.city,
                serverCountryCode = server.countryCode,
                serverMachine = server.machine,
                networkType = initial.label,
                completedAtMillis = System.currentTimeMillis(),
            )
        } finally {
            withContext(Dispatchers.IO) { client.close() }
        }
    }
}

internal fun rankSpeedServers(
    servers: List<SpeedTestServer>,
    latencyMillis: Map<SpeedTestServer, Long?>,
): List<SpeedTestServer> = servers.withIndex()
    .sortedWith(
        compareBy<IndexedValue<SpeedTestServer>>(
            { latencyMillis[it.value] == null },
            { latencyMillis[it.value] ?: Long.MAX_VALUE },
            IndexedValue<SpeedTestServer>::index,
        ),
    )
    .map(IndexedValue<SpeedTestServer>::value)

private fun NetworkCapabilities?.networkLabel(): String = when {
    this == null -> "Offline"
    hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
    hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
    hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
    hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
    else -> "Network"
}
