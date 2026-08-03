package com.nexdevlab.networktools.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress

class WakeOnLanRepository(context: Context) {
    private val appContext = context.applicationContext
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)

    fun detectLocalNetwork(): WakeLocalNetwork {
        val (_, details) = activeLocalNetwork()
        return details
    }

    suspend fun prepare(device: WakeOnLanDevice): PreparedWakeOnLanSend = withContext(Dispatchers.IO) {
        val safe = device.validated()
        if (safe.mode == WakeOnLanMode.LOCAL) {
            val (_, local) = activeLocalNetwork()
            require(safe.broadcastAddress == local.broadcastAddress) {
                "Saved broadcast does not match the active local network"
            }
            val localValue = LanSubnet.parseIpv4(local.localAddress) ?: error("Local address is unavailable")
            val targetValue = LanSubnet.parseIpv4(safe.targetAddress) ?: -1
            val networkMask = (0xffffffffL shl (32 - local.prefixLength)) and 0xffffffffL
            require(localValue and networkMask == targetValue and networkMask) {
                "Saved device is outside the active local network"
            }
            val destination = InetAddress.getByName(safe.broadcastAddress)
            PreparedWakeOnLanSend(safe, destination, local)
        } else {
            val addresses = InetAddress.getAllByName(safe.targetAddress).take(MaximumResolvedAddresses)
            val destination = addresses.firstOrNull(InetAddress::isPublicUnicast)
                ?: throw IllegalArgumentException("Remote destination must resolve to a public unicast address")
            PreparedWakeOnLanSend(safe, destination, null)
        }
    }

    suspend fun send(prepared: PreparedWakeOnLanSend): WakeOnLanResult = withContext(Dispatchers.IO) {
        val device = prepared.device.validated()
        val packetBytes = WakeMagicPacket.build(device.macAddress)
        DatagramSocket(null).use { socket ->
            socket.reuseAddress = true
            socket.broadcast = device.mode == WakeOnLanMode.LOCAL
            socket.bind(InetSocketAddress(0))
            if (device.mode == WakeOnLanMode.LOCAL) {
                val (network, current) = activeLocalNetwork()
                require(current.broadcastAddress == prepared.localNetwork?.broadcastAddress) {
                    "Local network changed before the packet was sent"
                }
                network.bindSocket(socket)
            }
            socket.send(DatagramPacket(packetBytes, packetBytes.size, prepared.resolvedDestination, device.udpPort))
        }
        WakeOnLanResult(
            deviceName = device.name,
            macAddress = device.macAddress,
            destination = device.destination,
            resolvedDestination = prepared.resolvedDestination.hostAddress ?: device.destination,
            udpPort = device.udpPort,
            packetSize = packetBytes.size,
            sentAtMillis = System.currentTimeMillis(),
        )
    }

    private fun activeLocalNetwork(): Pair<Network, WakeLocalNetwork> {
        val network = connectivityManager.activeNetwork
            ?: throw IllegalStateException("Connect to a Wi-Fi or Ethernet network")
        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: throw IllegalStateException("Active network details are unavailable")
        require(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            "Local Wake-on-LAN requires Wi-Fi or Ethernet"
        }
        val properties = connectivityManager.getLinkProperties(network)
            ?: throw IllegalStateException("Active network details are unavailable")
        val link = properties.linkAddresses.firstOrNull {
            it.address is Inet4Address && it.prefixLength in 1..30 &&
                LanSubnet.parseIpv4(it.address.hostAddress.orEmpty())?.let(LanSubnet::isPrivate) == true
        } ?: throw IllegalStateException("No private IPv4 local network is available")
        val value = LanSubnet.parseIpv4(link.address.hostAddress.orEmpty())
            ?: throw IllegalStateException("Local IPv4 address is unavailable")
        val hostMask = (1L shl (32 - link.prefixLength)) - 1L
        val broadcast = LanSubnet.formatIpv4(value or hostMask)
        return network to WakeLocalNetwork(
            localAddress = LanSubnet.formatIpv4(value),
            broadcastAddress = broadcast,
            prefixLength = link.prefixLength,
            interfaceName = properties.interfaceName,
        )
    }

    companion object {
        const val MinimumSendIntervalMillis = 2_000L
        private const val MaximumResolvedAddresses = 8

        fun hasLocalNetworkPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA ||
                PortScannerRepository.hasLocalNetworkPermission(context)
    }
}
