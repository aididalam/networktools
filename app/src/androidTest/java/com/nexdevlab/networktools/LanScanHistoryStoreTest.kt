package com.nexdevlab.networktools

import androidx.test.platform.app.InstrumentationRegistry
import com.nexdevlab.networktools.data.LanDevice
import com.nexdevlab.networktools.data.LanDiscoveryProtocol
import com.nexdevlab.networktools.data.LanScanHistoryStore
import com.nexdevlab.networktools.data.LanScanMode
import com.nexdevlab.networktools.data.LanScanResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LanScanHistoryStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var store: LanScanHistoryStore

    @Before
    fun setUp() {
        store = LanScanHistoryStore(context)
        store.clear()
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun completedScanSurvivesStoreRecreationWithDeviceEvidence() {
        store.add(result(100))

        val restored = LanScanHistoryStore(context).load().single()

        assertEquals("192.168.1.0/24", restored.subnet)
        assertEquals(1, restored.devices.size)
        assertEquals("aa:bb:cc:dd:ee:01", restored.devices.single().macAddress)
        assertEquals(setOf(LanDiscoveryProtocol.ARP, LanDiscoveryProtocol.PING), restored.devices.single().protocols)
    }

    @Test
    fun duplicateIdIsReplacedAndRemovalPersists() {
        store.add(result(100))
        store.add(result(100))
        assertEquals(1, store.load().size)

        store.remove(100)
        assertTrue(LanScanHistoryStore(context).load().isEmpty())
    }

    private fun result(id: Long) = LanScanResult(
        subnet = "192.168.1.0/24",
        mode = LanScanMode.FAST,
        devices = listOf(
            LanDevice(
                ipAddress = "192.168.1.1",
                macAddress = "aa:bb:cc:dd:ee:01",
                deviceType = "Router / gateway",
                protocols = setOf(LanDiscoveryProtocol.ARP, LanDiscoveryProtocol.PING),
                gateway = true,
                lastSeenMillis = id,
            ),
        ),
        scannedHosts = 254,
        stopped = false,
        completedAtMillis = id,
    )
}
