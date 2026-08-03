package com.nexdevlab.networktools.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WhoisRepositoryTest {
    @Test
    fun domainNormalizationSupportsIdnAndCanonicalCase() {
        assertEquals("example.com", WhoisRepository.normalizeDomain(" Example.COM. "))
        assertEquals("xn--bcher-kva.example", WhoisRepository.normalizeDomain("bücher.example"))
    }

    @Test
    fun domainNormalizationRejectsUrlsWildcardsAndIpAddresses() {
        assertThrows(IllegalArgumentException::class.java) {
            WhoisRepository.normalizeDomain("https://example.com")
        }
        assertThrows(IllegalArgumentException::class.java) {
            WhoisRepository.normalizeDomain("*.example.com")
        }
        assertThrows(IllegalArgumentException::class.java) {
            WhoisRepository.normalizeDomain("192.0.2.1")
        }
    }

    @Test
    fun ipNormalizationAcceptsIpv4AndIpv6Only() {
        assertEquals("8.8.8.8", WhoisRepository.normalizeIpAddress(" 8.8.8.8 "))
        assertTrue(WhoisRepository.normalizeIpAddress("2001:4860:4860::8888").contains(':'))
        assertThrows(IllegalArgumentException::class.java) {
            WhoisRepository.normalizeIpAddress("example.com")
        }
    }

    @Test
    fun manualServerRejectsUrlsPortsAndCommandSeparators() {
        assertEquals("whois.iana.org", WhoisRepository.normalizeWhoisServer("WHOIS.IANA.ORG"))
        listOf("whois://whois.iana.org", "whois.iana.org:43", "whois.iana.org\r\nHELP").forEach {
            assertThrows(IllegalArgumentException::class.java) {
                WhoisRepository.normalizeWhoisServer(it)
            }
        }
    }

    @Test
    fun domainResponseParsesRegistrarDatesNameserversAndRedaction() {
        val registry = """
            Domain Name: EXAMPLE.COM
            Registrar WHOIS Server: whois.registrar.test
            Registrar: Example Registrar, Inc.
            Creation Date: 1995-08-14T04:00:00Z
            Registry Expiry Date: 2027-08-13T04:00:00Z
            Name Server: A.IANA-SERVERS.NET
            Name Server: B.IANA-SERVERS.NET
        """.trimIndent()
        val registrar = """
            Domain Name: example.com
            Registrar: Example Registrar, Inc.
            Registrant Name: REDACTED FOR PRIVACY
            Registrant Organization: Example Organization
            Name Server: a.iana-servers.net
        """.trimIndent()

        val result = WhoisRepository.parseWhois(
            query = "example.com",
            queryType = WhoisQueryType.DOMAIN,
            responses = listOf(
                WhoisRepository.ServerResponse("whois.registry.test", registry),
                WhoisRepository.ServerResponse("whois.registrar.test", registrar),
            ),
        )

        assertEquals("Example Registrar, Inc.", result.registrar)
        assertEquals("1995-08-14T04:00:00Z", result.registrationDate)
        assertEquals("2027-08-13T04:00:00Z", result.expirationDate)
        assertEquals(listOf("a.iana-servers.net", "b.iana-servers.net"), result.nameServers)
        assertEquals("REDACTED FOR PRIVACY", result.registrant)
        assertEquals("Example Organization", result.organization)
        assertNull(result.ipBlockOwner)
        assertEquals(listOf("whois.registry.test", "whois.registrar.test"), result.servers)
    }

    @Test
    fun ipResponseParsesOwnerAndAutonomousSystemAcrossRegistryStyles() {
        val result = WhoisRepository.parseWhois(
            query = "8.8.8.8",
            queryType = WhoisQueryType.IP_ADDRESS,
            responses = listOf(
                WhoisRepository.ServerResponse(
                    "whois.arin.net",
                    """
                        NetRange: 8.8.8.0 - 8.8.8.255
                        OrgName: Google LLC
                        OriginAS: AS15169
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals("Google LLC", result.organization)
        assertEquals("Google LLC", result.ipBlockOwner)
        assertEquals("AS15169", result.autonomousSystem)
        assertTrue(result.rawResponse.startsWith("----- whois.arin.net -----"))
    }

    @Test
    fun exportIncludesOnlyAvailableStructuredFields() {
        val result = WhoisResult(
            query = "example.com",
            queryType = WhoisQueryType.DOMAIN,
            servers = listOf("whois.example.test"),
            registrar = "Example Registrar",
            rawResponse = "raw",
        ).asPlainText()

        assertTrue(result.contains("Query: example.com"))
        assertTrue(result.contains("Registrar: Example Registrar"))
        assertTrue(!result.contains("Registrant:"))
        assertTrue(!result.contains("raw"))
    }
}
