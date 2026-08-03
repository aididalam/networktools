package com.nexdevlab.networktools

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexdevlab.networktools.data.WakeOnLanDeviceDraft
import com.nexdevlab.networktools.data.WakeOnLanDeviceStore
import com.nexdevlab.networktools.data.WakeOnLanMode
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WakeOnLanDeviceStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var store: WakeOnLanDeviceStore

    @Before
    fun setUp() {
        store = WakeOnLanDeviceStore(context)
        store.clear()
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun profilesPersistUpdateAndDeleteInDeterministicOrder() {
        val first = draft("Desktop", "00:11:22:33:44:50").validate(UUID.randomUUID().toString(), 10, 10)
        val second = draft("Server", "00:11:22:33:44:52").validate(UUID.randomUUID().toString(), 20, 20)
        store.upsert(first)
        store.upsert(second)

        assertEquals(listOf("Server", "Desktop"), store.load().map { it.name })
        store.upsert(first.copy(name = "Edited desktop", updatedAtMillis = 30))
        assertEquals(listOf("Edited desktop", "Server"), store.load().map { it.name })
        assertEquals(listOf("Edited desktop"), store.remove(second.id).map { it.name })
    }

    @Test
    fun storageIsBoundedToThirtyValidatedProfiles() {
        repeat(35) { index ->
            val mac = "02:11:22:33:44:${index.toString(16).padStart(2, '0')}"
            store.upsert(draft("Device $index", mac).validate(createdAtMillis = 1, nowMillis = (index + 1).toLong()))
        }

        assertEquals(WakeOnLanDeviceStore.MaximumDevices, store.load().size)
        assertEquals("Device 34", store.load().first().name)
    }

    @Test
    fun malformedStoredJsonFailsClosed() {
        context.getSharedPreferences(WakeOnLanDeviceStore.PreferencesName, android.content.Context.MODE_PRIVATE)
            .edit().putString("devices", "not-json").commit()

        assertTrue(store.load().isEmpty())
    }

    private fun draft(name: String, mac: String) = WakeOnLanDeviceDraft(
        name = name,
        macAddress = mac,
        targetAddress = "192.168.1.50",
        broadcastAddress = "192.168.1.255",
        udpPort = "9",
        mode = WakeOnLanMode.LOCAL,
    )
}
