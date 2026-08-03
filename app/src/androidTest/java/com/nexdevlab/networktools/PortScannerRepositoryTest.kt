package com.nexdevlab.networktools

import androidx.test.platform.app.InstrumentationRegistry
import com.nexdevlab.networktools.data.PortScanConfig
import com.nexdevlab.networktools.data.PortScanStatus
import com.nexdevlab.networktools.data.PortScannerRepository
import com.nexdevlab.networktools.data.PortSelectionMode
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortScannerRepositoryDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun controlledLoopbackServerIsDetectedWithoutPayload() = runBlocking {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 3_000 }
        try {
            val received = async(Dispatchers.IO) {
                server.accept().use { socket ->
                    socket.soTimeout = 1_000
                    try {
                        socket.getInputStream().read()
                    } catch (_: SocketTimeoutException) {
                        Int.MIN_VALUE
                    }
                }
            }
            val config = PortScanConfig(
                target = "127.0.0.1",
                selectionMode = PortSelectionMode.SINGLE,
                singlePort = server.localPort.toString(),
                timeoutMillis = 500,
                authorized = true,
            )
            val repository = PortScannerRepository(context)
            val prepared = repository.prepare(config)
            val result = repository.scan(prepared) {}

            assertEquals(PortScanStatus.OPEN, result.entries.single().status)
            assertEquals(-1, received.await())
        } finally {
            server.close()
        }
    }

    @Test
    fun recentlyClosedLoopbackPortIsReportedClosed() = runBlocking {
        val port = withContext(Dispatchers.IO) { ServerSocket(0).use { it.localPort } }
        val repository = PortScannerRepository(context)
        val prepared = repository.prepare(
            PortScanConfig(
                target = "localhost",
                selectionMode = PortSelectionMode.SINGLE,
                singlePort = port.toString(),
                timeoutMillis = 500,
                authorized = true,
            ),
        )
        val result = repository.scan(prepared) {}

        assertTrue(result.entries.single().status in setOf(PortScanStatus.CLOSED, PortScanStatus.TIMED_OUT))
    }
}
