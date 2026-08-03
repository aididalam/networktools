package com.nexdevlab.networktools

import com.nexdevlab.networktools.data.RouterAddress
import com.nexdevlab.networktools.data.RouterAddressSource
import com.nexdevlab.networktools.data.RouterProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RouterSetupModelsTest {
    @Test
    fun privateIpv4IsNormalizedAndUsesHttpsByDefaultSelection() {
        val address = RouterAddress.parse(" 192.168.1.1 ", RouterAddressSource.MANUAL)

        assertEquals("192.168.1.1", address.value)
        assertEquals("https://192.168.1.1", address.browserUrl(RouterProtocol.HTTPS))
    }

    @Test
    fun rfc1918RangesAreAccepted() {
        listOf("10.0.0.1", "172.16.0.1", "172.31.255.254", "192.168.0.1").forEach {
            assertEquals(it, RouterAddress.parse(it, RouterAddressSource.DETECTED).value)
        }
    }

    @Test
    fun linkLocalAndCarrierGradeNatGatewaysAreAccepted() {
        listOf("169.254.10.1", "100.64.0.1", "100.127.255.254").forEach {
            assertEquals(it, RouterAddress.parse(it, RouterAddressSource.DETECTED).value)
        }
    }

    @Test
    fun publicIpv4IsRejected() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            RouterAddress.parse("8.8.8.8", RouterAddressSource.MANUAL)
        }

        assertEquals("Router address must be a private or link-local IP address", failure.message)
    }

    @Test
    fun loopbackAndUnspecifiedAddressesAreRejected() {
        listOf("127.0.0.1", "0.0.0.0").forEach {
            assertThrows(IllegalArgumentException::class.java) {
                RouterAddress.parse(it, RouterAddressSource.MANUAL)
            }
        }
    }

    @Test
    fun urlsPathsPortsAndCredentialsAreRejected() {
        listOf(
            "https://192.168.1.1",
            "192.168.1.1/admin",
            "admin@192.168.1.1",
            "192.168.1.1?next=evil",
            "192.168.1.1:8080",
        ).forEach {
            assertThrows(IllegalArgumentException::class.java) {
                RouterAddress.parse(it, RouterAddressSource.MANUAL)
            }
        }
    }

    @Test
    fun malformedAndLeadingZeroIpv4AddressesAreRejected() {
        listOf("192.168.1", "192.168.001.1", "192.168.1.999").forEach {
            assertThrows(IllegalArgumentException::class.java) {
                RouterAddress.parse(it, RouterAddressSource.MANUAL)
            }
        }
    }

    @Test
    fun ipv6UniqueLocalAddressIsNormalizedAndBracketed() {
        val address = RouterAddress.parse("fd00:0:0:0:0:0:0:1", RouterAddressSource.MANUAL)

        assertEquals("fd00::1", address.value)
        assertEquals("https://[fd00::1]", address.browserUrl(RouterProtocol.HTTPS))
    }

    @Test
    fun scopedLinkLocalIpv6EncodesZoneForBrowserUrl() {
        val address = RouterAddress.parse("[fe80::1%wlan0]", RouterAddressSource.DETECTED)

        assertEquals("fe80::1%wlan0", address.value)
        assertEquals("https://[fe80::1%25wlan0]", address.browserUrl(RouterProtocol.HTTPS))
    }

    @Test
    fun publicIpv6AndInvalidZoneAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            RouterAddress.parse("2001:4860:4860::8888", RouterAddressSource.MANUAL)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RouterAddress.parse("fe80::1%bad zone", RouterAddressSource.MANUAL)
        }
    }

    @Test
    fun httpUrlIsConstructedOnlyAfterExplicitProtocolSelection() {
        val address = RouterAddress.parse("10.0.0.1", RouterAddressSource.MANUAL)

        assertEquals("http://10.0.0.1", address.browserUrl(RouterProtocol.HTTP))
    }

    @Test
    fun inputLengthIsBounded() {
        assertThrows(IllegalArgumentException::class.java) {
            RouterAddress.parse("1".repeat(RouterAddress.MaximumLength + 1), RouterAddressSource.MANUAL)
        }
    }
}
