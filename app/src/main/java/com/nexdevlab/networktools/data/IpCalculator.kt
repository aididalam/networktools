package com.nexdevlab.networktools.data

import java.math.BigInteger
import java.net.Inet6Address
import java.net.InetAddress

enum class IpAddressFamily(val label: String, val bitCount: Int, val defaultPrefix: Int) {
    IPV4("IPv4", 32, 24),
    IPV6("IPv6", 128, 64),
}

enum class IpPrefixInputMode(val label: String) {
    PREFIX("Prefix"),
    SUBNET_MASK("Subnet mask"),
}

data class IpCalculationInput(
    val family: IpAddressFamily,
    val address: String,
    val prefixInputMode: IpPrefixInputMode = IpPrefixInputMode.PREFIX,
    val prefix: String = family.defaultPrefix.toString(),
    val subnetMask: String = "255.255.255.0",
    val membershipAddress: String = "",
)

data class IpCalculationResult(
    val family: IpAddressFamily,
    val inputAddress: String,
    val normalizedAddress: String,
    val prefixLength: Int,
    val cidr: String,
    val networkAddress: String,
    val subnetMask: String,
    val broadcastAddress: String?,
    val firstHost: String,
    val lastHost: String,
    val totalAddresses: BigInteger,
    val usableHosts: BigInteger,
    val membershipAddress: String?,
    val membershipMatches: Boolean?,
) {
    val hostRange: String get() = "$firstHost - $lastHost"

    fun asPlainText(): String = buildList {
        add("IP calculation")
        add("Family: ${family.label}")
        add("Address: $normalizedAddress")
        add("CIDR: $cidr")
        add("Network: $networkAddress")
        add("Subnet mask: $subnetMask")
        broadcastAddress?.let { add("Broadcast: $it") }
        add("Prefix length: /$prefixLength")
        add("First host: $firstHost")
        add("Last host: $lastHost")
        add("Host range: $hostRange")
        add("Total addresses: $totalAddresses")
        add("Usable hosts: $usableHosts")
        membershipAddress?.let { candidate ->
            add("Membership: $candidate is ${if (membershipMatches == true) "inside" else "outside"} $cidr")
        }
    }.joinToString("\n")
}

object IpCalculator {
    private val One = BigInteger.ONE
    private val Two = BigInteger.valueOf(2)

    fun calculate(input: IpCalculationInput): IpCalculationResult {
        require(input.address.length <= 128) { "IP address input is too long" }
        require(input.membershipAddress.length <= 128) { "Membership address input is too long" }
        val addressParts = input.address.trim().split('/')
        require(addressParts.size in 1..2 && addressParts[0].isNotEmpty()) {
            "Enter an IP address, optionally followed by a CIDR prefix"
        }
        val address = parseAddress(input.family, addressParts[0])
        val embeddedPrefix = addressParts.getOrNull(1)?.parsePrefix(input.family)
        val selectedPrefix = when {
            embeddedPrefix != null -> embeddedPrefix
            input.family == IpAddressFamily.IPV4 && input.prefixInputMode == IpPrefixInputMode.SUBNET_MASK ->
                prefixFromIpv4Mask(input.subnetMask)
            else -> input.prefix.parsePrefix(input.family)
        }
        if (embeddedPrefix != null && input.family == IpAddressFamily.IPV4 &&
            input.prefixInputMode == IpPrefixInputMode.SUBNET_MASK) {
            require(prefixFromIpv4Mask(input.subnetMask) == embeddedPrefix) {
                "CIDR prefix and subnet mask do not match"
            }
        }

        val bits = input.family.bitCount
        val hostBits = bits - selectedPrefix
        val allOnes = One.shiftLeft(bits).subtract(One)
        val hostMask = if (hostBits == 0) BigInteger.ZERO else One.shiftLeft(hostBits).subtract(One)
        val networkMask = allOnes.xor(hostMask)
        val network = address.value.and(networkMask)
        val end = network.or(hostMask)
        val total = One.shiftLeft(hostBits)
        val first: BigInteger
        val last: BigInteger
        val usable: BigInteger
        if (input.family == IpAddressFamily.IPV4) {
            when {
                selectedPrefix <= 30 -> {
                    first = network.add(One)
                    last = end.subtract(One)
                    usable = total.subtract(Two)
                }
                selectedPrefix == 31 -> {
                    first = network
                    last = end
                    usable = Two
                }
                else -> {
                    first = network
                    last = network
                    usable = One
                }
            }
        } else {
            first = network
            last = end
            usable = total
        }

        val candidate = input.membershipAddress.trim().takeIf(String::isNotEmpty)?.let { value ->
            require('/' !in value) { "Membership check requires one IP address without a prefix" }
            parseAddress(input.family, value)
        }
        return IpCalculationResult(
            family = input.family,
            inputAddress = input.address.trim(),
            normalizedAddress = address.format(),
            prefixLength = selectedPrefix,
            cidr = "${format(input.family, network)}/$selectedPrefix",
            networkAddress = format(input.family, network),
            subnetMask = format(input.family, networkMask),
            broadcastAddress = if (input.family == IpAddressFamily.IPV4) format(input.family, end) else null,
            firstHost = format(input.family, first),
            lastHost = format(input.family, last),
            totalAddresses = total,
            usableHosts = usable,
            membershipAddress = candidate?.format(),
            membershipMatches = candidate?.let { it.value.and(networkMask) == network },
        )
    }

    fun prefixFromIpv4Mask(input: String): Int {
        val mask = parseIpv4(input.trim()).value.toLong()
        var prefix = 0
        var foundZero = false
        for (bit in 31 downTo 0) {
            val set = mask and (1L shl bit) != 0L
            if (set) {
                require(!foundZero) { "Subnet mask must contain contiguous one bits" }
                prefix++
            } else foundZero = true
        }
        return prefix
    }

    internal fun parseIpv4(input: String): AddressValue {
        val parts = input.split('.')
        require(parts.size == 4) { "Enter a valid dotted-decimal IPv4 address" }
        var value = BigInteger.ZERO
        parts.forEach { part ->
            require(part.isNotEmpty() && part.length <= 3 && part.all(Char::isDigit)) {
                "Enter a valid dotted-decimal IPv4 address"
            }
            require(part == "0" || !part.startsWith('0')) { "IPv4 octets must not contain leading zeroes" }
            val octet = part.toIntOrNull()?.takeIf { it in 0..255 }
                ?: throw IllegalArgumentException("Every IPv4 octet must be from 0-255")
            value = value.shiftLeft(8).or(BigInteger.valueOf(octet.toLong()))
        }
        return AddressValue(IpAddressFamily.IPV4, value)
    }

    internal fun parseIpv6(input: String): AddressValue {
        val value = input.trim()
        require(value.isNotEmpty() && value.length <= 45 && ':' in value && '%' !in value &&
            value.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' || it == ':' || it == '.' }) {
            "Enter a valid IPv6 address without a zone identifier"
        }
        val address = try {
            InetAddress.getByName(value)
        } catch (_: Exception) {
            throw IllegalArgumentException("Enter a valid IPv6 address")
        }
        require(address is Inet6Address) { "Enter an IPv6 address" }
        return AddressValue(IpAddressFamily.IPV6, BigInteger(1, address.address))
    }

    internal fun formatIpv6(value: BigInteger): String {
        val raw = value.toByteArray()
        val bytes = ByteArray(16)
        val sourceStart = (raw.size - 16).coerceAtLeast(0)
        val length = (raw.size - sourceStart).coerceAtMost(16)
        raw.copyInto(bytes, 16 - length, sourceStart, sourceStart + length)
        val groups = IntArray(8) { index ->
            ((bytes[index * 2].toInt() and 0xff) shl 8) or (bytes[index * 2 + 1].toInt() and 0xff)
        }
        var bestStart = -1
        var bestLength = 0
        var index = 0
        while (index < groups.size) {
            if (groups[index] != 0) {
                index++
                continue
            }
            val start = index
            while (index < groups.size && groups[index] == 0) index++
            val lengthFound = index - start
            if (lengthFound >= 2 && lengthFound > bestLength) {
                bestStart = start
                bestLength = lengthFound
            }
        }
        if (bestStart < 0) return groups.joinToString(":") { it.toString(16) }
        val left = groups.take(bestStart).joinToString(":") { it.toString(16) }
        val right = groups.drop(bestStart + bestLength).joinToString(":") { it.toString(16) }
        return when {
            left.isEmpty() && right.isEmpty() -> "::"
            left.isEmpty() -> "::$right"
            right.isEmpty() -> "$left::"
            else -> "$left::$right"
        }
    }

    private fun parseAddress(family: IpAddressFamily, input: String): AddressValue = when (family) {
        IpAddressFamily.IPV4 -> parseIpv4(input.trim())
        IpAddressFamily.IPV6 -> parseIpv6(input.trim())
    }

    private fun String.parsePrefix(family: IpAddressFamily): Int {
        val value = trim().removePrefix("/").toIntOrNull()
            ?: throw IllegalArgumentException("Enter a numeric prefix length")
        require(value in 0..family.bitCount) { "${family.label} prefix must be from 0-${family.bitCount}" }
        return value
    }

    private fun format(family: IpAddressFamily, value: BigInteger): String = AddressValue(family, value).format()

    internal data class AddressValue(val family: IpAddressFamily, val value: BigInteger) {
        fun format(): String = when (family) {
            IpAddressFamily.IPV4 -> listOf(24, 16, 8, 0).joinToString(".") { shift ->
                value.shiftRight(shift).and(BigInteger.valueOf(255)).toString()
            }
            IpAddressFamily.IPV6 -> formatIpv6(value)
        }
    }
}
