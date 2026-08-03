package com.nexdevlab.networktools.data

import java.math.BigInteger

enum class RouterAddressSource(val label: String) {
    DETECTED("Detected"),
    MANUAL("Manual"),
}

enum class RouterProtocol(val label: String, val scheme: String) {
    HTTPS("HTTPS", "https"),
    HTTP("HTTP", "http"),
}

enum class RouterPageAction(val label: String, val description: String) {
    ADMIN("Router administration", "Open the router's main administration page"),
    DHCP("DHCP settings", "Manage addresses assigned by the router"),
    PORT_FORWARDING("Port forwarding", "Manage inbound port-forwarding rules"),
    CONNECTED_DEVICES("Connected devices", "View devices reported by the router"),
}

data class RouterAddress(
    val value: String,
    val source: RouterAddressSource,
) {
    fun browserUrl(protocol: RouterProtocol): String {
        val host = if (':' in value) "[${value.replace("%", "%25")}]" else value
        return "${protocol.scheme}://$host"
    }

    companion object {
        const val MaximumLength = 80

        fun parse(input: String, source: RouterAddressSource): RouterAddress {
            var candidate = input.trim()
            require(candidate.isNotEmpty()) { "Enter a router IP address" }
            require(candidate.length <= MaximumLength) { "Router address is too long" }
            require(!candidate.contains(Regex("[\\s/@?#\\\\]")) && "://" !in candidate) {
                "Enter one numeric router IP address without a URL, path, port, or credentials"
            }
            if (candidate.startsWith('[') || candidate.endsWith(']')) {
                require(candidate.startsWith('[') && candidate.endsWith(']')) {
                    "Enter a valid numeric router IP address"
                }
                candidate = candidate.substring(1, candidate.lastIndex)
            }

            val normalized = if (':' in candidate) parseIpv6(candidate) else parseIpv4(candidate)
            return RouterAddress(normalized, source)
        }

        private fun parseIpv4(candidate: String): String {
            val parsed = IpCalculator.parseIpv4(candidate)
            val value = parsed.value.toLong()
            val first = value ushr 24
            val second = value ushr 16 and 0xff
            val local = first == 10L ||
                (first == 172L && second in 16L..31L) ||
                (first == 192L && second == 168L) ||
                (first == 169L && second == 254L) ||
                (first == 100L && second in 64L..127L)
            require(local) { "Router address must be a private or link-local IP address" }
            return parsed.format()
        }

        private fun parseIpv6(candidate: String): String {
            val parts = candidate.split('%')
            require(parts.size <= 2 && parts[0].isNotEmpty()) { "Enter a valid numeric router IP address" }
            val zone = parts.getOrNull(1)?.also {
                require(it.matches(Regex("[A-Za-z0-9_.-]{1,32}"))) { "IPv6 zone identifier is invalid" }
            }
            val parsed = IpCalculator.parseIpv6(parts[0])
            val firstByte = parsed.value.shiftRight(120).and(BigInteger.valueOf(0xff)).toInt()
            val firstWord = parsed.value.shiftRight(112).and(BigInteger.valueOf(0xffff)).toInt()
            val local = firstByte and 0xfe == 0xfc || firstWord and 0xffc0 == 0xfe80
            require(local) { "Router address must be a private or link-local IP address" }
            return parsed.format() + zone?.let { "%$it" }.orEmpty()
        }
    }
}

data class DetectedRouter(
    val address: RouterAddress,
    val interfaceName: String?,
    val connectionLabel: String,
)

data class RouterSetupUiState(
    val refreshing: Boolean = false,
    val detectedRouter: DetectedRouter? = null,
    val detectionError: String? = null,
)
