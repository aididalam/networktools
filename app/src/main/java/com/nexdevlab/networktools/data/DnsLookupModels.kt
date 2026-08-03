package com.nexdevlab.networktools.data

import java.net.IDN
import java.net.InetAddress
import java.util.Locale
import org.xbill.DNS.ReverseMap
import org.xbill.DNS.Type

enum class DnsRecordType(val label: String, val code: Int, val description: String) {
    A("A", Type.A, "IPv4 address"),
    AAAA("AAAA", Type.AAAA, "IPv6 address"),
    MX("MX", Type.MX, "Mail exchange"),
    NS("NS", Type.NS, "Name server"),
    TXT("TXT", Type.TXT, "Text record"),
    CNAME("CNAME", Type.CNAME, "Canonical name"),
    SOA("SOA", Type.SOA, "Zone authority"),
    PTR("PTR", Type.PTR, "Reverse lookup"),
    SRV("SRV", Type.SRV, "Service location"),
    CAA("CAA", Type.CAA, "Certificate authority"),
}

enum class DnsResolverMode(val label: String) {
    SYSTEM("System"),
    CUSTOM("Custom"),
}

enum class DnsResponseStatus(val label: String) {
    SUCCESS("Success"),
    NO_RECORDS("No records"),
    NXDOMAIN("Name not found"),
    REFUSED("Refused"),
    SERVER_FAILURE("Server failure"),
    TIMED_OUT("Timed out"),
    NETWORK_ERROR("Network error"),
    MALFORMED("Malformed response"),
}

enum class DnsSection(val label: String) {
    ANSWER("Answer"),
    AUTHORITY("Authority"),
    ADDITIONAL("Additional"),
}

data class DnsQueryConfig(
    val name: String,
    val recordType: DnsRecordType,
    val resolverMode: DnsResolverMode = DnsResolverMode.SYSTEM,
    val customResolver: String = DnsLookupRepository.PublicResolvers.first().address,
    val timeoutMillis: Int = DefaultTimeoutMillis,
) {
    fun validate() {
        normalizeName(name, recordType)
        require(timeoutMillis in AllowedTimeouts) { "Choose a supported timeout" }
        if (resolverMode == DnsResolverMode.CUSTOM) parseResolverAddress(customResolver)
    }

    companion object {
        const val DefaultTimeoutMillis = 2_000
        val AllowedTimeouts = setOf(1_000, 2_000, 3_000)

        fun normalizeName(input: String, type: DnsRecordType): String {
            val trimmed = input.trim().removeSuffix(".")
            require(trimmed.isNotEmpty()) { "Enter a domain name or DNS name" }
            require(trimmed.length <= 253) { "DNS name is too long" }
            require(trimmed.none(Char::isWhitespace) &&
                listOf("://", "/", "@", "?", "#", ";").none(trimmed::contains)) {
                "Enter a DNS name without a URL, path, or command characters"
            }
            if (type == DnsRecordType.PTR && looksLikeIpAddress(trimmed)) {
                return try {
                    ReverseMap.fromAddress(trimmed).toString().removeSuffix(".").lowercase(Locale.US)
                } catch (_: Exception) {
                    throw IllegalArgumentException("Enter a valid IP address or reverse-DNS name")
                }
            }
            val labels = trimmed.split('.')
            require(labels.none(String::isEmpty)) { "DNS name contains an empty label" }
            val asciiLabels = labels.map { label ->
                require(!label.startsWith('-') && !label.endsWith('-')) { "DNS labels cannot start or end with a hyphen" }
                val ascii = if ('_' in label) {
                    require(label.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
                        "DNS name contains an invalid character"
                    }
                    label.lowercase(Locale.US)
                } else {
                    try {
                        IDN.toASCII(label, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.US)
                    } catch (_: IllegalArgumentException) {
                        throw IllegalArgumentException("DNS name contains an invalid label")
                    }
                }
                require(ascii.length in 1..63) { "Each DNS label must contain 1-63 characters" }
                ascii
            }
            val normalized = asciiLabels.joinToString(".")
            require(normalized.length <= 253) { "DNS name is too long" }
            return normalized
        }

        fun parseResolverAddress(input: String): InetAddress {
            val value = input.trim().removePrefix("[").removeSuffix("]")
            require(value.isNotEmpty() && value.length <= 45 && '%' !in value) {
                "Enter a numeric DNS-server address"
            }
            val ipv4 = value.split('.').takeIf { parts ->
                parts.size == 4 && parts.all { part ->
                    part.isNotEmpty() && part.length <= 3 && part.all(Char::isDigit) &&
                        part.toIntOrNull() in 0..255
                }
            }
            val ipv6 = ':' in value && value.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' || it == ':' || it == '.' }
            require(ipv4 != null || ipv6) { "Enter a numeric IPv4 or IPv6 DNS-server address" }
            val address = try {
                InetAddress.getByName(value)
            } catch (_: Exception) {
                throw IllegalArgumentException("Enter a valid DNS-server address")
            }
            require(!address.isAnyLocalAddress && !address.isMulticastAddress) {
                "DNS-server address must be unicast"
            }
            return address
        }

        private fun looksLikeIpAddress(value: String): Boolean =
            value.all { it.isDigit() || it == '.' } ||
                (':' in value && value.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' || it == ':' || it == '.' })
    }
}

data class DnsResolverTarget(
    val label: String,
    val address: String,
    val system: Boolean = false,
)

data class PreparedDnsQuery(
    val config: DnsQueryConfig,
    val normalizedName: String,
    val resolvers: List<DnsResolverTarget>,
    val privateDnsActive: Boolean,
    val privateDnsServerName: String?,
    val localNetworkResolver: Boolean,
)

data class DnsRecordEntry(
    val section: DnsSection,
    val name: String,
    val type: String,
    val value: String,
    val ttlSeconds: Long,
)

data class DnsLookupResult(
    val queryName: String,
    val recordType: DnsRecordType,
    val resolver: DnsResolverTarget,
    val status: DnsResponseStatus,
    val responseCode: String,
    val records: List<DnsRecordEntry>,
    val latencyMillis: Double,
    val usedTcp: Boolean,
    val privateDnsActive: Boolean,
    val privateDnsServerName: String?,
    val completedAtMillis: Long,
    val detail: String? = null,
) {
    val answerRecords: List<DnsRecordEntry> get() = records.filter { it.section == DnsSection.ANSWER }
    val minimumTtl: Long? get() = answerRecords.minOfOrNull(DnsRecordEntry::ttlSeconds)

    fun comparableAnswers(): List<String> = answerRecords
        .map { "${it.type}:${it.value}".lowercase(Locale.US) }
        .sorted()

    fun asPlainText(): String = buildList {
        add("DNS lookup")
        add("Query: $queryName ${recordType.label}")
        add("Resolver: ${resolver.label} (${resolver.address})")
        add("Status: ${status.label} ($responseCode)")
        add("Transport: ${if (usedTcp) "TCP" else "UDP"}")
        add("Latency: ${"%.1f".format(Locale.US, latencyMillis)} ms")
        records.forEach { record ->
            add("${record.section.label}: ${record.name} ${record.ttlSeconds}s ${record.type} ${record.value}")
        }
        detail?.let(::add)
    }.joinToString("\n")
}

data class DnsComparisonResult(
    val queryName: String,
    val recordType: DnsRecordType,
    val results: List<DnsLookupResult>,
    val completedAtMillis: Long,
) {
    val successfulResults: List<DnsLookupResult> = results.filter { it.status == DnsResponseStatus.SUCCESS }
    val consistent: Boolean = successfulResults.size >= 2 &&
        successfulResults.map(DnsLookupResult::comparableAnswers).distinct().size == 1

    fun asPlainText(): String = buildList {
        add("DNS resolver comparison")
        add("Query: $queryName ${recordType.label}")
        add("Consistency: ${if (consistent) "Matching answers" else "Answers differ or are incomplete"}")
        results.forEach { result ->
            add("")
            add(result.asPlainText())
        }
    }.joinToString("\n")
}

enum class DnsLookupView(val label: String) {
    LOOKUP("Lookup"),
    COMPARE("Compare"),
}

data class DnsLookupUiState(
    val running: Boolean = false,
    val permissionRequired: Boolean = false,
    val activeQuery: PreparedDnsQuery? = null,
    val result: DnsLookupResult? = null,
    val comparison: DnsComparisonResult? = null,
    val comparisonProgress: List<DnsLookupResult> = emptyList(),
    val error: String? = null,
)
