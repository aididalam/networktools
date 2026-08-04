package com.nexdevlab.networktools

import com.nexdevlab.networktools.data.ConnectionEventType
import com.nexdevlab.networktools.data.ConnectionKind
import com.nexdevlab.networktools.data.ConnectionLogEntry
import com.nexdevlab.networktools.data.ConnectionLogFilter
import com.nexdevlab.networktools.data.ConnectionLogUiState
import com.nexdevlab.networktools.data.ConnectionStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionLogModelsTest {
    private val wifi = ConnectionStateSnapshot(ConnectionKind.WIFI, validated = true)
    private val mobile = ConnectionStateSnapshot(ConnectionKind.MOBILE, metered = true, validated = true)

    @Test fun equalSnapshotsAreCoalesced() = assertNull(ConnectionLogEntry.transition(wifi, wifi, 1))

    @Test fun transitionsAreClassified() {
        assertEquals(ConnectionEventType.CONNECTED, ConnectionLogEntry.transition(null, wifi, 1)?.event)
        assertEquals(ConnectionEventType.SWITCHED, ConnectionLogEntry.transition(wifi, mobile, 2)?.event)
        assertEquals(ConnectionEventType.UPDATED,
            ConnectionLogEntry.transition(wifi, wifi.copy(validated = false), 3)?.event)
        assertEquals(ConnectionEventType.DISCONNECTED,
            ConnectionLogEntry.transition(mobile, ConnectionStateSnapshot(ConnectionKind.OFFLINE), 4)?.event)
    }

    @Test fun filtersIncludeEitherSideOfSwitch() {
        val entry = requireNotNull(ConnectionLogEntry.transition(wifi, mobile, 2))
        val state = ConnectionLogUiState(entries = listOf(entry))
        assertEquals(1, state.filtered(ConnectionLogFilter.WIFI).size)
        assertEquals(1, state.filtered(ConnectionLogFilter.MOBILE).size)
    }
}

