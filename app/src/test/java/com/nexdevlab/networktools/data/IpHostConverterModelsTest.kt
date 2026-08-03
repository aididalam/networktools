package com.nexdevlab.networktools.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IpHostConverterModelsTest {
    @Test
    fun hostname_normalizesCaseTrailingDotAndIdn() {
        assertEquals("example.com", host(" Example.COM. ").normalizedTarget())
        assertEquals("xn--bcher-kva.example", host("bücher.example").normalizedTarget())
    }

    @Test
    fun hostname_rejectsUrlsCommandsWhitespaceAndOversizedInput() {
        val invalid = listOf("", "https://example.com", "example.com/path", "example.com;id", "two hosts", "a\nexample.com")
        invalid.forEach { value -> assertTrue(value, runCatching { host(value).validate() }.isFailure) }
        assertTrue(runCatching { host("a".repeat(254)).validate() }.isFailure)
    }

    @Test
    fun addressMode_selectsOneOrTwoBoundedQueries() {
        assertEquals(listOf(DnsRecordType.A, DnsRecordType.AAAA), host("example.com").recordTypes())
        assertEquals(listOf(DnsRecordType.A), host("example.com", IpHostAddressMode.IPV4).recordTypes())
        assertEquals(listOf(DnsRecordType.AAAA), host("example.com", IpHostAddressMode.IPV6).recordTypes())
    }

    @Test
    fun reverseMode_normalizesIpv4AndIpv6() {
        assertEquals("192.0.2.1", reverse(" 192.0.2.1 ").normalizedTarget())
        assertEquals("2001:db8::1", reverse("2001:0db8:0:0:0:0:0:1").normalizedTarget())
        assertEquals(listOf(DnsRecordType.PTR), reverse("192.0.2.1").recordTypes())
    }

    @Test
    fun reverseMode_rejectsHostnamesCidrsZonesAndInvalidAddresses() {
        listOf("", "example.com", "192.168.1.1/24", "fe80::1%wlan0", "192.168.01.1", "999.1.1.1", "2001:::1")
            .forEach { value -> assertTrue(value, runCatching { reverse(value).validate() }.isFailure) }
    }

    @Test
    fun config_validatesTimeoutAndNumericCustomResolver() {
        host("example.com").copy(
            resolverMode = DnsResolverMode.CUSTOM,
            customResolver = "1.1.1.1",
            timeoutMillis = 1_000,
        ).validate()
        assertTrue(runCatching { host("example.com").copy(timeoutMillis = 500).validate() }.isFailure)
        assertTrue(runCatching {
            host("example.com").copy(resolverMode = DnsResolverMode.CUSTOM, customResolver = "dns.google").validate()
        }.isFailure)
    }

    @Test
    fun result_preservesMultipleFamiliesDeduplicatesAndRemovesPtrDot() {
        val prepared = prepared(host("example.com"))
        val results = listOf(
            result(DnsRecordType.A, listOf("192.0.2.1", "192.0.2.1", "192.0.2.2")),
            result(DnsRecordType.AAAA, listOf("2001:db8::1")),
        )

        val converted = IpHostConversionResult.from(prepared, results, 1)

        assertTrue(converted.successful)
        assertEquals(listOf("A", "A", "AAAA"), converted.answers.map(IpHostConversionAnswer::type))
        assertEquals(listOf("192.0.2.1", "192.0.2.2", "2001:db8::1"), converted.answers.map(IpHostConversionAnswer::value))
        assertEquals("3 results", converted.statusText)

        val ptrPrepared = prepared(reverse("192.0.2.1"))
        val ptr = IpHostConversionResult.from(ptrPrepared, listOf(result(DnsRecordType.PTR, listOf("host.example.com."))), 1)
        assertEquals("host.example.com", ptr.answers.single().value)
    }

    @Test
    fun result_capsAnswersAndIgnoresNonRequestedRecordTypes() {
        val prepared = prepared(host("example.com", IpHostAddressMode.IPV4))
        val values = List(40) { "192.0.2.${it + 1}" }
        val results = listOf(
            result(DnsRecordType.A, values),
            result(DnsRecordType.AAAA, listOf("2001:db8::1")),
        )

        val converted = IpHostConversionResult.from(prepared, results, 1)

        assertEquals(IpHostConversionResult.MaximumAnswers, converted.answers.size)
        assertTrue(converted.answers.all { it.type == "A" })
    }

    @Test
    fun emptyResult_exposesDistinctDnsStatuses() {
        val prepared = prepared(host("missing.example"))
        val results = listOf(
            result(DnsRecordType.A, emptyList(), DnsResponseStatus.NXDOMAIN),
            result(DnsRecordType.AAAA, emptyList(), DnsResponseStatus.TIMED_OUT),
        )

        val converted = IpHostConversionResult.from(prepared, results, 1)

        assertFalse(converted.successful)
        assertEquals("Name not found / Timed out", converted.statusText)
    }

    @Test
    fun plainText_includesModeResolverAnswersAndTtl() {
        val converted = IpHostConversionResult.from(
            prepared(host("example.com", IpHostAddressMode.IPV4)),
            listOf(result(DnsRecordType.A, listOf("192.0.2.1"))),
            1,
        )
        val text = converted.asPlainText()

        assertTrue(text.contains("Mode: Host to IP"))
        assertTrue(text.contains("Test DNS (192.0.2.53)"))
        assertTrue(text.contains("A: 192.0.2.1 (TTL 60s)"))
    }

    private fun host(value: String, addressMode: IpHostAddressMode = IpHostAddressMode.BOTH) =
        IpHostConversionConfig(IpHostConversionMode.HOST_TO_IP, value, addressMode)

    private fun reverse(value: String) = IpHostConversionConfig(IpHostConversionMode.IP_TO_HOST, value)

    private fun prepared(config: IpHostConversionConfig): PreparedIpHostConversion {
        val normalized = config.normalizedTarget()
        return PreparedIpHostConversion(
            config,
            normalized,
            config.recordTypes().map { type ->
                PreparedDnsQuery(
                    config = DnsQueryConfig(normalized, type),
                    normalizedName = normalized,
                    resolvers = listOf(DnsResolverTarget("Test DNS", "192.0.2.53")),
                    privateDnsActive = false,
                    privateDnsServerName = null,
                    localNetworkResolver = false,
                )
            },
        )
    }

    private fun result(
        type: DnsRecordType,
        values: List<String>,
        status: DnsResponseStatus = if (values.isEmpty()) DnsResponseStatus.NO_RECORDS else DnsResponseStatus.SUCCESS,
    ) = DnsLookupResult(
        queryName = "example.com",
        recordType = type,
        resolver = DnsResolverTarget("Test DNS", "192.0.2.53"),
        status = status,
        responseCode = "TEST",
        records = values.map { value -> DnsRecordEntry(DnsSection.ANSWER, "example.com", type.label, value, 60) },
        latencyMillis = 1.0,
        usedTcp = false,
        privateDnsActive = false,
        privateDnsServerName = null,
        completedAtMillis = 1,
    )
}
