package com.nexdevlab.networktools

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexdevlab.networktools.data.SpeedTestHistoryStore
import com.nexdevlab.networktools.data.SpeedTestResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpeedTestHistoryStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var store: SpeedTestHistoryStore

    @Before fun setUp() { store = SpeedTestHistoryStore(context); store.clear() }
    @After fun tearDown() { store.clear() }

    @Test fun historyIsNewestFirstBoundedAndNeverStoresSignedUrls() {
        repeat(25) { index ->
            store.add(result(index + 1L, machine = "ndt-$index.measurement-lab.org"))
        }
        val history = store.load()
        assertEquals(20, history.size)
        assertEquals(25L, history.first().completedAtMillis)
        val raw = context.getSharedPreferences(SpeedTestHistoryStore.PreferencesName, Context.MODE_PRIVATE)
            .getString("entries", "").orEmpty()
        assertFalse(raw.contains("access_token"))
        assertFalse(raw.contains("wss://"))
    }

    @Test fun malformedHistoryFailsClosed() {
        context.getSharedPreferences(SpeedTestHistoryStore.PreferencesName, Context.MODE_PRIVATE)
            .edit().putString("entries", "not-json").commit()
        assertTrue(store.load().isEmpty())
    }

    private fun result(time: Long, machine: String) = SpeedTestResult(
        downloadMbps = 80.0,
        uploadMbps = 20.0,
        latencyMillis = 10.0,
        jitterMillis = 1.0,
        serverCity = "Dhaka",
        serverCountryCode = "BD",
        serverMachine = machine,
        networkType = "Wi-Fi",
        completedAtMillis = time,
    )
}
