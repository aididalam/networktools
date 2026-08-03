package com.nexdevlab.networktools.data

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xbill.DNS.AAAARecord
import org.xbill.DNS.ARecord
import org.xbill.DNS.CAARecord
import org.xbill.DNS.CNAMERecord
import org.xbill.DNS.DClass
import org.xbill.DNS.MXRecord
import org.xbill.DNS.Message
import org.xbill.DNS.NSRecord
import org.xbill.DNS.Name
import org.xbill.DNS.PTRRecord
import org.xbill.DNS.Rcode
import org.xbill.DNS.SOARecord
import org.xbill.DNS.SRVRecord
import org.xbill.DNS.Section
import org.xbill.DNS.TXTRecord

class DnsLookupRepositoryTest {
    private val example = Name.fromConstantString("example.com.")

    @Test
    fun normalizeName_handlesCaseTrailingDotAndIdn() {
        assertEquals("example.com", DnsQueryConfig.normalizeName(" Example.COM. ", DnsRecordType.A))
        assertEquals("xn--bcher-kva.example", DnsQueryConfig.normalizeName("bücher.example", DnsRecordType.A))
        assertEquals("_sip._tcp.example.com", DnsQueryConfig.normalizeName("_SIP._TCP.Example.com", DnsRecordType.SRV))
    }

    @Test
    fun normalizeName_rejectsUrlsCommandsEmptyLabelsAndOversizedNames() {
        listOf("", "https://example.com", "example.com/path", "example.com;id", "a..example.com", "-a.example")
            .forEach { value ->
                assertTrue(value, runCatching { DnsQueryConfig.normalizeName(value, DnsRecordType.A) }.isFailure)
            }
        val oversized = List(5) { "a".repeat(63) }.joinToString(".")
        assertTrue(runCatching { DnsQueryConfig.normalizeName(oversized, DnsRecordType.A) }.isFailure)
    }

    @Test
    fun ptrInput_convertsIpv4AndIpv6ToReverseNames() {
        assertEquals("1.2.0.192.in-addr.arpa", DnsQueryConfig.normalizeName("192.0.2.1", DnsRecordType.PTR))
        assertTrue(DnsQueryConfig.normalizeName("2001:db8::1", DnsRecordType.PTR).endsWith("ip6.arpa"))
    }

    @Test
    fun resolverValidation_acceptsNumericUnicastAndRejectsNamesOrUnsafeAddresses() {
        assertEquals("1.1.1.1", DnsQueryConfig.parseResolverAddress("1.1.1.1").hostAddress)
        assertTrue(DnsQueryConfig.parseResolverAddress("2001:4860:4860::8888").hostAddress.orEmpty().contains(':'))
        listOf("dns.google", "0.0.0.0", "224.0.0.1", "fe80::1%wlan0", "999.1.1.1").forEach { value ->
            assertTrue(value, runCatching { DnsQueryConfig.parseResolverAddress(value) }.isFailure)
        }
    }

    @Test
    fun config_requiresAllowedTimeoutAndCustomResolver() {
        DnsQueryConfig("example.com", DnsRecordType.A, timeoutMillis = 1_000).validate()
        assertTrue(runCatching { DnsQueryConfig("example.com", DnsRecordType.A, timeoutMillis = 500).validate() }.isFailure)
        assertTrue(runCatching {
            DnsQueryConfig("example.com", DnsRecordType.A, DnsResolverMode.CUSTOM, "resolver.example").validate()
        }.isFailure)
    }

    @Test
    fun parseResponse_preservesSectionsTtlRcodeAndTransport() {
        val message = response().apply {
            addRecord(ARecord(example, DClass.IN, 300, InetAddress.getByName("192.0.2.1")), Section.ANSWER)
            addRecord(NSRecord(example, DClass.IN, 900, Name.fromConstantString("ns1.example.com.")), Section.AUTHORITY)
            addRecord(ARecord(Name.fromConstantString("ns1.example.com."), DClass.IN, 900,
                InetAddress.getByName("192.0.2.53")), Section.ADDITIONAL)
        }

        val result = parse(message, usedTcp = true)

        assertEquals(DnsResponseStatus.SUCCESS, result.status)
        assertEquals("NOERROR", result.responseCode)
        assertEquals(3, result.records.size)
        assertEquals(300L, result.minimumTtl)
        assertTrue(result.usedTcp)
        assertEquals(listOf(DnsSection.ANSWER, DnsSection.AUTHORITY, DnsSection.ADDITIONAL),
            result.records.map(DnsRecordEntry::section))
    }

    @Test
    fun parseResponse_distinguishesNoRecordsNxdomainRefusedAndServerFailure() {
        assertEquals(DnsResponseStatus.NO_RECORDS, parse(response()).status)
        assertEquals(DnsResponseStatus.NXDOMAIN, parse(response(Rcode.NXDOMAIN)).status)
        assertEquals(DnsResponseStatus.REFUSED, parse(response(Rcode.REFUSED)).status)
        assertEquals(DnsResponseStatus.SERVER_FAILURE, parse(response(Rcode.SERVFAIL)).status)
    }

    @Test
    fun recordDisplay_formatsSupportedStructuredTypes() {
        val records = listOf(
            ARecord(example, DClass.IN, 1, InetAddress.getByName("192.0.2.1")) to "192.0.2.1",
            AAAARecord(example, DClass.IN, 1, InetAddress.getByName("2001:db8::1")) to "2001:db8",
            MXRecord(example, DClass.IN, 1, 10, Name.fromConstantString("mail.example.com.")) to "10 mail.example.com",
            MXRecord(example, DClass.IN, 1, 0, Name.root) to "0 .",
            NSRecord(example, DClass.IN, 1, Name.fromConstantString("ns.example.com.")) to "ns.example.com",
            TXTRecord(example, DClass.IN, 1, listOf("one", "two")) to "\"one\" \"two\"",
            CNAMERecord(example, DClass.IN, 1, Name.fromConstantString("target.example.com.")) to "target.example.com",
            SOARecord(example, DClass.IN, 1, Name.fromConstantString("ns.example.com."),
                Name.fromConstantString("admin.example.com."), 1, 2, 3, 4, 5) to "ns.example.com admin.example.com 1 2 3 4 5",
            PTRRecord(example, DClass.IN, 1, Name.fromConstantString("host.example.com.")) to "host.example.com",
            SRVRecord(example, DClass.IN, 1, 10, 20, 443, Name.fromConstantString("service.example.com."))
                to "10 20 443 service.example.com",
            CAARecord(example, DClass.IN, 1, 0, "issue", "ca.example") to "0 issue \"ca.example\"",
        )
        records.forEach { (record, expected) ->
            assertTrue("${record.javaClass.simpleName}: ${record.rdataToString()}",
                DnsLookupRepository.run { record.displayValue() }.contains(expected, ignoreCase = true))
        }
    }

    @Test
    fun responseRecordCount_isBounded() {
        val message = response()
        repeat(120) { index ->
            message.addRecord(
                ARecord(Name.fromConstantString("host$index.example.com."), DClass.IN, 60,
                    InetAddress.getByName("192.0.2.${index % 250 + 1}")),
                Section.ANSWER,
            )
        }
        assertEquals(DnsLookupRepository.MaximumResponseRecords, parse(message).records.size)
    }

    @Test
    fun comparison_ignoresTtlButDetectsAnswerDifferences() {
        val first = successful("Cloudflare", "1.1.1.1", "192.0.2.1", 60)
        val second = successful("Google", "8.8.8.8", "192.0.2.1", 300)
        val matching = DnsComparisonResult("example.com", DnsRecordType.A, listOf(first, second), 1)
        val different = DnsComparisonResult("example.com", DnsRecordType.A,
            listOf(first, successful("Quad9", "9.9.9.9", "192.0.2.2", 60)), 1)

        assertTrue(matching.consistent)
        assertFalse(different.consistent)
    }

    @Test
    fun plainText_includesResolverTtlAndRecordValue() {
        val text = successful("Cloudflare", "1.1.1.1", "192.0.2.1", 60).asPlainText()
        assertTrue(text.contains("Cloudflare (1.1.1.1)"))
        assertTrue(text.contains("60s A 192.0.2.1"))
    }

    private fun response(rcode: Int = Rcode.NOERROR): Message = Message.newQuery(
        org.xbill.DNS.Record.newRecord(example, org.xbill.DNS.Type.A, DClass.IN),
    ).apply { header.setRcode(rcode) }

    private fun parse(message: Message, usedTcp: Boolean = false): DnsLookupResult =
        DnsLookupRepository.parseResponse(message, prepared(), DnsResolverTarget("Test", "192.0.2.53"), 12.5, usedTcp)

    private fun prepared() = PreparedDnsQuery(
        config = DnsQueryConfig("example.com", DnsRecordType.A),
        normalizedName = "example.com",
        resolvers = listOf(DnsResolverTarget("Test", "192.0.2.53")),
        privateDnsActive = false,
        privateDnsServerName = null,
        localNetworkResolver = false,
    )

    private fun successful(label: String, resolver: String, address: String, ttl: Long): DnsLookupResult =
        DnsLookupResult(
            queryName = "example.com",
            recordType = DnsRecordType.A,
            resolver = DnsResolverTarget(label, resolver),
            status = DnsResponseStatus.SUCCESS,
            responseCode = "NOERROR",
            records = listOf(DnsRecordEntry(DnsSection.ANSWER, "example.com", "A", address, ttl)),
            latencyMillis = 10.0,
            usedTcp = false,
            privateDnsActive = false,
            privateDnsServerName = null,
            completedAtMillis = 1,
        )
}
