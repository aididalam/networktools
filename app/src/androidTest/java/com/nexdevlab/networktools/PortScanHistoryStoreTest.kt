package com.nexdevlab.networktools

import androidx.test.platform.app.InstrumentationRegistry
import com.nexdevlab.networktools.data.PortScanEntry
import com.nexdevlab.networktools.data.PortScanHistoryStore
import com.nexdevlab.networktools.data.PortScanResult
import com.nexdevlab.networktools.data.PortScanStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PortScanHistoryStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var store: PortScanHistoryStore

    @Before
    fun setUp() {
        store = PortScanHistoryStore(context)
        store.clear()
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun resultPersistsOnlyBoundedSummaryAndOpenPorts() {
        store.add(result())

        val restored = PortScanHistoryStore(context).load().single()

        assertEquals(3, restored.scannedCount)
        assertEquals(1, restored.closedCount)
        assertEquals(1, restored.timedOutCount)
        assertEquals(listOf(443), restored.openPorts.map(PortScanEntry::port))
    }

    @Test
    fun duplicateIdIsDeduplicatedAndRemovalPersists() {
        store.add(result())
        store.add(result())
        assertEquals(1, store.load().size)

        store.remove(100)
        assertTrue(PortScanHistoryStore(context).load().isEmpty())
    }

    private fun result() = PortScanResult(
        target = "router.local",
        resolvedAddress = "192.168.1.1",
        entries = listOf(
            PortScanEntry(22, PortScanStatus.CLOSED, "SSH"),
            PortScanEntry(80, PortScanStatus.TIMED_OUT, "HTTP"),
            PortScanEntry(443, PortScanStatus.OPEN, "HTTPS", 2.0),
        ),
        timeoutMillis = 500,
        stopped = false,
        completedAtMillis = 100,
    )
}
