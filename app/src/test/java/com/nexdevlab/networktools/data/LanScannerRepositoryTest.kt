package com.nexdevlab.networktools.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanScannerRepositoryTest {
    @Test
    fun subnetParser_normalizesHostAddressAndCalculatesBounds() {
        val subnet = LanSubnet.parse("192.168.10.42/24")

        assertEquals("192.168.10.0/24", subnet.display)
        assertEquals("192.168.10.255", LanSubnet.formatIpv4(subnet.broadcast))
        assertEquals(254, subnet.hostCount)
        assertEquals("192.168.10.1", LanSubnet.formatIpv4(subnet.hostAddresses().first()))
        assertEquals("192.168.10.254", LanSubnet.formatIpv4(subnet.hostAddresses().last()))
    }

    @Test
    fun subnetParser_acceptsRfc1918BoundaryRanges() {
        listOf("10.0.0.0/24", "172.16.0.0/24", "172.31.255.252/30", "192.168.255.0/24")
            .forEach { assertEquals(it, LanSubnet.parse(it).display) }
    }

    @Test
    fun subnetParser_rejectsPublicMalformedAndOversizedRanges() {
        listOf(
            "", "192.168.1.0", "8.8.8.0/24", "172.32.0.0/24", "100.64.0.0/24",
            "192.168.1.0/23", "192.168.1.0/31", "192.168.1.999/24", "192.168.01.0/24",
            "192.168.1.0/24/1", "not-a-subnet/24",
        ).forEach { assertTrue("Expected $it to be rejected", runCatching { LanSubnet.parse(it) }.isFailure) }
    }

    @Test
    fun subnetContains_onlyAddressesInsideNormalizedRange() {
        val subnet = LanSubnet.parse("10.20.30.0/28")

        assertTrue(subnet.contains(LanSubnet.parseIpv4("10.20.30.14")!!))
        assertFalse(subnet.contains(LanSubnet.parseIpv4("10.20.30.16")!!))
        assertEquals(14, subnet.hostCount)
    }

    @Test
    fun scanConfig_requiresExplicitAuthorization() {
        val subnet = LanSubnet.parse("192.168.1.0/24")

        assertTrue(runCatching { LanScanConfig(subnet, LanScanMode.FAST, false).validate() }.isFailure)
        LanScanConfig(subnet, LanScanMode.DEEP, true).validate()
    }

    @Test
    fun openPort_exposesOnlyPrivateDiscoveredWebServices() {
        assertEquals("http://192.168.1.20/", LanOpenPort(80, "HTTP").webUrl("192.168.1.20"))
        assertEquals("https://10.0.0.5:8443/", LanOpenPort(8443, "Alternate HTTPS").webUrl("10.0.0.5"))
        assertNull(LanOpenPort(22, "SSH").webUrl("192.168.1.20"))
        assertNull(LanOpenPort(80, "HTTP").webUrl("8.8.8.8"))
        assertNull(LanOpenPort(80, "HTTP").webUrl("not-an-address"))
    }

    @Test
    fun pingParser_distinguishesReplyFromLossAndErrors() {
        assertTrue(LanScannerRepository.parsePingReply("64 bytes from 192.168.1.1: time=2 ms\n1 received"))
        assertFalse(LanScannerRepository.parsePingReply("1 packets transmitted, 0 received, 100% packet loss"))
        assertFalse(LanScannerRepository.parsePingReply("ping: unreachable"))
    }

    @Test
    fun ipNeighborParser_filtersInvalidAndDeduplicatesRecords() {
        val records = LanScannerRepository.parseIpNeighbors(
            """
            192.168.1.1 dev wlan0 lladdr AA:BB:CC:DD:EE:01 REACHABLE
            192.168.1.2 dev wlan0 FAILED
            192.168.1.1 dev wlan0 lladdr aa:bb:cc:dd:ee:01 STALE
            malformed dev wlan0 lladdr aa:bb:cc:dd:ee:02 REACHABLE
            """.trimIndent(),
        )

        assertEquals(listOf(NeighborRecord("192.168.1.1", "aa:bb:cc:dd:ee:01")), records)
    }

    @Test
    fun procArpParser_requiresCompleteEntriesAndValidFlags() {
        val records = LanScannerRepository.parseProcArp(
            """
            IP address       HW type     Flags       HW address            Mask     Device
            192.168.1.1      0x1         0x2         aa:bb:cc:dd:ee:01     *        wlan0
            192.168.1.2      0x1         0x0         00:00:00:00:00:00     *        wlan0
            """.trimIndent(),
        )

        assertEquals(1, records.size)
        assertEquals("192.168.1.1", records.single().ipAddress)
    }

    @Test
    fun ssdpParser_acceptsResponsesAndBoundsMalformedInput() {
        val headers = LanScannerRepository.parseSsdpHeaders(
            "HTTP/1.1 200 OK\r\nST: upnp:rootdevice\r\nSERVER: Router OS\r\nUSN: uuid:test\r\n\r\n",
        )

        assertEquals("upnp:rootdevice", headers["st"])
        assertEquals("Router OS", headers["server"])
        assertTrue(LanScannerRepository.parseSsdpHeaders("GET / HTTP/1.1\r\nHost: test").isEmpty())
        assertTrue(LanScannerRepository.parseSsdpHeaders("A".repeat(9_000)).isEmpty())
    }

    @Test
    fun netbiosQuery_isBoundedAndEncodesNodeStatusRequest() {
        val query = LanScannerRepository.buildNetbiosNodeStatusQuery(0x1234)

        assertEquals(50, query.size)
        assertEquals(0x12, query[0].toInt() and 0xff)
        assertEquals(0x34, query[1].toInt() and 0xff)
        assertEquals(1, query[5].toInt())
        assertEquals(0x21, query[47].toInt() and 0xff)
    }

    @Test
    fun netbiosParser_readsComputerNameAndMac() {
        val packet = netbiosResponse(0x1234, "OFFICE-PC", byteArrayOf(0, 17, 34, 51, 68, 85))

        val record = LanScannerRepository.parseNetbiosNodeStatus(packet, 0x1234)

        assertEquals("OFFICE-PC", record?.hostname)
        assertEquals("00:11:22:33:44:55", record?.macAddress)
        assertNull(LanScannerRepository.parseNetbiosNodeStatus(packet, 0x9999))
        assertNull(LanScannerRepository.parseNetbiosNodeStatus(byteArrayOf(1, 2, 3), 0x1234))
    }

    @Test
    fun manufacturerLookup_identifiesLocalAddressesAndBundledOui() {
        assertEquals("TP-Link", LanScannerRepository.manufacturerForMac("b0:19:21:00:00:01"))
        assertEquals("Private/randomized address", LanScannerRepository.manufacturerForMac("b2:19:21:00:00:01"))
        assertNull(LanScannerRepository.manufacturerForMac("invalid"))
    }

    @Test
    fun deviceTypeEstimator_usesRoleAndAdvertisedSignalsConservatively() {
        assertEquals("This Android device", LanScannerRepository.estimateDeviceType(true, false, null, null, emptySet()))
        assertEquals("Router / gateway", LanScannerRepository.estimateDeviceType(false, true, null, null, emptySet()))
        assertEquals("Printer", LanScannerRepository.estimateDeviceType(false, false, null, null, setOf("Office _ipp._tcp")))
        assertEquals("Media / TV device", LanScannerRepository.estimateDeviceType(false, false, null, null, setOf("_googlecast._tcp")))
        assertEquals("Unknown device", LanScannerRepository.estimateDeviceType(false, false, "device", null, emptySet()))
    }

    @Test
    fun plainText_includesScanBoundaryAndDeviceEvidence() {
        val result = LanScanResult(
            subnet = "192.168.1.0/24",
            mode = LanScanMode.FAST,
            devices = listOf(
                LanDevice(
                    ipAddress = "192.168.1.1",
                    deviceType = "Router / gateway",
                    protocols = setOf(LanDiscoveryProtocol.ARP, LanDiscoveryProtocol.PING),
                    openPorts = listOf(LanOpenPort(80, "HTTP")),
                    gateway = true,
                    lastSeenMillis = 1,
                ),
            ),
            scannedHosts = 254,
            stopped = false,
            completedAtMillis = 1,
        ).asPlainText()

        assertTrue(result.contains("Subnet: 192.168.1.0/24"))
        assertTrue(result.contains("Devices: 1"))
        assertTrue(result.contains("Found by: ARP, Ping"))
        assertTrue(result.contains("80 HTTP"))
    }

    private fun netbiosResponse(transactionId: Int, hostname: String, mac: ByteArray): ByteArray {
        val packet = ByteArray(49)
        packet[0] = (transactionId shr 8).toByte()
        packet[1] = transactionId.toByte()
        packet[2] = 0x85.toByte()
        packet[7] = 1
        packet[12] = 0xc0.toByte()
        packet[13] = 0x0c
        packet[14] = 0
        packet[15] = 0x21
        packet[16] = 0
        packet[17] = 1
        packet[22] = 0
        packet[23] = 25
        packet[24] = 1
        hostname.padEnd(15).take(15).toByteArray().copyInto(packet, 25)
        packet[40] = 0
        packet[41] = 0
        packet[42] = 0
        mac.copyInto(packet, 43)
        return packet
    }
}
