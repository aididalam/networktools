package com.nexdevlab.networktools.data

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortScannerRepositoryTest {
    @Test
    fun singleMode_acceptsPortBoundaries() {
        listOf("1", "65535").forEach { value ->
            val config = config(PortSelectionMode.SINGLE, singlePort = value)
            assertEquals(listOf(value.toInt()), config.validate())
        }
    }

    @Test
    fun singleMode_rejectsMalformedAndOutOfRangePorts() {
        listOf("", "0", "65536", "-1", "80.0", "http").forEach { value ->
            assertTrue(runCatching { config(PortSelectionMode.SINGLE, singlePort = value).validate() }.isFailure)
        }
    }

    @Test
    fun rangeMode_acceptsAnyBoundedWindowAndSortsNaturally() {
        val ports = config(PortSelectionMode.RANGE, rangeStart = "8000", rangeEnd = "8003").validate()

        assertEquals(listOf(8000, 8001, 8002, 8003), ports)
    }

    @Test
    fun rangeMode_rejectsReverseAndOversizedWindows() {
        assertTrue(runCatching {
            config(PortSelectionMode.RANGE, rangeStart = "100", rangeEnd = "99").validate()
        }.isFailure)
        assertTrue(runCatching {
            config(PortSelectionMode.RANGE, rangeStart = "1", rangeEnd = "513").validate()
        }.isFailure)
        assertEquals(512, config(PortSelectionMode.RANGE, rangeStart = "1000", rangeEnd = "1511").validate().size)
    }

    @Test
    fun customMode_normalizesWhitespaceAndSortsPorts() {
        val ports = config(PortSelectionMode.CUSTOM, customPorts = " 443, 22,8080 ").validate()

        assertEquals(listOf(22, 443, 8080), ports)
    }

    @Test
    fun customMode_rejectsDuplicatesEmptyTokensSyntaxAndOversizedLists() {
        listOf("", "22,22", "22,,80", "22-80", "22;80", "0,80", "80,65536").forEach { value ->
            assertTrue("Expected $value to fail", runCatching {
                config(PortSelectionMode.CUSTOM, customPorts = value).validate()
            }.isFailure)
        }
        val tooMany = (1..513).joinToString(",")
        assertTrue(runCatching { config(PortSelectionMode.CUSTOM, customPorts = tooMany).validate() }.isFailure)
    }

    @Test
    fun config_requiresAuthorizationAndBoundedTimeout() {
        assertTrue(runCatching { config(PortSelectionMode.SINGLE, authorized = false).validate() }.isFailure)
        listOf(249, 2001).forEach { timeout ->
            assertTrue(runCatching { config(PortSelectionMode.SINGLE, timeout = timeout).validate() }.isFailure)
        }
        config(PortSelectionMode.SINGLE, timeout = 250).validate()
        config(PortSelectionMode.SINGLE, timeout = 2000).validate()
    }

    @Test
    fun config_reusesStrictHostnameAndIpValidation() {
        listOf("https://example.com", "example.com/path", "host;reboot", "999.1.1.1").forEach { target ->
            assertTrue(runCatching { config(PortSelectionMode.SINGLE, target = target).validate() }.isFailure)
        }
        config(PortSelectionMode.SINGLE, target = "router.local").validate()
        config(PortSelectionMode.SINGLE, target = "2001:db8::1").validate()
    }

    @Test
    fun localAddressClassification_coversPrivateLoopbackUlaAndPublic() {
        listOf("127.0.0.1", "10.0.0.1", "172.16.0.1", "192.168.1.1", "fe80::1", "fd00::1")
            .forEach { value -> assertTrue(value, PortScannerRepository.isLocalNetworkAddress(InetAddress.getByName(value))) }
        listOf("8.8.8.8", "2001:4860:4860::8888")
            .forEach { value -> assertFalse(value, PortScannerRepository.isLocalNetworkAddress(InetAddress.getByName(value))) }
    }

    @Test
    fun commonServiceIdentification_isStaticAndDoesNotProbeBanners() {
        assertEquals("SSH", PortScannerRepository.serviceForPort(22))
        assertEquals("HTTPS", PortScannerRepository.serviceForPort(443))
        assertEquals("Remote Desktop", PortScannerRepository.serviceForPort(3389))
        assertEquals("Unknown service", PortScannerRepository.serviceForPort(65000))
    }

    @Test
    fun resultSummary_countsStatusesAndProducesShareableText() {
        val result = PortScanResult(
            target = "router.local",
            resolvedAddress = "192.168.1.1",
            entries = listOf(
                PortScanEntry(22, PortScanStatus.OPEN, "SSH", 1.0),
                PortScanEntry(23, PortScanStatus.CLOSED, "Telnet", 2.0),
                PortScanEntry(24, PortScanStatus.TIMED_OUT, "Unknown service"),
            ),
            timeoutMillis = 500,
            stopped = false,
            completedAtMillis = 1,
        )

        assertEquals(1, result.openCount)
        assertEquals(1, result.closedCount)
        assertEquals(1, result.timedOutCount)
        assertTrue(result.asPlainText().contains("22/tcp Open SSH"))
        assertTrue(result.asPlainText().contains("23/tcp Closed Telnet"))
    }

    private fun config(
        mode: PortSelectionMode,
        target: String = "example.com",
        singlePort: String = "443",
        rangeStart: String = "1",
        rangeEnd: String = "100",
        customPorts: String = "22,80,443",
        timeout: Int = 500,
        authorized: Boolean = true,
    ) = PortScanConfig(
        target = target,
        selectionMode = mode,
        singlePort = singlePort,
        rangeStart = rangeStart,
        rangeEnd = rangeEnd,
        customPorts = customPorts,
        timeoutMillis = timeout,
        authorized = authorized,
    )
}
