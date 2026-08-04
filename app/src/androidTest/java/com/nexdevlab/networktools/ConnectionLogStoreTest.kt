package com.nexdevlab.networktools

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexdevlab.networktools.data.ConnectionKind
import com.nexdevlab.networktools.data.ConnectionLogEntry
import com.nexdevlab.networktools.data.ConnectionLogStore
import com.nexdevlab.networktools.data.ConnectionStateSnapshot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectionLogStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var store: ConnectionLogStore

    @Before fun setUp() { store = ConnectionLogStore(context); store.clear() }
    @After fun tearDown() { store.clear() }

    @Test fun entriesPersistNewestFirstAndRemainBounded() {
        repeat(110) { index ->
            val current = ConnectionStateSnapshot(if (index % 2 == 0) ConnectionKind.WIFI else ConnectionKind.MOBILE)
            store.add(requireNotNull(ConnectionLogEntry.transition(null, current, index + 1L, index + 1L)))
        }
        assertEquals(100, store.load().size)
        assertEquals(110L, store.load().first().id)
    }

    @Test fun malformedJsonFailsClosed() {
        context.getSharedPreferences(ConnectionLogStore.PreferencesName, Context.MODE_PRIVATE)
            .edit().putString("entries", "bad-json").commit()
        assertTrue(store.load().isEmpty())
    }
}

