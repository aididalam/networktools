package com.nexdevlab.networktools.data

import java.net.InetAddress
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IpFinderJsonParsingTest {
    @Test
    fun ianaBootstrap_selectsMatchingAuthoritativeRdapService() {
        val bootstrap = JSONObject(
            """{"services":[[["1.0.0.0/8"],["https://rdap.apnic.net/"]],[["8.0.0.0/8"],["https://rdap.arin.net/registry/"]]]}""",
        )
        assertEquals("https://rdap.apnic.net/",
            IpFinderRepository.selectRdapBase(InetAddress.getByName("1.1.1.1"), bootstrap))
        assertEquals("https://rdap.arin.net/registry/",
            IpFinderRepository.selectRdapBase(InetAddress.getByName("8.8.8.8"), bootstrap))
        assertNull(IpFinderRepository.selectRdapBase(InetAddress.getByName("9.9.9.9"), bootstrap))
    }

    @Test
    fun rdapParser_prefersPublishedOrganizationAndKeepsNetworkName() {
        val response = JSONObject(
            """{
                "name":"CLOUDFLARENET",
                "entities":[{
                    "roles":["registrant"],
                    "vcardArray":["vcard",[["version",{},"text","4.0"],["org",{},"text","Cloudflare, Inc."]]]
                }]
            }""".trimIndent(),
        )
        val registration = IpFinderRepository.parseRegistration(response, "https://rdap.arin.net/registry/")
        assertEquals("Cloudflare, Inc.", registration.organization)
        assertEquals("CLOUDFLARENET", registration.networkName)
    }

    @Test
    fun rdapParser_findsNestedEntityAndFallsBackToNetworkName() {
        val nested = JSONObject(
            """{"name":"NET","entities":[{"entities":[{"vcardArray":["vcard",[["fn",{},"text","Nested Org"]]]}]}]}""",
        )
        assertEquals("Nested Org", IpFinderRepository.parseRegistration(nested, "https://rdap.example/").organization)
        val fallback = IpFinderRepository.parseRegistration(JSONObject("""{"name":"PUBLIC-NET"}"""), "https://rdap.example/")
        assertNull(fallback.organization)
        assertEquals("PUBLIC-NET", fallback.networkName)
    }
}
