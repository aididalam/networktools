package com.nexdevlab.networktools.data

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IpFinderRepositoryTest {
    @Test
    fun publicMode_requiresNoTarget() {
        val config = IpFinderConfig(IpFinderMode.PUBLIC_IP, target = "ignored input")
        config.validate()
        assertNull(config.normalizedTarget())
    }

    @Test
    fun targetMode_normalizesDomainIpv4AndIpv6() {
        assertEquals("example.com", target(" Example.COM. ").normalizedTarget())
        assertEquals("1.1.1.1", target(" 1.1.1.1 ").normalizedTarget())
        assertEquals("2606:4700:4700::1111", target("2606:4700:4700:0:0:0:0:1111").normalizedTarget())
    }

    @Test
    fun targetMode_rejectsBlankUrlCommandCidrAndMalformedNumericInput() {
        listOf("", "https://example.com", "example.com/path", "example.com;id", "1.1.1.1/24", "999.1.1.1")
            .forEach { value -> assertTrue(value, runCatching { target(value).validate() }.isFailure) }
    }

    @Test
    fun config_rejectsUnsupportedTimeoutAndNamedCustomResolver() {
        target("example.com").copy(timeoutMillis = 500).let { config ->
            assertTrue(runCatching(config::validate).isFailure)
        }
        target("example.com").copy(
            resolverMode = DnsResolverMode.CUSTOM,
            customResolver = "dns.google",
        ).let { config -> assertTrue(runCatching(config::validate).isFailure) }
    }

    @Test
    fun publicAddressFilter_acceptsGlobalIpv4AndIpv6() {
        listOf("1.1.1.1", "8.8.8.8", "2606:4700:4700::1111").forEach { value ->
            assertTrue(value, IpFinderRepository.isPublicAddress(InetAddress.getByName(value)))
        }
    }

    @Test
    fun publicAddressFilter_rejectsPrivateDocumentationAndReservedRanges() {
        listOf(
            "0.0.0.0", "10.0.0.1", "100.64.0.1", "127.0.0.1", "169.254.1.1", "192.0.2.1",
            "192.168.1.1", "198.51.100.1", "203.0.113.1", "224.0.0.1", "::1", "fc00::1", "fe80::1", "2001:db8::1",
        ).forEach { value ->
            assertFalse(value, IpFinderRepository.isPublicAddress(InetAddress.getByName(value)))
        }
    }

    @Test
    fun originQueryName_buildsIpv4AndIpv6CymruNames() {
        assertEquals("1.1.1.1.origin.asn.cymru.com",
            IpFinderRepository.originQueryName(InetAddress.getByName("1.1.1.1")))
        val ipv6 = IpFinderRepository.originQueryName(InetAddress.getByName("2001:4860:4860::8888"))
        assertTrue(ipv6.endsWith(".origin6.asn.cymru.com"))
        assertTrue(ipv6.startsWith("8.8.8.8"))
    }

    @Test
    fun originParser_handlesSingleAndMultiOriginAnswers() {
        val single = IpFinderRepository.parseOriginAsn("\"13335 | 1.1.1.0/24 | AU | apnic | 2011-08-11\"")
        assertEquals(listOf("AS13335"), single?.numbers)
        assertEquals("1.1.1.0/24", single?.routedPrefix)
        assertEquals("apnic", single?.registry)

        val multiple = IpFinderRepository.parseOriginAsn("13335 209242 | 1.1.1.0/24 | AU | APNIC")
        assertEquals(listOf("AS13335", "AS209242"), multiple?.numbers)
        assertNull(IpFinderRepository.parseOriginAsn("invalid response"))
    }

    @Test
    fun plainText_containsOnlyDocumentedRegistrationAndOriginFields() {
        val result = IpFinderResult(
            mode = IpFinderMode.TARGET,
            query = "example.com",
            resolvedAddresses = listOf("1.1.1.1", "2606:4700:4700::1111"),
            selectedAddress = "1.1.1.1",
            resolver = DnsResolverTarget("Test DNS", "8.8.8.8"),
            registration = IpFinderRegistration("Example Org", "EXAMPLE-NET", "https://rdap.example/"),
            origin = IpFinderAsn(listOf("AS13335"), "1.1.1.0/24", "apnic"),
            notices = emptyList(),
            completedAtMillis = 1,
        )
        val text = result.asPlainText()
        assertTrue(text.contains("Organization: Example Org"))
        assertTrue(text.contains("ASN: AS13335"))
        listOf("Latitude", "Longitude", "City", "VPN", "Proxy", "Hosting", "Time zone").forEach {
            assertFalse(it, text.contains(it, ignoreCase = true))
        }
    }

    private fun target(value: String) = IpFinderConfig(IpFinderMode.TARGET, target = value)
}
