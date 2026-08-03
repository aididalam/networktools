package com.nexdevlab.networktools.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.ContextCompat
import java.net.ConnectException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

class PortScannerRepository(private val context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    suspend fun prepare(config: PortScanConfig): PreparedPortScan = withContext(Dispatchers.IO) {
        val ports = config.validate()
        val target = PingRepository.normalizeTarget(config.target)
        val addresses = try {
            InetAddress.getAllByName(target).toList()
        } catch (_: UnknownHostException) {
            throw IllegalArgumentException("The host could not be resolved")
        }
        val address = addresses.firstOrNull { !it.isAnyLocalAddress && !it.isMulticastAddress }
            ?: throw IllegalArgumentException("No usable unicast address was found")
        val numeric = address.hostAddress ?: throw IllegalArgumentException("The host could not be resolved")
        PreparedPortScan(
            config = config,
            normalizedTarget = target,
            resolvedAddress = numeric,
            address = address,
            ports = ports,
            localNetworkTarget = isLocalNetworkAddress(address),
        )
    }

    suspend fun scan(
        prepared: PreparedPortScan,
        onEntries: (List<PortScanEntry>) -> Unit,
    ): PortScanResult = withContext(Dispatchers.IO) {
        require(!prepared.localNetworkTarget || hasLocalNetworkPermission(context)) {
            "Nearby devices permission is required to scan a local-network host"
        }
        val entries = mutableListOf<PortScanEntry>()
        prepared.ports.chunked(ScanConcurrency).forEach { chunk ->
            currentCoroutineContext().ensureActive()
            val scanned = chunk.map { port ->
                async { probe(prepared.address, port, prepared.config.timeoutMillis) }
            }.awaitAll()
            entries += scanned
            entries.sortBy(PortScanEntry::port)
            onEntries(entries.toList())
        }
        PortScanResult(
            target = prepared.normalizedTarget,
            resolvedAddress = prepared.resolvedAddress,
            entries = entries.toList(),
            timeoutMillis = prepared.config.timeoutMillis,
            stopped = false,
            completedAtMillis = System.currentTimeMillis(),
        )
    }

    fun localTarget(): String {
        val network = connectivityManager.activeNetwork ?: return "127.0.0.1"
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "127.0.0.1"
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return "127.0.0.1"
        return connectivityManager.getLinkProperties(network)?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address && it.address.isSiteLocalAddress }
            ?.address?.hostAddress ?: "127.0.0.1"
    }

    private suspend fun probe(address: InetAddress, port: Int, timeoutMillis: Int): PortScanEntry {
        val socket = Socket()
        val cancellation = currentCoroutineContext().job.invokeOnCompletion { runCatching { socket.close() } }
        val started = System.nanoTime()
        return try {
            socket.connect(InetSocketAddress(address, port), timeoutMillis)
            PortScanEntry(port, PortScanStatus.OPEN, serviceForPort(port), elapsedMillis(started))
        } catch (_: NoRouteToHostException) {
            PortScanEntry(port, PortScanStatus.UNREACHABLE, serviceForPort(port))
        } catch (_: SocketTimeoutException) {
            PortScanEntry(port, PortScanStatus.TIMED_OUT, serviceForPort(port))
        } catch (_: ConnectException) {
            PortScanEntry(port, PortScanStatus.CLOSED, serviceForPort(port), elapsedMillis(started))
        } catch (failure: SecurityException) {
            PortScanEntry(port, PortScanStatus.ERROR, serviceForPort(port))
        } catch (failure: SocketException) {
            currentCoroutineContext().ensureActive()
            val status = if (failure.message.orEmpty().contains("unreachable", ignoreCase = true)) {
                PortScanStatus.UNREACHABLE
            } else {
                PortScanStatus.ERROR
            }
            PortScanEntry(port, status, serviceForPort(port))
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            PortScanEntry(port, PortScanStatus.ERROR, serviceForPort(port))
        } finally {
            cancellation.dispose()
            runCatching { socket.close() }
        }
    }

    companion object {
        private const val ScanConcurrency = 32
        private val Services = mapOf(
            20 to "FTP data", 21 to "FTP", 22 to "SSH", 23 to "Telnet", 25 to "SMTP",
            53 to "DNS", 67 to "DHCP server", 68 to "DHCP client", 69 to "TFTP",
            80 to "HTTP", 88 to "Kerberos", 110 to "POP3", 111 to "RPC bind",
            123 to "NTP", 135 to "Microsoft RPC", 137 to "NetBIOS name", 138 to "NetBIOS datagram",
            139 to "NetBIOS session", 143 to "IMAP", 161 to "SNMP", 162 to "SNMP trap",
            179 to "BGP", 389 to "LDAP", 443 to "HTTPS", 445 to "SMB",
            465 to "SMTPS", 500 to "IKE", 514 to "Syslog", 515 to "Line printer",
            548 to "AFP", 554 to "RTSP", 587 to "SMTP submission", 631 to "IPP",
            636 to "LDAPS", 873 to "rsync", 993 to "IMAPS", 995 to "POP3S",
            1194 to "OpenVPN", 1433 to "Microsoft SQL Server", 1521 to "Oracle database",
            1723 to "PPTP", 1883 to "MQTT", 2049 to "NFS", 2375 to "Docker",
            2376 to "Docker TLS", 3000 to "Web development", 3306 to "MySQL",
            3389 to "Remote Desktop", 5000 to "Web / UPnP", 5060 to "SIP",
            5222 to "XMPP", 5353 to "mDNS", 5432 to "PostgreSQL", 5672 to "AMQP",
            5900 to "VNC", 6379 to "Redis", 8000 to "Alternate HTTP", 8080 to "HTTP proxy",
            8443 to "Alternate HTTPS", 8883 to "MQTT TLS", 9000 to "Web service",
            9090 to "Web service", 9100 to "Raw printing", 9200 to "Elasticsearch",
            9418 to "Git", 27017 to "MongoDB",
        )

        fun hasLocalNetworkPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) ==
                PackageManager.PERMISSION_GRANTED

        internal fun isLocalNetworkAddress(address: InetAddress): Boolean {
            if (address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress) return true
            if (address is Inet6Address) {
                val first = address.address.firstOrNull()?.toInt()?.and(0xff) ?: return false
                return first and 0xfe == 0xfc
            }
            return false
        }

        fun serviceForPort(port: Int): String = Services[port] ?: "Unknown service"

        private fun elapsedMillis(started: Long): Double = (System.nanoTime() - started) / 1_000_000.0
    }
}
