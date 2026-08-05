package com.nexdevlab.networktools

import androidx.test.platform.app.InstrumentationRegistry
import com.nexdevlab.networktools.data.WifiAccessPoint
import com.nexdevlab.networktools.data.WifiBand
import com.nexdevlab.networktools.data.WifiSavedNetworkStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WifiSavedNetworkStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var store: WifiSavedNetworkStore

    @Before
    fun setUp() {
        store = WifiSavedNetworkStore(context)
        store.clear()
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun savedIdentitySurvivesStoreRecreationAndToggleRemovesIt() {
        store.toggle(accessPoint())

        val restored = WifiSavedNetworkStore(context).load()
        assertEquals(1, restored.size)
        assertEquals("Test Wi-Fi", restored.single().ssid)
        assertEquals("aa:bb:cc:dd:ee:01", restored.single().bssid)

        store.toggle(accessPoint())
        assertTrue(WifiSavedNetworkStore(context).load().isEmpty())
    }

    @Test
    fun scanObservationRefreshesSavedMetadataWithoutChangingIdentity() {
        store.toggle(accessPoint(lastSeenMillis = 100))

        val updated = store.updateSeen(
            listOf(accessPoint(ssid = "Renamed Wi-Fi", channel = 6, lastSeenMillis = 200)),
        ).single()

        assertEquals("aa:bb:cc:dd:ee:01", updated.bssid)
        assertEquals("Renamed Wi-Fi", updated.ssid)
        assertEquals(6, updated.channel)
        assertEquals(200, updated.lastSeenMillis)
    }

    @Test
    fun connectedObservationIsRecordedAutomaticallyAndUpdated() {
        assertTrue(store.recordConnected(listOf(accessPoint())).isEmpty())

        store.recordConnected(listOf(accessPoint(connected = true, lastSeenMillis = 100)))
        val updated = store.recordConnected(
            listOf(accessPoint(ssid = "Home renamed", channel = 6, connected = true, lastSeenMillis = 200)),
        ).single()

        assertEquals("Home renamed", updated.ssid)
        assertEquals(6, updated.channel)
        assertEquals(200, updated.lastSeenMillis)
    }

    private fun accessPoint(
        ssid: String = "Test Wi-Fi",
        channel: Int = 1,
        lastSeenMillis: Long = 100,
        connected: Boolean = false,
    ) = WifiAccessPoint(
        ssid = ssid,
        hidden = false,
        bssid = "aa:bb:cc:dd:ee:01",
        levelDbm = -50,
        frequencyMhz = if (channel == 1) 2_412 else 2_437,
        channel = channel,
        band = WifiBand.BAND_2_4_GHZ,
        security = "WPA2 Personal",
        channelWidth = "20 MHz",
        channelWidthMhz = 20,
        lastSeenMillis = lastSeenMillis,
        connected = connected,
    )
}
