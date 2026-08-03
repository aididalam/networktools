package com.nexdevlab.networktools.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TracerouteRepositoryTest {
    @Test
    fun config_acceptsAllBoundaryValues() {
        listOf(TracerouteConfig.MaxHopsRange.first, TracerouteConfig.MaxHopsRange.last).forEach { hops ->
            listOf(TracerouteConfig.AttemptsRange.first, TracerouteConfig.AttemptsRange.last).forEach { attempts ->
                listOf(TracerouteConfig.TimeoutRange.first, TracerouteConfig.TimeoutRange.last).forEach { timeout ->
                    TracerouteConfig("example.com", hops, attempts, timeout).validate()
                }
            }
        }
    }

    @Test
    fun config_rejectsExcessiveOrMalformedRequests() {
        listOf(
            TracerouteConfig("", 20, 1, 2),
            TracerouteConfig("https://example.com", 20, 1, 2),
            TracerouteConfig("example.com;reboot", 20, 1, 2),
            TracerouteConfig("example.com", 0, 1, 2),
            TracerouteConfig("example.com", 31, 1, 2),
            TracerouteConfig("example.com", 20, 0, 2),
            TracerouteConfig("example.com", 20, 4, 2),
            TracerouteConfig("example.com", 20, 1, 0),
            TracerouteConfig("example.com", 20, 1, 4),
        ).forEach { config -> assertTrue(runCatching(config::validate).isFailure) }
    }

    @Test
    fun parseProbeOutput_readsTtlExceededHop() {
        val attempt = TracerouteRepository.parseProbeOutput(
            "From 192.168.0.1: icmp_seq=1 Time to live exceeded",
            elapsedMillis = 12.4,
        )

        assertEquals(TraceAttemptStatus.HOP_REPLY, attempt.status)
        assertEquals("192.168.0.1", attempt.address)
        assertEquals(12.4, attempt.responseTimeMillis ?: 0.0, 0.001)
    }

    @Test
    fun parseProbeOutput_readsDestinationReplyAndReportedLatency() {
        val attempt = TracerouteRepository.parseProbeOutput(
            "64 bytes from 8.8.8.8: icmp_seq=1 ttl=112 time=53.7 ms",
            elapsedMillis = 60.0,
        )

        assertEquals(TraceAttemptStatus.DESTINATION_REPLY, attempt.status)
        assertEquals("8.8.8.8", attempt.address)
        assertEquals(53.7, attempt.responseTimeMillis ?: 0.0, 0.001)
    }

    @Test
    fun parseProbeOutput_readsIpv6Hop() {
        val attempt = TracerouteRepository.parseProbeOutput(
            "From 2001:4860:0:1::1 icmp_seq=1 Hop limit exceeded",
            elapsedMillis = 20.0,
        )

        assertEquals(TraceAttemptStatus.HOP_REPLY, attempt.status)
        assertEquals("2001:4860:0:1:0:0:0:1", attempt.address)
    }

    @Test
    fun parseProbeOutput_distinguishesUnreachableAndTimeout() {
        val unreachable = TracerouteRepository.parseProbeOutput(
            "From 172.16.0.1: icmp_seq=1 Destination Host Unreachable",
            elapsedMillis = 100.0,
        )
        val timeout = TracerouteRepository.parseProbeOutput(
            "1 packets transmitted, 0 received, 100% packet loss",
            elapsedMillis = 2000.0,
        )

        assertEquals(TraceAttemptStatus.UNREACHABLE, unreachable.status)
        assertEquals("172.16.0.1", unreachable.address)
        assertEquals(TraceAttemptStatus.TIMEOUT, timeout.status)
        assertNull(timeout.address)
        assertNull(timeout.responseTimeMillis)
    }

    @Test
    fun publicAddressFilter_excludesLocalReservedAndDocumentationRanges() {
        listOf(
            "0.0.0.0", "10.0.0.1", "100.64.0.1", "127.0.0.1", "169.254.1.1",
            "172.16.0.1", "192.168.0.1", "192.0.2.1", "198.51.100.1", "203.0.113.1",
            "224.0.0.1", "fc00::1", "fe80::1", "2001:db8::1",
        ).forEach { address ->
            assertFalse("Expected $address to remain local", TracerouteRepository.isPublicAddress(address))
        }
        assertTrue(TracerouteRepository.isPublicAddress("8.8.8.8"))
        assertTrue(TracerouteRepository.isPublicAddress("2001:4860:4860::8888"))
    }

    @Test
    fun resultText_preservesTimedOutHopsAndCompletionState() {
        val result = TracerouteResult(
            target = "example.com",
            resolvedAddress = "93.184.216.34",
            hops = listOf(
                TracerouteHop(1, listOf(TraceAttempt(TraceAttemptStatus.HOP_REPLY, "192.168.0.1", 2.0))),
                TracerouteHop(2, listOf(TraceAttempt(TraceAttemptStatus.TIMEOUT))),
                TracerouteHop(3, listOf(TraceAttempt(TraceAttemptStatus.DESTINATION_REPLY, "93.184.216.34", 30.0))),
            ),
            reachedDestination = true,
            stopped = false,
            completedAtMillis = 1L,
        )

        assertTrue(result.asPlainText().contains("Status: Destination reached"))
        assertTrue(result.asPlainText().contains("2. No response  *"))
        assertTrue(result.asPlainText().contains("3. 93.184.216.34  30.0 ms"))
    }
}
