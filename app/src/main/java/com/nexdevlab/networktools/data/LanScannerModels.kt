package com.nexdevlab.networktools.data

enum class LanScanMode(val label: String) {
    FAST("Fast"),
    DEEP("Deep"),
}

enum class LanDiscoveryProtocol(val label: String) {
    ARP("ARP"),
    PING("Ping"),
    MDNS("mDNS"),
    BONJOUR("Bonjour"),
    SSDP("SSDP"),
    UPNP("UPnP"),
    SNMP("SNMP service"),
    NETBIOS("NetBIOS"),
}

data class LanSubnet(val network: Long, val prefixLength: Int) {
    init {
        require(prefixLength in PrefixRange) { "Subnet prefix must be /24-/30" }
        require(network == networkFor(network, prefixLength)) { "Use a valid subnet address" }
        require(isPrivate(network)) { "Only private LAN subnets can be scanned" }
    }

    val broadcast: Long get() = network or hostMask(prefixLength)
    val hostCount: Int get() = (broadcast - network - 1).toInt().coerceAtLeast(0)
    val display: String get() = "${formatIpv4(network)}/$prefixLength"

    fun contains(address: Long): Boolean = networkFor(address, prefixLength) == network

    fun hostAddresses(): List<Long> = ((network + 1) until broadcast).toList()

    companion object {
        val PrefixRange = 24..30
        private val SegmentPattern = Regex("^(0|[1-9][0-9]{0,2})$")

        fun parse(value: String): LanSubnet {
            val trimmed = value.trim()
            require(trimmed.length <= 32) { "Subnet is too long" }
            val parts = trimmed.split('/')
            require(parts.size == 2) { "Enter a subnet such as 192.168.1.0/24" }
            val address = parseIpv4(parts[0]) ?: throw IllegalArgumentException("Enter a valid IPv4 subnet")
            val prefix = parts[1].toIntOrNull()
                ?: throw IllegalArgumentException("Enter a valid prefix length")
            require(prefix in PrefixRange) { "Subnet prefix must be /24-/30" }
            require(isPrivate(address)) { "Only private LAN subnets can be scanned" }
            return LanSubnet(networkFor(address, prefix), prefix)
        }

        fun fromAddress(address: Long, prefixLength: Int): LanSubnet {
            require(isPrivate(address)) { "Only private LAN subnets can be scanned" }
            val boundedPrefix = prefixLength.coerceIn(PrefixRange)
            return LanSubnet(networkFor(address, boundedPrefix), boundedPrefix)
        }

        fun parseIpv4(value: String): Long? {
            val parts = value.split('.')
            if (parts.size != 4) return null
            var result = 0L
            for (part in parts) {
                if (!SegmentPattern.matches(part)) return null
                val octet = part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
                result = (result shl 8) or octet.toLong()
            }
            return result
        }

        fun formatIpv4(address: Long): String = listOf(24, 16, 8, 0)
            .joinToString(".") { shift -> ((address shr shift) and 0xff).toString() }

        fun isPrivate(address: Long): Boolean {
            val first = (address shr 24) and 0xff
            val second = (address shr 16) and 0xff
            return first == 10L || (first == 172L && second in 16L..31L) ||
                (first == 192L && second == 168L)
        }

        private fun networkFor(address: Long, prefixLength: Int): Long = address and hostMask(prefixLength).inv() and 0xffffffffL

        private fun hostMask(prefixLength: Int): Long = (1L shl (32 - prefixLength)) - 1L
    }
}

data class LanScanConfig(
    val subnet: LanSubnet,
    val mode: LanScanMode,
    val authorized: Boolean,
) {
    fun validate() {
        require(authorized) { "Confirm that you are authorized to scan this network" }
        require(subnet.hostCount in 2..254) { "Subnet must contain 2-254 usable hosts" }
    }
}

data class LanDevice(
    val ipAddress: String,
    val hostname: String? = null,
    val macAddress: String? = null,
    val manufacturer: String? = null,
    val deviceType: String = "Unknown device",
    val protocols: Set<LanDiscoveryProtocol> = emptySet(),
    val services: Set<String> = emptySet(),
    val thisDevice: Boolean = false,
    val gateway: Boolean = false,
    val lastSeenMillis: Long,
) {
    val displayName: String
        get() = when {
            thisDevice -> "This device"
            gateway -> hostname ?: "Router / gateway"
            !hostname.isNullOrBlank() -> hostname
            else -> deviceType
        }
    val sortAddress: Long get() = LanSubnet.parseIpv4(ipAddress) ?: Long.MAX_VALUE
}

data class LanScanResult(
    val subnet: String,
    val mode: LanScanMode,
    val devices: List<LanDevice>,
    val scannedHosts: Int,
    val stopped: Boolean,
    val completedAtMillis: Long,
) {
    fun asPlainText(): String = buildList {
        add("LAN scan result")
        add("Subnet: $subnet")
        add("Mode: ${mode.label}")
        add("Status: ${if (stopped) "Stopped" else "Completed"}")
        add("Devices: ${devices.size}")
        devices.forEach { device ->
            add("${device.displayName} - ${device.ipAddress}")
            device.hostname?.let { add("  Hostname: $it") }
            device.macAddress?.let { add("  MAC: $it") }
            device.manufacturer?.let { add("  Manufacturer: $it") }
            add("  Type: ${device.deviceType}")
            if (device.protocols.isNotEmpty()) {
                add("  Found by: ${device.protocols.joinToString { it.label }}")
            }
            if (device.services.isNotEmpty()) add("  Services: ${device.services.joinToString()}")
        }
    }.joinToString("\n")
}

data class LanScanHistoryEntry(
    val id: Long,
    val subnet: String,
    val mode: LanScanMode,
    val devices: List<LanDevice>,
    val scannedHosts: Int,
    val stopped: Boolean,
    val completedAtMillis: Long,
)

enum class LanScannerView(val label: String) {
    SCANNER("Scanner"),
    HISTORY("History"),
}

data class LanScannerUiState(
    val scanning: Boolean = false,
    val phase: String = "Ready",
    val scannedHosts: Int = 0,
    val totalHosts: Int = 0,
    val devices: List<LanDevice> = emptyList(),
    val result: LanScanResult? = null,
    val history: List<LanScanHistoryEntry> = emptyList(),
    val detectedSubnet: String? = null,
    val error: String? = null,
)
