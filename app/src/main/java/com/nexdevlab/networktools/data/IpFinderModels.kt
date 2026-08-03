package com.nexdevlab.networktools.data

import java.net.InetAddress

enum class IpFinderMode(val label: String) {
    PUBLIC_IP("Public IP"),
    TARGET("Domain or IP"),
}

data class IpFinderConfig(
    val mode: IpFinderMode,
    val target: String = "",
    val resolverMode: DnsResolverMode = DnsResolverMode.SYSTEM,
    val customResolver: String = DnsLookupRepository.PublicResolvers.first().address,
    val timeoutMillis: Int = DnsQueryConfig.DefaultTimeoutMillis,
) {
    fun normalizedTarget(): String? {
        if (mode == IpFinderMode.PUBLIC_IP) return null
        require(target.length <= MaximumTargetLength) { "Target is too long" }
        val value = target.trim()
        require(value.isNotEmpty()) { "Enter a domain or public IP address" }
        require(value.none(Char::isWhitespace) &&
            listOf("://", "/", "@", "?", "#", ";").none(value::contains)) {
            "Enter a domain or numeric public IP without a URL, path, or command characters"
        }
        return when {
            ':' in value || value.all { it.isDigit() || it == '.' } ->
                IpHostConversionConfig.normalizeNumericIp(value)
            else -> DnsQueryConfig.normalizeName(value, DnsRecordType.A)
        }
    }

    fun validate() {
        normalizedTarget()
        require(timeoutMillis in DnsQueryConfig.AllowedTimeouts) { "Choose a supported timeout" }
        if (resolverMode == DnsResolverMode.CUSTOM) DnsQueryConfig.parseResolverAddress(customResolver)
    }

    companion object {
        const val MaximumTargetLength = 253
    }
}

data class PreparedIpFinderLookup(
    val config: IpFinderConfig,
    val normalizedTarget: String?,
    val resolverProbe: PreparedDnsQuery,
    val domainQueries: List<PreparedDnsQuery>,
) {
    val localNetworkResolver: Boolean get() = resolverProbe.localNetworkResolver
}

data class IpFinderRegistration(
    val organization: String?,
    val networkName: String?,
    val registryUrl: String,
)

data class IpFinderAsn(
    val numbers: List<String>,
    val routedPrefix: String?,
    val registry: String?,
)

data class IpFinderResult(
    val mode: IpFinderMode,
    val query: String,
    val resolvedAddresses: List<String>,
    val selectedAddress: String,
    val resolver: DnsResolverTarget,
    val registration: IpFinderRegistration?,
    val origin: IpFinderAsn?,
    val notices: List<String>,
    val completedAtMillis: Long,
) {
    val organization: String? get() = registration?.organization ?: registration?.networkName
    val asn: String? get() = origin?.numbers?.joinToString(", ")

    fun asPlainText(): String = buildList {
        add("IP Finder")
        add("Query: $query")
        add("Selected public IP: $selectedAddress")
        add("DNS resolver: ${resolver.label} (${resolver.address})")
        resolvedAddresses.forEach { add("Resolved address: $it") }
        organization?.let { add("Organization: $it") }
        asn?.let { add("ASN: $it") }
        origin?.routedPrefix?.let { add("Routed prefix: $it") }
        origin?.registry?.let { add("Origin registry: $it") }
        registration?.networkName?.takeIf { it != organization }?.let { add("Network name: $it") }
        notices.forEach { add("Notice: $it") }
    }.joinToString("\n")
}

data class IpFinderUiState(
    val running: Boolean = false,
    val permissionRequired: Boolean = false,
    val activeLookup: PreparedIpFinderLookup? = null,
    val result: IpFinderResult? = null,
    val error: String? = null,
)

internal fun String.isNumericIpLiteral(): Boolean = runCatching {
    val value = IpHostConversionConfig.normalizeNumericIp(this)
    InetAddress.getByName(value)
}.isSuccess
