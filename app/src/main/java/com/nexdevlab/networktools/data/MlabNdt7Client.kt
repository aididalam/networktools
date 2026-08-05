package com.nexdevlab.networktools.data

import android.net.Network
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject

internal data class Ndt7RunResult(
    val download: SpeedProgress,
    val upload: SpeedProgress,
    val rttSamplesMillis: List<Double>,
)

internal data class MlabServerDiscovery(
    val servers: List<SpeedTestServer>,
    val regionalFallback: Boolean,
)

internal class MlabNdt7Client(private val network: Network) {
    private val client = OkHttpClient.Builder()
        .socketFactory(network.socketFactory)
        .dns(object : Dns {
            override fun lookup(hostname: String) = network.getAllByName(hostname).toList()
        })
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun discover(choice: SpeedServerChoice): MlabServerDiscovery {
        val countryCode = choice.countryCode
        if (countryCode == null) {
            return MlabServerDiscovery(
                servers = discoverCandidates(choice, strictCountry = false),
                regionalFallback = false,
            )
        }

        val inCountry = discoverCandidates(choice, strictCountry = true, allowEmpty = true)
        if (inCountry.isNotEmpty()) return MlabServerDiscovery(inCountry, regionalFallback = false)

        return MlabServerDiscovery(
            servers = discoverCandidates(choice, strictCountry = false),
            regionalFallback = true,
        )
    }

    private suspend fun discoverCandidates(
        choice: SpeedServerChoice,
        strictCountry: Boolean,
        allowEmpty: Boolean = false,
    ): List<SpeedTestServer> = withContext(Dispatchers.IO) {
        val url = LocateBaseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("client_name", "networktools-android")
            .addQueryParameter("client_version", "1.0")
            .apply {
                choice.countryCode?.let {
                    addQueryParameter("country", it)
                    if (strictCountry) addQueryParameter("strict", "true")
                }
            }
            .build()
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        val call = client.newCall(request)
        try {
            val response = call.await()
            response.use {
                if (!it.isSuccessful) {
                    if (allowEmpty) return@use emptyList()
                    throw IOException("Server discovery is temporarily unavailable")
                }
                if (it.code == 204 && allowEmpty) return@use emptyList()
                val body = it.body ?: if (allowEmpty) return@use emptyList()
                else throw IOException("The server directory returned an empty response")
                if (body.contentLength() > MaximumLocateBytes) throw IOException("The server directory response was too large")
                val bytes = readBounded(body.byteStream(), MaximumLocateBytes)
                if (bytes.size > MaximumLocateBytes) throw IOException("The server directory response was too large")
                val servers = parseLocateResponse(
                    bytes.toString(Charsets.UTF_8),
                    requestedCountry = choice.countryCode.takeIf { strictCountry },
                )
                if (servers.isEmpty() && !allowEmpty) {
                    throw IOException("No test server is currently available for this selection")
                }
                servers
            }
        } catch (cancelled: CancellationException) {
            call.cancel()
            throw cancelled
        }
    }

    suspend fun run(
        server: SpeedTestServer,
        networkIsCurrent: () -> Boolean,
        onProgress: (SpeedProgress) -> Unit,
    ): Ndt7RunResult {
        if (!networkIsCurrent()) throw IOException("The active network changed before the test started")
        val download = runDownload(server.downloadUrl, networkIsCurrent, onProgress)
        if (!networkIsCurrent()) throw IOException("The active network changed during the test")
        val upload = runUpload(server.uploadUrl, networkIsCurrent, onProgress)
        return Ndt7RunResult(
            download = download.progress,
            upload = upload.progress,
            rttSamplesMillis = download.rttSamples + upload.rttSamples,
        )
    }

    suspend fun rankByLatency(servers: List<SpeedTestServer>): List<SpeedTestServer> = coroutineScope {
        val measurements = servers.map { server ->
            async(Dispatchers.IO) { server to probeTcpLatency(server.endpointHost) }
        }.awaitAll().toMap()
        rankSpeedServers(servers, measurements)
    }

    fun close() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
    }

    private fun probeTcpLatency(hostname: String): Long? = runCatching {
        val address = network.getAllByName(hostname).firstOrNull() ?: return null
        buildList {
            repeat(LatencyProbeAttempts) {
                val started = System.nanoTime()
                network.socketFactory.createSocket().use { socket ->
                    socket.connect(InetSocketAddress(address, 443), LatencyProbeTimeoutMillis)
                }
                add(((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(1L))
            }
        }.minOrNull()
    }.getOrNull()

    private suspend fun runDownload(
        url: String,
        networkIsCurrent: () -> Boolean,
        onProgress: (SpeedProgress) -> Unit,
    ): SocketMeasurement = withTimeout(25_000L) {
        runSocket(url, SpeedTestPhase.DOWNLOAD, networkIsCurrent, onProgress, upload = false)
    }

    private suspend fun runUpload(
        url: String,
        networkIsCurrent: () -> Boolean,
        onProgress: (SpeedProgress) -> Unit,
    ): SocketMeasurement = withTimeout(25_000L) {
        runSocket(url, SpeedTestPhase.UPLOAD, networkIsCurrent, onProgress, upload = true)
    }

    private suspend fun runSocket(
        url: String,
        phase: SpeedTestPhase,
        networkIsCurrent: () -> Boolean,
        onProgress: (SpeedProgress) -> Unit,
        upload: Boolean,
    ): SocketMeasurement = coroutineScope {
        val completed = CompletableDeferred<SocketMeasurement>()
        val closed = AtomicBoolean(false)
        val rttSamples = mutableListOf<Double>()
        var startedNanos = 0L
        val transferredBytes = AtomicLong(0L)
        var lastUpdateNanos = 0L
        var sender: Job? = null

        fun publish(force: Boolean = false) {
            if (startedNanos == 0L) return
            val now = System.nanoTime()
            if (!force && now - lastUpdateNanos < ProgressIntervalNanos) return
            lastUpdateNanos = now
            val elapsed = ((now - startedNanos) / 1_000L).coerceAtLeast(1L)
            onProgress(SpeedProgress(phase, transferredBytes.get().coerceAtLeast(0), elapsed,
                rttMillis = rttSamples.lastOrNull()))
        }

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!networkIsCurrent()) {
                    webSocket.cancel()
                    completed.completeExceptionally(IOException("The active network changed during the test"))
                    return
                }
                startedNanos = System.nanoTime()
                publish(force = true)
                if (upload) {
                    sender = launch(Dispatchers.IO) {
                        var payload = ByteArray(8 * 1_024).toByteString()
                        try {
                            while (System.nanoTime() - startedNanos < UploadDurationNanos) {
                                ensureActive()
                                if (!networkIsCurrent()) throw IOException("The active network changed during the test")
                                if (webSocket.queueSize() > MaximumQueueBytes) {
                                    delay(2)
                                    continue
                                }
                                if (!webSocket.send(payload)) throw IOException("The upload connection closed")
                                transferredBytes.addAndGet(payload.size.toLong())
                                publish()
                                if (payload.size < MaximumPayloadBytes && webSocket.queueSize() < payload.size * 2L) {
                                    payload = ByteArray((payload.size * 2).coerceAtMost(MaximumPayloadBytes)).toByteString()
                                }
                            }
                            publish(force = true)
                            webSocket.close(1000, "complete")
                        } catch (cancelled: CancellationException) {
                            webSocket.cancel()
                        } catch (failure: Exception) {
                            webSocket.cancel()
                            completed.completeExceptionally(failure)
                        }
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!upload) transferredBytes.addAndGet(text.toByteArray(Charsets.UTF_8).size.toLong())
                parseRttMillis(text)?.let { synchronized(rttSamples) { rttSamples += it } }
                publish()
                if (!networkIsCurrent()) {
                    webSocket.cancel()
                    completed.completeExceptionally(IOException("The active network changed during the test"))
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (!upload) transferredBytes.addAndGet(bytes.size.toLong())
                publish()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!closed.compareAndSet(false, true)) return
                sender?.cancel()
                publish(force = true)
                val elapsed = ((System.nanoTime() - startedNanos) / 1_000L).coerceAtLeast(1L)
                val deliveredBytes = if (upload) {
                    (transferredBytes.get() - webSocket.queueSize()).coerceAtLeast(0L)
                } else transferredBytes.get()
                if (code != 1000 || deliveredBytes == 0L) {
                    completed.completeExceptionally(IOException("The ${phase.label.lowercase()} did not complete"))
                } else {
                    completed.complete(
                        SocketMeasurement(
                            SpeedProgress(phase, deliveredBytes, elapsed, rttMillis = rttSamples.lastOrNull()),
                            synchronized(rttSamples) { rttSamples.toList() },
                        ),
                    )
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!closed.compareAndSet(false, true)) return
                sender?.cancel()
                completed.completeExceptionally(IOException("The ${phase.label.lowercase()} connection failed", t))
            }
        }
        val request = Request.Builder()
            .url(url)
            .header("Sec-WebSocket-Protocol", Ndt7Protocol)
            .build()
        val socket = client.newWebSocket(request, listener)
        completed.invokeOnCompletion { if (it is CancellationException) socket.cancel() }
        try {
            completed.await()
        } finally {
            if (!completed.isCompleted) socket.cancel()
            sender?.cancel()
        }
    }

    private data class SocketMeasurement(val progress: SpeedProgress, val rttSamples: List<Double>)

    companion object {
        private const val LocateBaseUrl = "https://locate.measurementlab.net/v2/nearest/ndt/ndt7"
        private const val Ndt7Protocol = "net.measurementlab.ndt.v7"
        private const val MaximumLocateBytes = 128 * 1_024
        private const val ProgressIntervalNanos = 250_000_000L
        private const val UploadDurationNanos = 10_000_000_000L
        private const val MaximumQueueBytes = 4L * 1_024L * 1_024L
        private const val MaximumPayloadBytes = 1 * 1_024 * 1_024
        private const val MaximumCandidates = 4
        private const val LatencyProbeAttempts = 2
        private const val LatencyProbeTimeoutMillis = 1_500

        internal fun parseLocateResponse(json: String, requestedCountry: String? = null): List<SpeedTestServer> {
            if (json.toByteArray(Charsets.UTF_8).size > MaximumLocateBytes) return emptyList()
            return runCatching {
                val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
                buildList {
                    for (index in 0 until minOf(results.length(), MaximumCandidates)) {
                        val item = results.optJSONObject(index) ?: continue
                        val machine = item.optString("machine").clean(253)
                        val endpointHost = item.optString("hostname").clean(253).ifBlank { machine }
                        val location = item.optJSONObject("location")
                        val city = location?.optString("city").orEmpty().clean(80)
                        val country = location?.optString("country").orEmpty().uppercase().take(2)
                        val urls = item.optJSONObject("urls") ?: continue
                        val download = urls.optString("wss:///ndt/v7/download")
                        val upload = urls.optString("wss:///ndt/v7/upload")
                        if (requestedCountry != null && country != requestedCountry) continue
                        if (!validEndpoint(download, endpointHost, "/ndt/v7/download")) continue
                        if (!validEndpoint(upload, endpointHost, "/ndt/v7/upload")) continue
                        add(SpeedTestServer(machine, city, country, endpointHost, download, upload))
                    }
                }
            }.getOrDefault(emptyList())
        }

        internal fun validEndpoint(value: String, machine: String, path: String): Boolean {
            if (!value.startsWith("wss://")) return false
            val url = value.replaceFirst("wss://", "https://").toHttpUrlOrNull() ?: return false
            val safeMachine = machine.lowercase()
            val safeHost = url.host.lowercase()
            return url.scheme == "https" &&
                safeHost == safeMachine &&
                safeHost.endsWith(".measurement-lab.org") &&
                url.encodedPath == path &&
                !url.queryParameter("access_token").isNullOrBlank() &&
                url.fragment == null
        }

        private fun String.clean(maximum: Int): String = filter { !it.isISOControl() }.trim().take(maximum)

        private fun parseRttMillis(value: String): Double? = runCatching {
            val json = JSONObject(value)
            val tcp = json.optJSONObject("TCPInfo") ?: return null
            tcp.optDouble("RTT", Double.NaN).takeIf { it.isFinite() && it in 0.0..60_000_000.0 }?.div(1_000.0)
        }.getOrNull()

        private fun readBounded(input: java.io.InputStream, maximumBytes: Int): ByteArray {
            val output = ByteArrayOutputStream(minOf(maximumBytes, 16 * 1_024))
            val buffer = ByteArray(8 * 1_024)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > maximumBytes) return ByteArray(maximumBytes + 1)
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (continuation.isActive) continuation.resume(response) else response.close()
        }
    })
}
