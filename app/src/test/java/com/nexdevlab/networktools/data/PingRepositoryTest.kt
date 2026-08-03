package com.nexdevlab.networktools.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PingRepositoryTest {
    @Test
    fun normalizeTarget_acceptsHostnameIpv4AndIpv6() {
        assertEquals("example.com", PingRepository.normalizeTarget(" Example.COM. "))
        assertEquals("8.8.8.8", PingRepository.normalizeTarget("8.8.8.8"))
        assertEquals("2001:db8::1", PingRepository.normalizeTarget("2001:DB8::1"))
    }

    @Test
    fun normalizeTarget_convertsInternationalHostnameToAscii() {
        assertEquals("xn--bcher-kva.example", PingRepository.normalizeTarget("bücher.example"))
    }

    @Test
    fun normalizeTarget_rejectsUrlsShellCharactersAndInvalidAddresses() {
        listOf(
            "",
            "https://example.com",
            "example.com/path",
            "example.com;reboot",
            "example.com && reboot",
            "user@example.com",
            "999.1.1.1",
            "-example.com",
            "example..com",
            "fe80::1%wlan0",
        ).forEach { value ->
            assertTrue("Expected '$value' to be rejected", runCatching { PingRepository.normalizeTarget(value) }.isFailure)
        }
    }

    @Test
    fun config_acceptsAllSupportedBoundaries() {
        listOf(PingConfig.PacketCountRange.first, PingConfig.PacketCountRange.last).forEach { count ->
            listOf(PingConfig.PacketSizeRange.first, PingConfig.PacketSizeRange.last).forEach { size ->
                listOf(PingConfig.TimeoutRange.first, PingConfig.TimeoutRange.last).forEach { timeout ->
                    listOf(PingConfig.IntervalRange.first, PingConfig.IntervalRange.last).forEach { interval ->
                        PingConfig("127.0.0.1", PingMethod.ICMP, packetCount = count, packetSize = size,
                            timeoutMillis = timeout, intervalMillis = interval).validate()
                    }
                }
            }
        }
        PingConfig("localhost", PingMethod.TCP, port = 1).validate()
        PingConfig("localhost", PingMethod.TCP, port = 65535).validate()
    }

    @Test
    fun config_rejectsUnsafeProbeSettings() {
        val invalid = listOf(
            PingConfig("localhost", PingMethod.ICMP, packetCount = 0),
            PingConfig("localhost", PingMethod.ICMP, packetCount = 11),
            PingConfig("localhost", PingMethod.ICMP, packetSize = 15),
            PingConfig("localhost", PingMethod.ICMP, packetSize = 1401),
            PingConfig("localhost", PingMethod.ICMP, timeoutMillis = 499),
            PingConfig("localhost", PingMethod.ICMP, timeoutMillis = 5001),
            PingConfig("localhost", PingMethod.ICMP, intervalMillis = 249),
            PingConfig("localhost", PingMethod.ICMP, intervalMillis = 2001),
            PingConfig("localhost", PingMethod.TCP, port = 0),
            PingConfig("localhost", PingMethod.TCP, port = 65536),
        )
        invalid.forEach { config -> assertTrue(runCatching(config::validate).isFailure) }
    }

    @Test
    fun parseIcmpLine_extractsSequenceTtlAndDecimalLatency() {
        val probe = PingRepository.parseIcmpLine(
            "64 bytes from 8.8.8.8: icmp_seq=2 ttl=112 time=55.2 ms",
        )

        assertEquals(2, probe?.sequence)
        assertEquals(PingProbeStatus.REPLY, probe?.status)
        assertEquals(112, probe?.ttl)
        assertEquals(55.2, probe?.latencyMillis ?: 0.0, 0.001)
        assertTrue(probe?.received == true)
    }

    @Test
    fun parseIcmpLine_handlesLessThanLatencyAndTimeout() {
        val reply = PingRepository.parseIcmpLine(
            "64 bytes from 127.0.0.1: icmp_seq=1 ttl=64 time<1 ms",
        )
        val timeout = PingRepository.parseIcmpLine("no answer yet for icmp_seq=3")

        assertEquals(1.0, reply?.latencyMillis ?: 0.0, 0.001)
        assertEquals(PingProbeStatus.TIMEOUT, timeout?.status)
        assertFalse(timeout?.received ?: true)
        assertNull(PingRepository.parseIcmpLine("--- 8.8.8.8 ping statistics ---"))
    }

    @Test
    fun summary_calculatesCountsLossAndLatencyFromActualResponses() {
        val summary = PingSummary(
            target = "example.com",
            resolvedAddress = "192.0.2.1",
            method = PingMethod.ICMP,
            port = null,
            probes = listOf(
                PingProbe(1, PingProbeStatus.REPLY, 10.0, 50, "ICMP reply"),
                PingProbe(2, PingProbeStatus.TIMEOUT, detail = "No ICMP response"),
                PingProbe(3, PingProbeStatus.REPLY, 30.0, 49, "ICMP reply"),
                PingProbe(4, PingProbeStatus.ERROR, detail = "Destination unreachable"),
            ),
            stopped = false,
            completedAtMillis = 1L,
        )

        assertEquals(4, summary.sentCount)
        assertEquals(2, summary.receivedCount)
        assertEquals(2, summary.lostCount)
        assertEquals(50.0, summary.lossPercentage, 0.001)
        assertEquals(10.0, summary.minimumLatency ?: 0.0, 0.001)
        assertEquals(30.0, summary.maximumLatency ?: 0.0, 0.001)
        assertEquals(20.0, summary.averageLatency ?: 0.0, 0.001)
    }

    @Test
    fun summary_countsOpenAndClosedTcpPortsAsReceivedResponses() {
        val summary = PingSummary(
            target = "localhost",
            resolvedAddress = "127.0.0.1",
            method = PingMethod.TCP,
            port = 443,
            probes = listOf(
                PingProbe(1, PingProbeStatus.OPEN, 1.0, detail = "open"),
                PingProbe(2, PingProbeStatus.CLOSED, 2.0, detail = "closed"),
                PingProbe(3, PingProbeStatus.TIMEOUT, detail = "timeout"),
            ),
            stopped = true,
            completedAtMillis = 1L,
        )

        assertEquals(2, summary.receivedCount)
        assertEquals(1, summary.lostCount)
        assertTrue(summary.asPlainText().contains("Status: Stopped"))
        assertTrue(summary.asPlainText().contains("Method: TCP:443"))
    }
}
