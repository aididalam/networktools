package com.nexdevlab.networktools.data

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IpCalculatorTest {
    @Test
    fun ipv4Slash24_calculatesAllNetworkAndHostFields() {
        val result = calculate("192.168.10.42", "24")

        assertEquals("192.168.10.42", result.normalizedAddress)
        assertEquals("192.168.10.0/24", result.cidr)
        assertEquals("192.168.10.0", result.networkAddress)
        assertEquals("255.255.255.0", result.subnetMask)
        assertEquals("192.168.10.255", result.broadcastAddress)
        assertEquals("192.168.10.1", result.firstHost)
        assertEquals("192.168.10.254", result.lastHost)
        assertEquals(BigInteger.valueOf(256), result.totalAddresses)
        assertEquals(BigInteger.valueOf(254), result.usableHosts)
    }

    @Test
    fun subnetMaskInput_derivesPrefixAndCidr() {
        val result = IpCalculator.calculate(
            IpCalculationInput(
                family = IpAddressFamily.IPV4,
                address = "172.16.31.200",
                prefixInputMode = IpPrefixInputMode.SUBNET_MASK,
                subnetMask = "255.255.240.0",
            ),
        )

        assertEquals(20, result.prefixLength)
        assertEquals("172.16.16.0/20", result.cidr)
        assertEquals("172.16.31.255", result.broadcastAddress)
    }

    @Test
    fun embeddedCidrPrefix_isAcceptedAndNormalized() {
        val result = calculate("10.20.30.40/12", "24")

        assertEquals(12, result.prefixLength)
        assertEquals("10.16.0.0/12", result.cidr)
    }

    @Test
    fun embeddedCidrAndMask_mustMatch() {
        assertTrue(runCatching {
            IpCalculator.calculate(
                IpCalculationInput(IpAddressFamily.IPV4, "192.168.1.4/25",
                    IpPrefixInputMode.SUBNET_MASK, subnetMask = "255.255.255.0"),
            )
        }.isFailure)
    }

    @Test
    fun ipv4Slash31_usesBothPointToPointAddresses() {
        val result = calculate("192.0.2.11", "31")

        assertEquals("192.0.2.10/31", result.cidr)
        assertEquals("192.0.2.10", result.firstHost)
        assertEquals("192.0.2.11", result.lastHost)
        assertEquals(BigInteger.TWO, result.usableHosts)
    }

    @Test
    fun ipv4Slash32_hasOneAddressAndHost() {
        val result = calculate("203.0.113.9", "32")

        assertEquals("203.0.113.9/32", result.cidr)
        assertEquals(result.networkAddress, result.broadcastAddress)
        assertEquals(result.networkAddress, result.firstHost)
        assertEquals(result.networkAddress, result.lastHost)
        assertEquals(BigInteger.ONE, result.totalAddresses)
        assertEquals(BigInteger.ONE, result.usableHosts)
    }

    @Test
    fun ipv4SlashZero_coversEntireAddressSpace() {
        val result = calculate("255.255.255.255", "0")

        assertEquals("0.0.0.0/0", result.cidr)
        assertEquals("255.255.255.255", result.broadcastAddress)
        assertEquals(BigInteger.ONE.shiftLeft(32), result.totalAddresses)
        assertEquals(BigInteger.ONE.shiftLeft(32).subtract(BigInteger.TWO), result.usableHosts)
    }

    @Test
    fun ipv4Parser_rejectsLeadingZerosMalformedAndOutOfRangeOctets() {
        listOf("", "192.168.1", "192.168.1.1.2", "192.168.01.1", "256.1.1.1", "1.-1.1.1", "a.b.c.d")
            .forEach { value ->
                assertTrue(value, runCatching { IpCalculator.parseIpv4(value) }.isFailure)
            }
    }

    @Test
    fun ipv4Mask_requiresContiguousBits() {
        assertEquals(0, IpCalculator.prefixFromIpv4Mask("0.0.0.0"))
        assertEquals(32, IpCalculator.prefixFromIpv4Mask("255.255.255.255"))
        assertEquals(23, IpCalculator.prefixFromIpv4Mask("255.255.254.0"))
        listOf("255.0.255.0", "255.255.255.1", "255.255.256.0").forEach { mask ->
            assertTrue(mask, runCatching { IpCalculator.prefixFromIpv4Mask(mask) }.isFailure)
        }
    }

    @Test
    fun prefixValidation_rejectsFamilyOverflowAndNonNumericValues() {
        listOf("-1", "33", "abc", "").forEach { prefix ->
            assertTrue(prefix, runCatching { calculate("192.0.2.1", prefix) }.isFailure)
        }
        listOf("-1", "129", "abc", "").forEach { prefix ->
            assertTrue(prefix, runCatching { calculate6("2001:db8::1", prefix) }.isFailure)
        }
    }

    @Test
    fun membershipCheck_reportsInsideAndOutsideIpv4Subnet() {
        val inside = calculate("192.168.1.9", "24", "192.168.1.250")
        val outside = calculate("192.168.1.9", "24", "192.168.2.1")

        assertTrue(inside.membershipMatches == true)
        assertFalse(outside.membershipMatches == true)
        assertEquals("192.168.1.250", inside.membershipAddress)
    }

    @Test
    fun membershipCheck_rejectsMixedFamiliesAndPrefixes() {
        assertTrue(runCatching { calculate("192.168.1.1", "24", "2001:db8::1") }.isFailure)
        assertTrue(runCatching { calculate("192.168.1.1", "24", "192.168.1.2/24") }.isFailure)
    }

    @Test
    fun ipv6Slash64_calculatesCanonicalNetworkMaskAndRange() {
        val result = calculate6("2001:0db8:abcd:0012:1234:5678:9abc:def0", "64", "2001:db8:abcd:12::99")

        assertEquals("2001:db8:abcd:12:1234:5678:9abc:def0", result.normalizedAddress)
        assertEquals("2001:db8:abcd:12::/64", result.cidr)
        assertEquals("ffff:ffff:ffff:ffff::", result.subnetMask)
        assertNull(result.broadcastAddress)
        assertEquals("2001:db8:abcd:12::", result.firstHost)
        assertEquals("2001:db8:abcd:12:ffff:ffff:ffff:ffff", result.lastHost)
        assertEquals(BigInteger.ONE.shiftLeft(64), result.totalAddresses)
        assertEquals(result.totalAddresses, result.usableHosts)
        assertTrue(result.membershipMatches == true)
    }

    @Test
    fun ipv6Slash128_hasOneAddress() {
        val result = calculate6("2001:db8::1", "128")

        assertEquals("2001:db8::1/128", result.cidr)
        assertEquals("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff", result.subnetMask)
        assertEquals(BigInteger.ONE, result.totalAddresses)
        assertEquals(result.firstHost, result.lastHost)
    }

    @Test
    fun ipv6SlashZero_coversFull128BitRange() {
        val result = calculate6("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff", "0")

        assertEquals("::/0", result.cidr)
        assertEquals("::", result.subnetMask)
        assertEquals("::", result.firstHost)
        assertEquals("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff", result.lastHost)
        assertEquals(BigInteger.ONE.shiftLeft(128), result.totalAddresses)
    }

    @Test
    fun ipv6Formatter_usesLongestLeftmostZeroRun() {
        assertEquals("::", IpCalculator.formatIpv6(BigInteger.ZERO))
        assertEquals("2001:db8::1:0:0:1", IpCalculator.parseIpv6("2001:db8:0:0:1:0:0:1").format())
        assertEquals("2001:db8::", IpCalculator.parseIpv6("2001:db8:0:0:0:0:0:0").format())
    }

    @Test
    fun ipv6Parser_rejectsIpv4ZoneNamesAndMalformedInput() {
        listOf("", "192.168.1.1", "fe80::1%wlan0", "2001:db8:::1", "example.com", "gggg::1")
            .forEach { value ->
                assertTrue(value, runCatching { IpCalculator.parseIpv6(value) }.isFailure)
            }
    }

    @Test
    fun plainText_containsEveryRequestedCalculation() {
        val text = calculate("192.168.1.10", "24", "192.168.1.20").asPlainText()

        listOf("CIDR:", "Network:", "Subnet mask:", "Broadcast:", "Prefix length:", "First host:",
            "Last host:", "Host range:", "Total addresses:", "Usable hosts:", "Membership:")
            .forEach { label -> assertTrue(label, text.contains(label)) }
    }

    private fun calculate(address: String, prefix: String, member: String = "") =
        IpCalculator.calculate(IpCalculationInput(IpAddressFamily.IPV4, address, prefix = prefix,
            membershipAddress = member))

    private fun calculate6(address: String, prefix: String, member: String = "") =
        IpCalculator.calculate(IpCalculationInput(IpAddressFamily.IPV6, address, prefix = prefix,
            membershipAddress = member))
}
