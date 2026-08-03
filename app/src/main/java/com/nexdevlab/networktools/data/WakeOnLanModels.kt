package com.nexdevlab.networktools.data

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.Locale
import java.util.UUID

enum class WakeOnLanMode(val label: String) {
    LOCAL("Local"),
    REMOTE("Remote"),
}

data class WakeMacAddress(val value: String) {
    fun bytes(): ByteArray = value.split(':').map { it.toInt(16).toByte() }.toByteArray()

    companion object {
        fun parse(input: String): WakeMacAddress {
            val compact = input.trim().replace(":", "").replace("-", "")
            require(compact.length == 12 && compact.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                "Enter a 12-digit MAC address"
            }
            val bytes = compact.chunked(2).map { it.toInt(16) }
            require(bytes.any { it != 0 } && bytes.any { it != 0xff }) { "Enter a device MAC address" }
            require(bytes.first() and 1 == 0) { "MAC address must identify one device" }
            return WakeMacAddress(bytes.joinToString(":") { "%02X".format(Locale.US, it) })
        }
    }
}

data class WakeOnLanDeviceDraft(
    val name: String,
    val macAddress: String,
    val targetAddress: String,
    val broadcastAddress: String,
    val udpPort: String,
    val mode: WakeOnLanMode,
) {
    fun validate(
        existingId: String? = null,
        createdAtMillis: Long = System.currentTimeMillis(),
        nowMillis: Long = System.currentTimeMillis(),
    ): WakeOnLanDevice {
        val safeName = name.trim()
        require(safeName.isNotEmpty()) { "Enter a friendly device name" }
        require(safeName.length <= MaximumNameLength && safeName.none(Char::isISOControl)) {
            "Device name must be 1-$MaximumNameLength characters"
        }
        val mac = WakeMacAddress.parse(macAddress)
        val port = udpPort.trim().toIntOrNull()?.takeIf { it in 1..65535 }
            ?: throw IllegalArgumentException("Enter a UDP port from 1-65535")
        val target = when (mode) {
            WakeOnLanMode.LOCAL -> normalizePrivateIpv4(targetAddress, "Enter the device's private IPv4 address")
            WakeOnLanMode.REMOTE -> PingRepository.normalizeTarget(targetAddress)
        }
        val broadcast = when (mode) {
            WakeOnLanMode.LOCAL -> normalizeBroadcast(broadcastAddress)
            WakeOnLanMode.REMOTE -> ""
        }
        require(createdAtMillis > 0 && nowMillis >= createdAtMillis) { "Device timestamps are invalid" }
        val safeId = existingId?.also {
            require(runCatching { UUID.fromString(it) }.isSuccess) { "Device identifier is invalid" }
        } ?: UUID.randomUUID().toString()
        return WakeOnLanDevice(
            id = safeId,
            name = safeName,
            macAddress = mac.value,
            targetAddress = target,
            broadcastAddress = broadcast,
            udpPort = port,
            mode = mode,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = nowMillis,
        )
    }

    companion object {
        const val MaximumNameLength = 48
        const val MaximumTargetLength = 253

        internal fun normalizePrivateIpv4(input: String, message: String): String {
            val value = runCatching { IpCalculator.parseIpv4(input.trim()) }.getOrNull()
                ?: throw IllegalArgumentException(message)
            require(LanSubnet.isPrivate(value.value.toLong())) { message }
            return value.format()
        }

        internal fun normalizeBroadcast(input: String): String {
            val normalized = if (input.trim() == "255.255.255.255") {
                input.trim()
            } else {
                normalizePrivateIpv4(input, "Enter a private IPv4 broadcast address")
            }
            return normalized
        }
    }
}

data class WakeOnLanDevice(
    val id: String,
    val name: String,
    val macAddress: String,
    val targetAddress: String,
    val broadcastAddress: String,
    val udpPort: Int,
    val mode: WakeOnLanMode,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    val destination: String get() = if (mode == WakeOnLanMode.LOCAL) broadcastAddress else targetAddress

    fun validated(): WakeOnLanDevice = WakeOnLanDeviceDraft(
        name = name,
        macAddress = macAddress,
        targetAddress = targetAddress,
        broadcastAddress = broadcastAddress,
        udpPort = udpPort.toString(),
        mode = mode,
    ).validate(id, createdAtMillis, updatedAtMillis)
}

data class WakeLocalNetwork(
    val localAddress: String,
    val broadcastAddress: String,
    val prefixLength: Int,
    val interfaceName: String?,
)

data class PreparedWakeOnLanSend(
    val device: WakeOnLanDevice,
    val resolvedDestination: InetAddress,
    val localNetwork: WakeLocalNetwork?,
)

data class WakeOnLanResult(
    val deviceName: String,
    val macAddress: String,
    val destination: String,
    val resolvedDestination: String,
    val udpPort: Int,
    val packetSize: Int,
    val sentAtMillis: Long,
) {
    fun asPlainText(): String = buildList {
        add("Wake-on-LAN packet sent")
        add("Device: $deviceName")
        add("MAC: $macAddress")
        add("Destination: $destination ($resolvedDestination)")
        add("UDP port: $udpPort")
        add("Packet: $packetSize bytes")
        add("This confirms packet transmission, not that the device woke.")
    }.joinToString("\n")
}

data class WakeOnLanUiState(
    val devices: List<WakeOnLanDevice> = emptyList(),
    val localNetwork: WakeLocalNetwork? = null,
    val localNetworkError: String? = null,
    val sending: Boolean = false,
    val permissionRequired: Boolean = false,
    val activeDevice: WakeOnLanDevice? = null,
    val result: WakeOnLanResult? = null,
    val error: String? = null,
)

object WakeMagicPacket {
    const val Size = 102

    fun build(macAddress: String): ByteArray {
        val mac = WakeMacAddress.parse(macAddress).bytes()
        return ByteArray(Size).also { packet ->
            repeat(6) { packet[it] = 0xff.toByte() }
            repeat(16) { repetition -> mac.copyInto(packet, 6 + repetition * mac.size) }
        }
    }
}

internal fun InetAddress.isPublicUnicast(): Boolean {
    if (isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress || isSiteLocalAddress || isMulticastAddress) {
        return false
    }
    if (this is Inet4Address) {
        val value = address.fold(0L) { result, byte -> (result shl 8) or (byte.toInt() and 0xff).toLong() }
        fun inRange(network: Long, prefix: Int): Boolean {
            val mask = (0xffffffffL shl (32 - prefix)) and 0xffffffffL
            return value and mask == network and mask
        }
        return !inRange(0x00000000, 8) && !inRange(0x64400000, 10) &&
            !inRange(0xc0000000, 24) && !inRange(0xc0000200, 24) &&
            !inRange(0xc0586300, 24) && !inRange(0xc6120000, 15) &&
            !inRange(0xc6336400, 24) && !inRange(0xcb007100, 24) &&
            !inRange(0xf0000000, 4)
    }
    if (this is Inet6Address) {
        val first = address.first().toInt() and 0xff
        val globalUnicast = first and 0xe0 == 0x20
        val documentation = address[0].toInt() and 0xff == 0x20 && address[1].toInt() and 0xff == 0x01 &&
            address[2].toInt() and 0xff == 0x0d && address[3].toInt() and 0xff == 0xb8
        return globalUnicast && !documentation
    }
    return false
}
