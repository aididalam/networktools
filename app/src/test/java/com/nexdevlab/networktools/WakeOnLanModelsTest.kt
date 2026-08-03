package com.nexdevlab.networktools

import com.nexdevlab.networktools.data.WakeMacAddress
import com.nexdevlab.networktools.data.WakeMagicPacket
import com.nexdevlab.networktools.data.WakeOnLanDeviceDraft
import com.nexdevlab.networktools.data.WakeOnLanMode
import com.nexdevlab.networktools.data.isPublicUnicast
import java.net.InetAddress
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeOnLanModelsTest {
    @Test
    fun macAddressFormatsAreNormalized() {
        listOf("00:11:22:33:44:55", "00-11-22-33-44-55", "001122334455").forEach {
            assertEquals("00:11:22:33:44:55", WakeMacAddress.parse(it).value)
        }
    }

    @Test
    fun malformedMulticastAndPlaceholderMacsAreRejected() {
        listOf("bad", "01:00:5E:00:00:01", "00:00:00:00:00:00", "FF:FF:FF:FF:FF:FF").forEach {
            assertThrows(IllegalArgumentException::class.java) { WakeMacAddress.parse(it) }
        }
    }

    @Test
    fun magicPacketHasSixFfBytesAndSixteenMacCopies() {
        val mac = byteArrayOf(0x00, 0x11, 0x22, 0x33, 0x44, 0x55)
        val packet = WakeMagicPacket.build("00:11:22:33:44:55")

        assertEquals(102, packet.size)
        assertArrayEquals(ByteArray(6) { 0xff.toByte() }, packet.copyOfRange(0, 6))
        repeat(16) { index -> assertArrayEquals(mac, packet.copyOfRange(6 + index * 6, 12 + index * 6)) }
    }

    @Test
    fun localDraftNormalizesAndRetainsAllPacketFields() {
        val device = localDraft().validate(existingId = "12345678-1234-1234-1234-123456789abc", createdAtMillis = 10, nowMillis = 20)

        assertEquals("Office PC", device.name)
        assertEquals("00:11:22:33:44:55", device.macAddress)
        assertEquals("192.168.1.50", device.targetAddress)
        assertEquals("192.168.1.255", device.destination)
        assertEquals(9, device.udpPort)
        assertEquals(WakeOnLanMode.LOCAL, device.mode)
    }

    @Test
    fun validNon24BroadcastIsAcceptedForActiveNetworkValidation() {
        val device = localDraft().copy(broadcastAddress = "10.0.0.3").validate()

        assertEquals("10.0.0.3", device.broadcastAddress)
    }

    @Test
    fun localTargetAndBroadcastMustBePrivateNumericIpv4() {
        listOf(
            localDraft().copy(targetAddress = "8.8.8.8"),
            localDraft().copy(targetAddress = "host.local"),
            localDraft().copy(broadcastAddress = "8.8.8.8"),
            localDraft().copy(broadcastAddress = "https://192.168.1.255"),
        ).forEach { draft -> assertThrows(IllegalArgumentException::class.java) { draft.validate() } }
    }

    @Test
    fun remoteDraftAcceptsExplicitHostnameAndClearsBroadcast() {
        val device = localDraft().copy(
            mode = WakeOnLanMode.REMOTE,
            targetAddress = "WOL.Example.com.",
            broadcastAddress = "192.168.1.255",
            udpPort = "7",
        ).validate()

        assertEquals("wol.example.com", device.targetAddress)
        assertEquals("", device.broadcastAddress)
        assertEquals("wol.example.com", device.destination)
        assertEquals(7, device.udpPort)
    }

    @Test
    fun remoteUrlPathCredentialAndWhitespaceAreRejected() {
        listOf("https://example.com", "example.com/wol", "user@example.com", "bad host").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                localDraft().copy(mode = WakeOnLanMode.REMOTE, targetAddress = value).validate()
            }
        }
    }

    @Test
    fun udpPortBoundariesAreAccepted() {
        assertEquals(1, localDraft().copy(udpPort = "1").validate().udpPort)
        assertEquals(65535, localDraft().copy(udpPort = "65535").validate().udpPort)
    }

    @Test
    fun invalidPortsAreRejected() {
        listOf("", "0", "65536", "nine").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { localDraft().copy(udpPort = value).validate() }
        }
    }

    @Test
    fun friendlyNameIsRequiredAndBounded() {
        assertThrows(IllegalArgumentException::class.java) { localDraft().copy(name = " ").validate() }
        assertThrows(IllegalArgumentException::class.java) {
            localDraft().copy(name = "x".repeat(WakeOnLanDeviceDraft.MaximumNameLength + 1)).validate()
        }
    }

    @Test
    fun validatedRejectsTamperedPersistedProfile() {
        val device = localDraft().validate()

        assertThrows(IllegalArgumentException::class.java) { device.copy(macAddress = "bad").validated() }
        assertThrows(IllegalArgumentException::class.java) { device.copy(udpPort = 0).validated() }
    }

    @Test
    fun publicUnicastClassificationRejectsLocalAndMulticast() {
        assertTrue(InetAddress.getByName("8.8.8.8").isPublicUnicast())
        assertTrue(InetAddress.getByName("2001:4860:4860::8888").isPublicUnicast())
        listOf(
            "127.0.0.1", "192.168.1.1", "169.254.1.1", "100.64.0.1", "192.0.2.1",
            "198.18.0.1", "198.51.100.1", "203.0.113.1", "224.0.0.1", "fd00::1", "fe80::1", "2001:db8::1",
        ).forEach {
            assertFalse(InetAddress.getByName(it).isPublicUnicast())
        }
    }

    private fun localDraft() = WakeOnLanDeviceDraft(
        name = " Office PC ",
        macAddress = "00-11-22-33-44-55",
        targetAddress = "192.168.1.50",
        broadcastAddress = "192.168.1.255",
        udpPort = "9",
        mode = WakeOnLanMode.LOCAL,
    )
}
