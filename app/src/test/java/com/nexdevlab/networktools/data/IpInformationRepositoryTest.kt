package com.nexdevlab.networktools.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IpInformationRepositoryTest {
    @Test
    fun subnetMaskCoversBoundaryAndCommonPrefixes() {
        assertEquals("0.0.0.0", IpInformationRepository.ipv4SubnetMask(0))
        assertEquals("255.255.255.0", IpInformationRepository.ipv4SubnetMask(24))
        assertEquals("255.255.255.255", IpInformationRepository.ipv4SubnetMask(32))
        assertNull(IpInformationRepository.ipv4SubnetMask(33))
    }

    @Test
    fun broadcastAddressUsesUnsignedIpv4Math() {
        assertEquals(
            "192.168.1.255",
            IpInformationRepository.ipv4BroadcastAddress(byteArrayOf(192.toByte(), 168.toByte(), 1, 42), 24),
        )
        assertEquals(
            "10.255.255.255",
            IpInformationRepository.ipv4BroadcastAddress(byteArrayOf(10, 40, 1, 7), 8),
        )
        assertNull(IpInformationRepository.ipv4BroadcastAddress(byteArrayOf(1, 2, 3), 24))
    }

    @Test
    fun plainTextIncludesOnlyAvailableValues() {
        val result = NetworkSnapshot(
            publicIp = "203.0.113.7",
            localAddresses = listOf("192.168.1.42"),
            connectionType = "Wi-Fi",
        ).asPlainText()

        assertEquals(
            "Network information\n" +
                "Public IP: 203.0.113.7\n" +
                "Local IP: 192.168.1.42\n" +
                "Connection type: Wi-Fi",
            result,
        )
    }
}
