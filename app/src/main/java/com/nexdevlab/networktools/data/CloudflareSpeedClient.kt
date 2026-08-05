package com.nexdevlab.networktools.data

import android.net.Network
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.Buffer

internal data class LocalEdgeInfo(
    val edgeCode: String,
    val countryCode: String,
) {
    val edgeName: String
        get() = EdgeCities[edgeCode] ?: "$edgeCode edge"

    fun asServer() = SpeedTestServer(
        machine = edgeCode.ifBlank { SpeedHost },
        city = edgeName,
        countryCode = countryCode,
        endpointHost = SpeedHost,
        downloadUrl = DownloadUrl,
        uploadUrl = UploadUrl,
    )
}

internal data class EdgeSpeedRun(
    val download: SpeedProgress,
    val upload: SpeedProgress,
    val rttSamplesMillis: List<Double>,
)

/** Nearest-edge measurements based on Cloudflare's MIT-licensed speedtest engine. */
internal class CloudflareSpeedClient(private val network: Network) {
    private val client = OkHttpClient.Builder()
        .socketFactory(network.socketFactory)
        .dns(object : Dns {
            override fun lookup(hostname: String) = network.getAllByName(hostname).toList()
        })
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun resolveEdge(): LocalEdgeInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(measurementUrl(0)).header("Accept", "application/octet-stream").build()
        val call = client.newCall(request)
        try {
            call.await().use { response ->
                if (!response.isSuccessful) throw IOException("The nearest test server is temporarily unavailable")
                response.body?.close()
                parseEdgeHeaders(response.headers.toMultimap())
            }
        } catch (cancelled: CancellationException) {
            call.cancel()
            throw cancelled
        }
    }

    suspend fun run(
        networkIsCurrent: () -> Boolean,
        onProgress: (SpeedProgress) -> Unit,
    ): EdgeSpeedRun = withContext(Dispatchers.IO) {
        ensureNetworkCurrent(networkIsCurrent)
        val latency = buildList {
            repeat(LatencySamples) {
                currentCoroutineContext().ensureActive()
                ensureNetworkCurrent(networkIsCurrent)
                add(measureLatency())
            }
        }
        val medianLatency = percentile(latency, 0.5)
        val download = measureDirection(
            phase = SpeedTestPhase.DOWNLOAD,
            rounds = DownloadRounds,
            networkIsCurrent = networkIsCurrent,
            rttMillis = medianLatency,
            onProgress = onProgress,
        )
        ensureNetworkCurrent(networkIsCurrent)
        val upload = measureDirection(
            phase = SpeedTestPhase.UPLOAD,
            rounds = UploadRounds,
            networkIsCurrent = networkIsCurrent,
            rttMillis = medianLatency,
            onProgress = onProgress,
        )
        EdgeSpeedRun(download, upload, latency)
    }

    fun close() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
    }

    private suspend fun measureLatency(): Double {
        var lastFailure: IOException? = null
        repeat(MaximumMeasurementAttempts) { attempt ->
            try {
                val started = System.nanoTime()
                val request = Request.Builder().url(measurementUrl(0)).build()
                client.newCall(request).await().use { response ->
                    if (!response.isSuccessful) throw IOException("Latency measurement failed (${response.code})")
                    response.body?.close()
                    val elapsedMillis = (System.nanoTime() - started) / 1_000_000.0
                    return (elapsedMillis - response.serverTimeMillis()).coerceAtLeast(0.0)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: IOException) {
                lastFailure = failure
                if (attempt + 1 < MaximumMeasurementAttempts) delay(RetryDelayMillis * (attempt + 1))
            }
        }
        throw lastFailure ?: IOException("Latency measurement failed")
    }

    private suspend fun measureDirection(
        phase: SpeedTestPhase,
        rounds: List<MeasurementRound>,
        networkIsCurrent: () -> Boolean,
        rttMillis: Double,
        onProgress: (SpeedProgress) -> Unit,
    ): SpeedProgress {
        val phaseStarted = System.nanoTime()
        var totalBytes = 0L
        val samples = mutableListOf<Double>()
        var hasUsableSample = false
        roundLoop@ for (round in rounds) {
            var minimumDurationMillis = Double.POSITIVE_INFINITY
            var measurementIndex = 0
            while (measurementIndex < round.count) {
                currentCoroutineContext().ensureActive()
                ensureNetworkCurrent(networkIsCurrent)
                val sample = try {
                    measureTransfer(phase, round.bytes) { delta ->
                        totalBytes = (totalBytes + delta).coerceAtLeast(0L)
                        val elapsedMicros = ((System.nanoTime() - phaseStarted) / 1_000L).coerceAtLeast(1L)
                        onProgress(SpeedProgress(phase, totalBytes, elapsedMicros, rttMillis = rttMillis))
                    }
                } catch (failure: IOException) {
                    ensureNetworkCurrent(networkIsCurrent)
                    if (!hasUsableSample) throw failure
                    break@roundLoop
                }
                minimumDurationMillis = minOf(minimumDurationMillis, sample.durationMillis)
                if (sample.durationMillis >= MinimumSampleDurationMillis) {
                    samples += sample.mbps
                    if (!round.warmUp) hasUsableSample = true
                }
                measurementIndex++
            }
            if (!round.warmUp && minimumDurationMillis >= FinishSetDurationMillis) break
        }
        if (samples.isEmpty()) throw IOException("The ${phase.name.lowercase(Locale.US)} measurement was too short")
        val elapsedMicros = ((System.nanoTime() - phaseStarted) / 1_000L).coerceAtLeast(1L)
        return SpeedProgress(
            phase = phase,
            bytes = totalBytes,
            elapsedMicros = elapsedMicros,
            megabitsPerSecond = percentile(samples, BandwidthPercentile),
            rttMillis = rttMillis,
        ).also(onProgress)
    }

    private suspend fun measureTransfer(
        phase: SpeedTestPhase,
        bytes: Long,
        onBytes: (Long) -> Unit,
    ): TransferSample {
        var lastFailure: IOException? = null
        repeat(MaximumMeasurementAttempts) { attempt ->
            var attemptBytes = 0L
            try {
                return measureTransferOnce(phase, bytes) { delta ->
                    attemptBytes += delta
                    onBytes(delta)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: IOException) {
                if (attemptBytes > 0) onBytes(-attemptBytes)
                lastFailure = failure
                if (attempt + 1 < MaximumMeasurementAttempts) delay(RetryDelayMillis * (attempt + 1))
            }
        }
        throw lastFailure ?: IOException("The ${phase.name.lowercase(Locale.US)} measurement failed")
    }

    private suspend fun measureTransferOnce(
        phase: SpeedTestPhase,
        bytes: Long,
        onBytes: (Long) -> Unit,
    ): TransferSample {
        val started = System.nanoTime()
        val request = if (phase == SpeedTestPhase.DOWNLOAD) {
            Request.Builder().url(measurementUrl(bytes)).build()
        } else {
            Request.Builder()
                .url(UploadUrl.toHttpUrl().newBuilder().addQueryParameter("bytes", bytes.toString()).build())
                .post(CountingRequestBody(bytes, onBytes))
                .build()
        }
        var transferred = 0L
        client.newCall(request).await().use { response ->
            if (!response.isSuccessful) {
                throw IOException("The ${phase.name.lowercase(Locale.US)} measurement failed (${response.code})")
            }
            if (phase == SpeedTestPhase.DOWNLOAD) {
                val source = response.body?.source() ?: throw IOException("The test server returned an empty response")
                val buffer = Buffer()
                while (true) {
                    val read = source.read(buffer, ReadBufferBytes)
                    if (read < 0) break
                    buffer.clear()
                    transferred += read
                    onBytes(read)
                }
                if (transferred != bytes) throw IOException("The test server returned incomplete measurement data")
            } else {
                response.body?.close()
                transferred = bytes
                val accepted = response.header("cf-meta-upload-bytes")?.toLongOrNull()
                if (accepted != null && accepted != bytes) {
                    throw IOException("The test server did not receive the complete measurement payload")
                }
            }
            val elapsedMillis = (System.nanoTime() - started) / 1_000_000.0
            // Android's request timer includes the actual body transfer. Some edge responses also
            // include that transfer in Server-Timing, so subtracting it can create impossible rates.
            val duration = elapsedMillis.coerceAtLeast(0.1)
            return TransferSample(duration, transferred * 8.0 / duration / 1_000.0)
        }
    }

    private fun measurementUrl(bytes: Long) = DownloadUrl.toHttpUrl().newBuilder()
        .addQueryParameter("bytes", bytes.toString())
        .addQueryParameter("r", System.nanoTime().toString())
        .build()

    private fun ensureNetworkCurrent(networkIsCurrent: () -> Boolean) {
        if (!networkIsCurrent()) throw IOException("The active network changed during the test")
    }
}

private data class MeasurementRound(val bytes: Long, val count: Int, val warmUp: Boolean = false)
private data class TransferSample(val durationMillis: Double, val mbps: Double)

private class CountingRequestBody(
    private val byteCount: Long,
    private val onBytes: (Long) -> Unit,
) : RequestBody() {
    override fun contentType() = "application/octet-stream".toMediaType()
    override fun contentLength() = byteCount

    override fun writeTo(sink: BufferedSink) {
        var remaining = byteCount
        while (remaining > 0) {
            val count = minOf(remaining, UploadBuffer.size.toLong()).toInt()
            sink.write(UploadBuffer, 0, count)
            onBytes(count.toLong())
            remaining -= count
        }
    }
}

internal fun parseEdgeHeaders(headers: Map<String, List<String>>): LocalEdgeInfo {
    fun first(name: String) = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value?.firstOrNull().orEmpty().trim()
    val rayEdge = first("CF-Ray").substringAfterLast('-', "")
    val edgeCode = first("colo").ifBlank { first("cf-meta-colo") }.ifBlank { rayEdge }
        .uppercase(Locale.US).filter(Char::isLetterOrDigit).take(8)
    val countryCode = first("country").ifBlank { first("cf-meta-country") }
        .uppercase(Locale.US).filter(Char::isLetter).take(2)
    if (edgeCode.isBlank()) throw IOException("The nearest test server did not identify its edge location")
    return LocalEdgeInfo(edgeCode, countryCode)
}

internal fun parseServerTimeMillis(value: String?): Double {
    if (value.isNullOrBlank()) return 0.0
    val direct = Regex("(?:^|,\\s*)cfReq(?:uest)?Dur(?:ation)?;\\s*dur=([0-9.]+)", RegexOption.IGNORE_CASE)
        .find(value)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    if (direct != null && direct > 0.01) return direct
    return Regex("(?:^|,\\s*)cfSpeed[a-zA-Z]*;\\s*dur=([0-9.]+)", RegexOption.IGNORE_CASE)
        .findAll(value).sumOf { it.groupValues[1].toDoubleOrNull() ?: 0.0 }
}

private fun Response.serverTimeMillis(): Double =
    parseServerTimeMillis(headers.values("Server-Timing").joinToString(","))

internal fun percentile(values: List<Double>, fraction: Double): Double {
    if (values.isEmpty()) return 0.0
    val sorted = values.filter { it.isFinite() && it >= 0.0 }.sorted()
    if (sorted.isEmpty()) return 0.0
    val index = (sorted.lastIndex * fraction.coerceIn(0.0, 1.0))
    val lower = index.toInt()
    val upper = kotlin.math.ceil(index).toInt()
    return sorted[lower] + (sorted[upper] - sorted[lower]) * (index - lower)
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCompleted) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (continuation.isActive) continuation.resume(response) else response.close()
        }
    })
}

private const val SpeedHost = "speed.cloudflare.com"
private const val DownloadUrl = "https://speed.cloudflare.com/__down"
private const val UploadUrl = "https://speed.cloudflare.com/__up"
private const val LatencySamples = 12
private const val ReadBufferBytes = 64L * 1_024L
private const val MinimumSampleDurationMillis = 10.0
private const val FinishSetDurationMillis = 1_000.0
private const val BandwidthPercentile = 0.9
private const val MaximumMeasurementAttempts = 3
private const val RetryDelayMillis = 200L
private val UploadBuffer = ByteArray(ReadBufferBytes.toInt())
private val DownloadRounds = listOf(
    MeasurementRound(100_000, 1, warmUp = true),
    MeasurementRound(1_000_000, 3),
    MeasurementRound(10_000_000, 3),
    MeasurementRound(25_000_000, 2),
    MeasurementRound(100_000_000, 1),
)
private val UploadRounds = listOf(
    MeasurementRound(100_000, 2, warmUp = true),
    MeasurementRound(1_000_000, 3),
    MeasurementRound(10_000_000, 2),
    MeasurementRound(25_000_000, 2),
    MeasurementRound(50_000_000, 1),
)
private val EdgeCities = mapOf("DAC" to "Dhaka edge", "CGP" to "Chittagong edge")
