package com.nexdevlab.networktools.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class LanNetworkContext(
    val subnet: LanSubnet,
    val localAddress: String,
    val gatewayAddress: String?,
    val interfaceName: String?,
)

internal data class NeighborRecord(val ipAddress: String, val macAddress: String)

internal data class NetbiosRecord(val hostname: String?, val macAddress: String?)

private data class LanServiceObservation(
    val ipAddress: String,
    val name: String?,
    val type: String,
)

class LanScannerRepository(private val context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    fun detectNetwork(): LanNetworkContext {
        val network = connectivityManager.activeNetwork
            ?: throw IllegalStateException("Connect to a private Wi-Fi or Ethernet network")
        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: throw IllegalStateException("Active network details are unavailable")
        require(!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            "Automatic detection is unavailable while a VPN is the active network"
        }
        require(
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
        ) { "Connect to a private Wi-Fi or Ethernet network" }
        val properties = connectivityManager.getLinkProperties(network)
            ?: throw IllegalStateException("Active network details are unavailable")
        val link = properties.linkAddresses.firstOrNull {
            it.address is Inet4Address && LanSubnet.parseIpv4(it.address.hostAddress.orEmpty())?.let(LanSubnet::isPrivate) == true
        } ?: throw IllegalStateException("No private IPv4 LAN address is available")
        val local = link.address.hostAddress ?: throw IllegalStateException("Local IPv4 address is unavailable")
        val localValue = LanSubnet.parseIpv4(local) ?: throw IllegalStateException("Local IPv4 address is unavailable")
        val subnet = LanSubnet.fromAddress(localValue, link.prefixLength.coerceAtLeast(LanSubnet.PrefixRange.first))
        val gateway = properties.routes.firstOrNull { route ->
            route.isDefaultRoute && route.gateway is Inet4Address &&
                LanSubnet.parseIpv4(route.gateway?.hostAddress.orEmpty())?.let(subnet::contains) == true
        }?.gateway?.hostAddress
        return LanNetworkContext(subnet, local, gateway, properties.interfaceName)
    }

    suspend fun scan(
        config: LanScanConfig,
        onPhase: (String) -> Unit,
        onProgress: (Int, Int) -> Unit,
        onDevices: (List<LanDevice>) -> Unit,
    ): LanScanResult = withContext(Dispatchers.IO) {
        config.validate()
        require(hasLocalNetworkPermission(context)) { "Nearby devices permission is required for LAN scanning" }
        val active = detectNetwork()
        require(config.subnet.contains(LanSubnet.parseIpv4(active.localAddress) ?: -1)) {
            "The selected subnet does not contain this device's active LAN address"
        }

        val devices = linkedMapOf<String, DeviceAccumulator>()
        fun accumulator(ip: String): DeviceAccumulator? {
            val value = LanSubnet.parseIpv4(ip)?.takeIf(config.subnet::contains) ?: return null
            val normalized = LanSubnet.formatIpv4(value)
            return devices.getOrPut(normalized) { DeviceAccumulator(normalized) }
        }
        fun publish() = onDevices(devices.values.map(DeviceAccumulator::toDevice).sortedBy(LanDevice::sortAddress))

        accumulator(active.localAddress)?.apply { thisDevice = true }
        active.gatewayAddress?.let { accumulator(it)?.gateway = true }
        publish()

        val hosts = config.subnet.hostAddresses().map(LanSubnet::formatIpv4)
        var scanned = 0
        onPhase("Discovering active devices")
        onProgress(scanned, hosts.size)
        hosts.chunked(PingConcurrency).forEach { chunk ->
            currentCoroutineContext().ensureActive()
            val replies = chunk.map { address -> async { address to ping(address) } }.awaitAll()
            replies.forEach { (address, replied) ->
                if (replied) accumulator(address)?.protocols?.add(LanDiscoveryProtocol.PING)
            }
            scanned += chunk.size
            onProgress(scanned, hosts.size)
            publish()
        }

        onPhase("Reading neighbor table")
        readNeighbors().forEach { neighbor ->
            accumulator(neighbor.ipAddress)?.apply {
                macAddress = neighbor.macAddress
                protocols += LanDiscoveryProtocol.ARP
            }
        }
        publish()

        if (config.mode == LanScanMode.DEEP) {
            onPhase("Discovering local services")
            val ssdp = async { discoverSsdp(config.subnet) }
            val dnsSd = discoverDnsSd(config.subnet)
            dnsSd.forEach { service ->
                accumulator(service.ipAddress)?.apply {
                    service.name?.let { if (hostname.isNullOrBlank()) hostname = it }
                    services += serviceLabel(service)
                    protocols += LanDiscoveryProtocol.MDNS
                    protocols += LanDiscoveryProtocol.BONJOUR
                    if (service.type.contains("_snmp._udp", ignoreCase = true)) {
                        protocols += LanDiscoveryProtocol.SNMP
                    }
                }
            }
            ssdp.await().forEach { service ->
                accumulator(service.ipAddress)?.apply {
                    services += serviceLabel(service)
                    protocols += LanDiscoveryProtocol.SSDP
                    protocols += LanDiscoveryProtocol.UPNP
                }
            }
            publish()

            onPhase("Resolving device names")
            val current = devices.keys.toList()
            current.chunked(HostnameConcurrency).forEach { chunk ->
                val resolved = chunk.map { address -> async { address to reverseHostname(address) } }.awaitAll()
                resolved.forEach { (address, hostname) ->
                    if (!hostname.isNullOrBlank() && devices[address]?.hostname.isNullOrBlank()) {
                        devices[address]?.hostname = hostname
                    }
                }
                publish()
            }

            onPhase("Checking NetBIOS identity")
            devices.keys.toList().take(MaximumDeepDevices).chunked(NetbiosConcurrency).forEach { chunk ->
                val records = chunk.map { address -> async { address to queryNetbios(address) } }.awaitAll()
                records.forEach { (address, record) ->
                    record ?: return@forEach
                    devices[address]?.apply {
                        record.hostname?.let { if (hostname.isNullOrBlank()) hostname = it }
                        record.macAddress?.let { if (macAddress.isNullOrBlank()) macAddress = it }
                        protocols += LanDiscoveryProtocol.NETBIOS
                    }
                }
                publish()
            }

            onPhase("Checking common TCP ports")
            devices.keys.toList().take(MaximumPortScanDevices).chunked(PortDeviceConcurrency).forEach { chunk ->
                currentCoroutineContext().ensureActive()
                val results = chunk.map { address -> async { address to scanCommonPorts(address) } }.awaitAll()
                results.forEach { (address, ports) ->
                    if (ports.isNotEmpty()) {
                        devices[address]?.apply {
                            openPorts.clear()
                            openPorts += ports
                            protocols += LanDiscoveryProtocol.TCP
                        }
                    }
                }
                publish()
            }
        }

        onPhase("Finalizing results")
        val finalDevices = devices.values.map(DeviceAccumulator::toDevice).sortedBy(LanDevice::sortAddress)
        onDevices(finalDevices)
        LanScanResult(
            subnet = config.subnet.display,
            mode = config.mode,
            devices = finalDevices,
            scannedHosts = scanned,
            stopped = false,
            completedAtMillis = System.currentTimeMillis(),
        )
    }

    private suspend fun ping(address: String): Boolean {
        val process = try {
            ProcessBuilder("/system/bin/ping", "-n", "-c", "1", "-W", "1", address)
                .redirectErrorStream(true)
                .start()
        } catch (_: Exception) {
            return false
        }
        val cancellation = currentCoroutineContext().job.invokeOnCompletion { process.destroy() }
        return try {
            val output = withTimeoutOrNull(PingTimeoutMillis) {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    buildString {
                        while (length < MaximumPingOutputCharacters) {
                            val line = reader.readLine() ?: break
                            appendLine(line)
                        }
                    }
                }
            }
            if (output == null) process.destroy() else process.waitFor()
            currentCoroutineContext().ensureActive()
            output?.let(::parsePingReply) == true
        } finally {
            cancellation.dispose()
            process.destroy()
        }
    }

    private fun readNeighbors(): List<NeighborRecord> {
        val output = runCatching {
            ProcessBuilder("ip", "neigh", "show").redirectErrorStream(true).start().let { process ->
                process.inputStream.bufferedReader().use { it.readText().take(MaximumNeighborOutputCharacters) }
            }
        }.getOrDefault("")
        val fromIp = parseIpNeighbors(output)
        if (fromIp.isNotEmpty()) return fromIp
        val arp = runCatching { File("/proc/net/arp").readText().take(MaximumNeighborOutputCharacters) }.getOrDefault("")
        return parseProcArp(arp)
    }

    private suspend fun discoverSsdp(subnet: LanSubnet): List<LanServiceObservation> = withContext(Dispatchers.IO) {
        val socket = DatagramSocket().apply {
            reuseAddress = true
            soTimeout = SsdpReceiveSliceMillis
        }
        val cancellation = currentCoroutineContext().job.invokeOnCompletion { socket.close() }
        try {
            val request = (
                "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: 239.255.255.250:1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 1\r\n" +
                    "ST: ssdp:all\r\n\r\n"
                ).toByteArray(StandardCharsets.US_ASCII)
            socket.send(DatagramPacket(request, request.size, InetAddress.getByName("239.255.255.250"), 1900))
            val deadline = System.currentTimeMillis() + SsdpDiscoveryMillis
            val found = linkedMapOf<String, LanServiceObservation>()
            while (System.currentTimeMillis() < deadline && found.size < MaximumServiceObservations) {
                currentCoroutineContext().ensureActive()
                val packet = DatagramPacket(ByteArray(MaximumSsdpPacketBytes), MaximumSsdpPacketBytes)
                try {
                    socket.receive(packet)
                } catch (_: java.net.SocketTimeoutException) {
                    continue
                }
                val source = packet.address?.hostAddress ?: continue
                val value = LanSubnet.parseIpv4(source)?.takeIf(subnet::contains) ?: continue
                val response = String(packet.data, packet.offset, packet.length, StandardCharsets.ISO_8859_1)
                val headers = parseSsdpHeaders(response)
                val name = headers["server"] ?: headers["usn"] ?: headers["st"]
                found[LanSubnet.formatIpv4(value)] = LanServiceObservation(
                    LanSubnet.formatIpv4(value),
                    sanitizeText(name),
                    sanitizeText(headers["st"]) ?: "UPnP",
                )
            }
            found.values.toList()
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            emptyList()
        } finally {
            cancellation.dispose()
            socket.close()
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private suspend fun discoverDnsSd(subnet: LanSubnet): List<LanServiceObservation> {
        val unresolved = mutableListOf<NsdServiceInfo>()
        for (type in DnsSdServiceTypes) {
            currentCoroutineContext().ensureActive()
            val found = withContext(Dispatchers.Main.immediate) {
                val services = mutableListOf<NsdServiceInfo>()
                val listener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String?) = Unit
                    override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                        if (serviceInfo != null && services.size < MaximumServiceObservations) services += serviceInfo
                    }
                    override fun onServiceLost(serviceInfo: NsdServiceInfo?) = Unit
                    override fun onDiscoveryStopped(serviceType: String?) = Unit
                    override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) = Unit
                    override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) = Unit
                }
                try {
                    nsdManager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
                    delay(DnsSdDiscoveryPerTypeMillis)
                } catch (_: Exception) {
                    // Unsupported service discovery is represented by an empty result.
                } finally {
                    runCatching { nsdManager.stopServiceDiscovery(listener) }
                }
                services.toList()
            }
            unresolved += found
            if (unresolved.size >= MaximumServiceObservations) break
        }
        return buildList {
            for (service in unresolved.distinctBy { it.serviceName to it.serviceType }.take(MaximumServiceObservations)) {
                currentCoroutineContext().ensureActive()
                val resolved = resolveService(service) ?: continue
                val address = resolved.host?.hostAddress ?: continue
                val numeric = LanSubnet.parseIpv4(address)?.takeIf(subnet::contains) ?: continue
                add(
                    LanServiceObservation(
                        ipAddress = LanSubnet.formatIpv4(numeric),
                        name = sanitizeText(resolved.serviceName),
                        type = sanitizeText(resolved.serviceType) ?: "DNS-SD",
                    ),
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private suspend fun resolveService(service: NsdServiceInfo): NsdServiceInfo? =
        withContext(Dispatchers.Main.immediate) {
            withTimeoutOrNull(DnsSdResolveTimeoutMillis) {
                suspendCancellableCoroutine { continuation ->
                    runCatching {
                        nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                                if (continuation.isActive) continuation.resume(null)
                            }

                            override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                                if (continuation.isActive) continuation.resume(serviceInfo)
                            }
                        })
                    }.onFailure {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            }
        }

    private suspend fun reverseHostname(address: String): String? = withTimeoutOrNull(ReverseHostnameTimeoutMillis) {
        withContext(Dispatchers.IO) {
            runCatching {
                InetAddress.getByName(address).canonicalHostName
                    .takeIf { it.isNotBlank() && !it.equals(address, ignoreCase = true) }
                    ?.trimEnd('.')
                    ?.let(::sanitizeText)
            }.getOrNull()
        }
    }

    private suspend fun queryNetbios(address: String): NetbiosRecord? = withContext(Dispatchers.IO) {
        val socket = DatagramSocket().apply { soTimeout = NetbiosTimeoutMillis }
        val cancellation = currentCoroutineContext().job.invokeOnCompletion { socket.close() }
        try {
            val query = buildNetbiosNodeStatusQuery(NetbiosTransactionId)
            socket.send(DatagramPacket(query, query.size, InetAddress.getByName(address), NetbiosPort))
            val response = DatagramPacket(ByteArray(MaximumNetbiosPacketBytes), MaximumNetbiosPacketBytes)
            socket.receive(response)
            parseNetbiosNodeStatus(response.data.copyOfRange(response.offset, response.offset + response.length), NetbiosTransactionId)
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            null
        } finally {
            cancellation.dispose()
            socket.close()
        }
    }

    private suspend fun scanCommonPorts(address: String): List<LanOpenPort> = coroutineScope {
        val inetAddress = runCatching { InetAddress.getByName(address) }.getOrNull() ?: return@coroutineScope emptyList()
        CommonLanPorts.map { port ->
            async {
                if (probeTcpPort(inetAddress, port)) {
                    LanOpenPort(port, PortScannerRepository.serviceForPort(port))
                } else null
            }
        }.awaitAll().filterNotNull().sortedBy(LanOpenPort::port)
    }

    private suspend fun probeTcpPort(address: InetAddress, port: Int): Boolean {
        val socket = Socket()
        val cancellation = currentCoroutineContext().job.invokeOnCompletion { runCatching { socket.close() } }
        return try {
            socket.connect(InetSocketAddress(address, port), PortProbeTimeoutMillis)
            true
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            false
        } finally {
            cancellation.dispose()
            runCatching { socket.close() }
        }
    }

    private class DeviceAccumulator(val ipAddress: String) {
        var hostname: String? = null
        var macAddress: String? = null
        var thisDevice = false
        var gateway = false
        val protocols = linkedSetOf<LanDiscoveryProtocol>()
        val services = linkedSetOf<String>()
        val openPorts = mutableListOf<LanOpenPort>()

        fun toDevice(): LanDevice {
            val safeMac = macAddress?.lowercase(Locale.US)?.takeIf(MacPattern::matches)
            val manufacturer = safeMac?.let(::manufacturerForMac)
            val safeOpenPorts = openPorts.distinctBy(LanOpenPort::port).sortedBy(LanOpenPort::port)
            val identitySignals = services + safeOpenPorts.map { "${it.port} ${it.service}" }
            return LanDevice(
                ipAddress = ipAddress,
                hostname = sanitizeText(hostname),
                macAddress = safeMac,
                manufacturer = manufacturer,
                deviceType = estimateDeviceType(thisDevice, gateway, hostname, manufacturer, identitySignals.toSet()),
                protocols = protocols.toSet(),
                services = services.mapNotNull(::sanitizeText).take(MaximumServicesPerDevice).toSet(),
                openPorts = safeOpenPorts,
                thisDevice = thisDevice,
                gateway = gateway,
                lastSeenMillis = System.currentTimeMillis(),
            )
        }
    }

    companion object {
        private const val PingConcurrency = 20
        private const val HostnameConcurrency = 8
        private const val NetbiosConcurrency = 8
        private const val PortDeviceConcurrency = 4
        private const val MaximumDeepDevices = 64
        private const val MaximumPortScanDevices = 64
        private const val PingTimeoutMillis = 1_300L
        private const val MaximumPingOutputCharacters = 8_192
        private const val MaximumNeighborOutputCharacters = 128 * 1024
        private const val SsdpReceiveSliceMillis = 250
        private const val SsdpDiscoveryMillis = 1_600L
        private const val MaximumSsdpPacketBytes = 8_192
        private const val MaximumServiceObservations = 32
        private const val DnsSdDiscoveryPerTypeMillis = 550L
        private const val DnsSdResolveTimeoutMillis = 900L
        private const val ReverseHostnameTimeoutMillis = 900L
        private const val NetbiosTimeoutMillis = 450
        private const val PortProbeTimeoutMillis = 450
        private const val NetbiosPort = 137
        private const val NetbiosTransactionId = 0x4e54
        private const val MaximumNetbiosPacketBytes = 2_048
        private const val MaximumServicesPerDevice = 20
        private const val MaximumTextLength = 160
        private val MacPattern = Regex("^(?:[0-9a-f]{2}:){5}[0-9a-f]{2}$")
        private val DnsSdServiceTypes = listOf(
            "_http._tcp.",
            "_workstation._tcp.",
            "_googlecast._tcp.",
            "_airplay._tcp.",
            "_ipp._tcp.",
            "_snmp._udp.",
        )
        private val CommonLanPorts = listOf(
            21, 22, 23, 53, 80, 139, 443, 445, 515, 554, 631, 1883,
            3000, 5000, 5900, 8000, 8080, 8443, 8883, 9000, 9090, 9100,
        )
        private val OuiManufacturers = mapOf(
            "001A11" to "Google",
            "001D7E" to "Cisco-Linksys",
            "0024E4" to "Withings",
            "18B430" to "Nest",
            "3C5A37" to "Samsung",
            "44D9E7" to "Ubiquiti",
            "50C7BF" to "TP-Link",
            "5CF370" to "Cisco",
            "64BC0C" to "LG Electronics",
            "6C5AB0" to "TP-Link",
            "B01921" to "TP-Link",
            "B827EB" to "Raspberry Pi",
            "D850E6" to "ASUS",
            "DC A6 32".replace(" ", "") to "Raspberry Pi",
            "E4F0A6" to "Huawei",
            "F4F5D8" to "Google",
        )

        fun hasLocalNetworkPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) ==
                PackageManager.PERMISSION_GRANTED

        internal fun parsePingReply(output: String): Boolean {
            val lower = output.lowercase(Locale.US)
            return ("bytes from" in lower || "1 received" in lower || "1 packets received" in lower) &&
                "100% packet loss" !in lower
        }

        internal fun parseIpNeighbors(output: String): List<NeighborRecord> = output.lineSequence()
            .mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                val address = parts.firstOrNull() ?: return@mapNotNull null
                val lladdr = parts.indexOf("lladdr").takeIf { it >= 0 }?.let { parts.getOrNull(it + 1) }
                    ?.lowercase(Locale.US)?.takeIf(MacPattern::matches) ?: return@mapNotNull null
                val normalized = LanSubnet.parseIpv4(address)?.let(LanSubnet::formatIpv4) ?: return@mapNotNull null
                NeighborRecord(normalized, lladdr)
            }
            .distinctBy(NeighborRecord::ipAddress)
            .take(254)
            .toList()

        internal fun parseProcArp(output: String): List<NeighborRecord> = output.lineSequence()
            .drop(1)
            .mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size < 6 || parts[2] == "0x0") return@mapNotNull null
                val address = LanSubnet.parseIpv4(parts[0])?.let(LanSubnet::formatIpv4) ?: return@mapNotNull null
                val mac = parts[3].lowercase(Locale.US).takeIf(MacPattern::matches) ?: return@mapNotNull null
                NeighborRecord(address, mac)
            }
            .distinctBy(NeighborRecord::ipAddress)
            .take(254)
            .toList()

        internal fun parseSsdpHeaders(response: String): Map<String, String> {
            if (response.length > MaximumSsdpPacketBytes) return emptyMap()
            val first = response.lineSequence().firstOrNull()?.trim().orEmpty()
            if (!first.startsWith("HTTP/1.1 200", ignoreCase = true) &&
                !first.startsWith("NOTIFY * HTTP/1.1", ignoreCase = true)
            ) return emptyMap()
            return response.lineSequence().drop(1).take(64).mapNotNull { line ->
                val separator = line.indexOf(':')
                if (separator !in 1..64) return@mapNotNull null
                val key = line.substring(0, separator).trim().lowercase(Locale.US)
                val value = sanitizeText(line.substring(separator + 1)) ?: return@mapNotNull null
                key to value
            }.toMap()
        }

        internal fun buildNetbiosNodeStatusQuery(transactionId: Int): ByteArray {
            val packet = ByteArray(50)
            packet[0] = (transactionId shr 8).toByte()
            packet[1] = transactionId.toByte()
            packet[5] = 1
            packet[12] = 0x20
            val name = ByteArray(16)
            name[0] = '*'.code.toByte()
            for (index in name.indices) {
                val value = name[index].toInt() and 0xff
                packet[13 + index * 2] = ('A'.code + (value shr 4)).toByte()
                packet[14 + index * 2] = ('A'.code + (value and 0x0f)).toByte()
            }
            packet[45] = 0
            packet[46] = 0
            packet[47] = 0x21
            packet[48] = 0
            packet[49] = 1
            return packet
        }

        internal fun parseNetbiosNodeStatus(packet: ByteArray, transactionId: Int): NetbiosRecord? {
            if (packet.size < 32 || readUnsignedShort(packet, 0) != transactionId) return null
            if ((packet[2].toInt() and 0x80) == 0) return null
            var offset = 12
            val questionCount = readUnsignedShort(packet, 4)
            val answerCount = readUnsignedShort(packet, 6)
            repeat(questionCount) {
                offset = skipDnsName(packet, offset) ?: return null
                if (offset + 4 > packet.size) return null
                offset += 4
            }
            repeat(answerCount) {
                offset = skipDnsName(packet, offset) ?: return null
                if (offset + 10 > packet.size) return null
                val type = readUnsignedShort(packet, offset)
                val dataLength = readUnsignedShort(packet, offset + 8)
                val dataStart = offset + 10
                val dataEnd = dataStart + dataLength
                if (dataEnd > packet.size) return null
                if (type == 0x21 && dataLength >= 1) {
                    val count = packet[dataStart].toInt() and 0xff
                    if (count > 64 || dataStart + 1 + count * 18 > dataEnd) return null
                    var hostname: String? = null
                    repeat(count) { index ->
                        val nameOffset = dataStart + 1 + index * 18
                        val name = String(packet, nameOffset, 15, StandardCharsets.US_ASCII).trim()
                        val suffix = packet[nameOffset + 15].toInt() and 0xff
                        val flags = readUnsignedShort(packet, nameOffset + 16)
                        val group = flags and 0x8000 != 0
                        if (hostname == null && !group && suffix in listOf(0x00, 0x20) && name.isNotBlank()) {
                            hostname = sanitizeText(name)
                        }
                    }
                    val macOffset = dataStart + 1 + count * 18
                    val mac = if (macOffset + 6 <= dataEnd) {
                        packet.copyOfRange(macOffset, macOffset + 6)
                            .joinToString(":") { "%02x".format(it.toInt() and 0xff) }
                            .takeUnless { it == "00:00:00:00:00:00" }
                    } else null
                    return NetbiosRecord(hostname, mac)
                }
                offset = dataEnd
            }
            return null
        }

        internal fun manufacturerForMac(macAddress: String): String? {
            val normalized = macAddress.replace(":", "").uppercase(Locale.US)
            if (normalized.length != 12 || normalized.any { it !in "0123456789ABCDEF" }) return null
            val firstByte = normalized.substring(0, 2).toInt(16)
            if (firstByte and 0x02 != 0) return "Private/randomized address"
            return OuiManufacturers[normalized.take(6)]
        }

        internal fun estimateDeviceType(
            thisDevice: Boolean,
            gateway: Boolean,
            hostname: String?,
            manufacturer: String?,
            services: Set<String>,
        ): String {
            if (thisDevice) return "This Android device"
            if (gateway) return "Router / gateway"
            val signals = (listOfNotNull(hostname, manufacturer) + services).joinToString(" ").lowercase(Locale.US)
            return when {
                listOf("printer", "_ipp", "9100").any(signals::contains) -> "Printer"
                listOf("chromecast", "googlecast", "airplay", "appletv", "smart tv").any(signals::contains) -> "Media / TV device"
                listOf("nas", "synology", "qnap").any(signals::contains) -> "Network storage"
                listOf("camera", "doorbell").any(signals::contains) -> "Camera / IoT device"
                listOf("android", "iphone", "ipad", "phone").any(signals::contains) -> "Phone / tablet"
                listOf("workstation", "desktop", "macbook", "windows").any(signals::contains) -> "Computer"
                else -> "Unknown device"
            }
        }

        private fun serviceLabel(service: LanServiceObservation): String =
            listOfNotNull(service.name, sanitizeText(service.type)).distinct().joinToString(" - ").take(MaximumTextLength)

        private fun sanitizeText(value: String?): String? = value.orEmpty()
            .filterNot(Char::isISOControl)
            .trim()
            .take(MaximumTextLength)
            .takeIf(String::isNotBlank)

        private fun readUnsignedShort(packet: ByteArray, offset: Int): Int {
            if (offset + 1 >= packet.size) return -1
            return ((packet[offset].toInt() and 0xff) shl 8) or (packet[offset + 1].toInt() and 0xff)
        }

        private fun skipDnsName(packet: ByteArray, start: Int): Int? {
            var offset = start
            var labels = 0
            while (offset < packet.size && labels++ < 128) {
                val length = packet[offset].toInt() and 0xff
                if (length == 0) return offset + 1
                if (length and 0xc0 == 0xc0) return if (offset + 1 < packet.size) offset + 2 else null
                if (length > 63 || offset + 1 + length > packet.size) return null
                offset += 1 + length
            }
            return null
        }
    }
}
