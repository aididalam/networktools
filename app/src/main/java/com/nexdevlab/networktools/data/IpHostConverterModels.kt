package com.nexdevlab.networktools.data

import java.util.Locale

enum class IpHostConversionMode(val label: String) {
    HOST_TO_IP("Host to IP"),
    IP_TO_HOST("IP to host"),
}

enum class IpHostAddressMode(val label: String) {
    BOTH("Both"),
    IPV4("IPv4"),
    IPV6("IPv6"),
}

data class IpHostConversionConfig(
    val mode: IpHostConversionMode,
    val target: String,
    val addressMode: IpHostAddressMode = IpHostAddressMode.BOTH,
    val resolverMode: DnsResolverMode = DnsResolverMode.SYSTEM,
    val customResolver: String = DnsLookupRepository.PublicResolvers.first().address,
    val timeoutMillis: Int = DnsQueryConfig.DefaultTimeoutMillis,
) {
    fun normalizedTarget(): String {
        require(target.length <= MaximumTargetLength) { "Input is too long" }
        return when (mode) {
            IpHostConversionMode.HOST_TO_IP -> DnsQueryConfig.normalizeName(target, DnsRecordType.A)
            IpHostConversionMode.IP_TO_HOST -> normalizeNumericIp(target)
        }
    }

    fun recordTypes(): List<DnsRecordType> = when (mode) {
        IpHostConversionMode.IP_TO_HOST -> listOf(DnsRecordType.PTR)
        IpHostConversionMode.HOST_TO_IP -> when (addressMode) {
            IpHostAddressMode.BOTH -> listOf(DnsRecordType.A, DnsRecordType.AAAA)
            IpHostAddressMode.IPV4 -> listOf(DnsRecordType.A)
            IpHostAddressMode.IPV6 -> listOf(DnsRecordType.AAAA)
        }
    }

    fun validate() {
        normalizedTarget()
        require(timeoutMillis in DnsQueryConfig.AllowedTimeouts) { "Choose a supported timeout" }
        if (resolverMode == DnsResolverMode.CUSTOM) DnsQueryConfig.parseResolverAddress(customResolver)
    }

    companion object {
        const val MaximumTargetLength = 253

        internal fun normalizeNumericIp(input: String): String {
            val value = input.trim()
            require(value.isNotEmpty()) { "Enter an IPv4 or IPv6 address" }
            require(value.length <= 45 && '/' !in value && '%' !in value && value.none(Char::isWhitespace)) {
                "Enter one numeric IP address without a prefix or zone identifier"
            }
            return try {
                when {
                    ':' in value -> IpCalculator.parseIpv6(value).format()
                    '.' in value && value.all { it.isDigit() || it == '.' } -> IpCalculator.parseIpv4(value).format()
                    else -> throw IllegalArgumentException("Enter a numeric IPv4 or IPv6 address")
                }
            } catch (failure: IllegalArgumentException) {
                throw IllegalArgumentException(failure.message ?: "Enter a valid IPv4 or IPv6 address")
            }
        }
    }
}

data class PreparedIpHostConversion(
    val config: IpHostConversionConfig,
    val normalizedTarget: String,
    val queries: List<PreparedDnsQuery>,
) {
    val localNetworkResolver: Boolean get() = queries.any(PreparedDnsQuery::localNetworkResolver)
}

data class IpHostConversionAnswer(
    val type: String,
    val value: String,
    val ttlSeconds: Long,
)

data class IpHostConversionResult(
    val mode: IpHostConversionMode,
    val normalizedTarget: String,
    val resolver: DnsResolverTarget,
    val answers: List<IpHostConversionAnswer>,
    val queryResults: List<DnsLookupResult>,
    val completedAtMillis: Long,
) {
    val successful: Boolean get() = answers.isNotEmpty()
    val statusText: String get() = if (successful) {
        "${answers.size} ${if (answers.size == 1) "result" else "results"}"
    } else {
        queryResults.map(DnsLookupResult::status).distinct().joinToString(" / ") { it.label }
            .ifEmpty { "No results" }
    }
    val privateDnsActive: Boolean get() = queryResults.any(DnsLookupResult::privateDnsActive)

    fun asPlainText(): String = buildList {
        add("IP and host conversion")
        add("Mode: ${mode.label}")
        add("Input: $normalizedTarget")
        add("Resolver: ${resolver.label} (${resolver.address})")
        add("Status: $statusText")
        answers.forEach { answer -> add("${answer.type}: ${answer.value} (TTL ${answer.ttlSeconds}s)") }
    }.joinToString("\n")

    companion object {
        const val MaximumAnswers = 32

        fun from(
            prepared: PreparedIpHostConversion,
            results: List<DnsLookupResult>,
            completedAtMillis: Long = System.currentTimeMillis(),
        ): IpHostConversionResult {
            require(results.isNotEmpty()) { "Conversion did not run a DNS query" }
            val acceptedTypes = prepared.config.recordTypes().map(DnsRecordType::label).toSet()
            val answers = results.flatMap(DnsLookupResult::answerRecords)
                .filter { it.type.uppercase(Locale.US) in acceptedTypes }
                .map { IpHostConversionAnswer(it.type.uppercase(Locale.US), it.value.removeSuffix("."), it.ttlSeconds) }
                .distinctBy { "${it.type}:${it.value.lowercase(Locale.US)}" }
                .take(MaximumAnswers)
            return IpHostConversionResult(
                mode = prepared.config.mode,
                normalizedTarget = prepared.normalizedTarget,
                resolver = results.first().resolver,
                answers = answers,
                queryResults = results,
                completedAtMillis = completedAtMillis,
            )
        }
    }
}

data class IpHostConverterUiState(
    val running: Boolean = false,
    val permissionRequired: Boolean = false,
    val activeConversion: PreparedIpHostConversion? = null,
    val result: IpHostConversionResult? = null,
    val error: String? = null,
)
