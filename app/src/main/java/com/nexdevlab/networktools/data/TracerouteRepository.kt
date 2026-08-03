package com.nexdevlab.networktools.data

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

class TracerouteRepository {
    suspend fun trace(
        config: TracerouteConfig,
        onPhase: (TraceroutePhase) -> Unit,
        onResolved: (String) -> Unit,
        onHop: (TracerouteHop) -> Unit,
    ): TracerouteResult = withContext(Dispatchers.IO) {
        config.validate()
        val target = PingRepository.normalizeTarget(config.target)
        onPhase(TraceroutePhase.RESOLVING)
        val destination = try {
            InetAddress.getAllByName(target).firstOrNull()
                ?: throw IllegalArgumentException("No address was found for this target")
        } catch (_: UnknownHostException) {
            throw IllegalArgumentException("The host could not be resolved")
        }
        val destinationAddress = destination.hostAddress
            ?: throw IllegalArgumentException("The host could not be resolved")
        onResolved(destinationAddress)
        onPhase(TraceroutePhase.TRACING)

        val hops = mutableListOf<TracerouteHop>()
        for (hopNumber in 1..config.maxHops) {
            currentCoroutineContext().ensureActive()
            val attempts = buildList {
                repeat(config.attemptsPerHop) {
                    add(probe(destinationAddress, hopNumber, config.timeoutSeconds))
                }
            }
            val primaryAddress = attempts.firstNotNullOfOrNull(TraceAttempt::address)
            val hostname = primaryAddress?.let { reverseHostname(it) }
            val hop = TracerouteHop(hopNumber, attempts, hostname)
            hops += hop
            onHop(hop)
            if (hop.reachedDestination || hop.unreachable) break
        }

        onPhase(TraceroutePhase.MAPPING)
        val locations = mutableMapOf<String, HopLocation?>()
        var geolocationRequests = 0
        for (index in hops.indices) {
            currentCoroutineContext().ensureActive()
            val address = hops[index].primaryAddress ?: continue
            if (!isPublicAddress(address)) continue
            val location = if (address in locations) {
                locations[address]
            } else if (geolocationRequests < MaximumGeolocationRequests) {
                geolocationRequests++
                fetchLocation(address)
            } else {
                null
            }
            locations[address] = location
            if (location != null) {
                hops[index] = hops[index].copy(location = location)
                onHop(hops[index])
            }
        }
        TracerouteResult(
            target = target,
            resolvedAddress = destinationAddress,
            hops = hops.toList(),
            reachedDestination = hops.any(TracerouteHop::reachedDestination),
            stopped = false,
            completedAtMillis = System.currentTimeMillis(),
        )
    }

    private suspend fun probe(destination: String, hop: Int, timeoutSeconds: Int): TraceAttempt {
        val command = listOf(
            "/system/bin/ping",
            "-n",
            "-c", "1",
            "-W", timeoutSeconds.toString(),
            "-t", hop.toString(),
            destination,
        )
        val started = System.nanoTime()
        val process = try {
            ProcessBuilder(command).redirectErrorStream(true).start()
        } catch (_: Exception) {
            throw IllegalStateException("Traceroute probing is unavailable on this device")
        }
        val cancellation = currentCoroutineContext().job.invokeOnCompletion { process.destroy() }
        val output = StringBuilder()
        try {
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = reader.readLine() ?: break
                    if (output.length < MaximumProbeOutputCharacters) output.appendLine(line)
                }
            }
            process.waitFor()
            currentCoroutineContext().ensureActive()
        } finally {
            cancellation.dispose()
            process.destroy()
        }
        return parseProbeOutput(output.toString(), elapsedMillis(started))
    }

    private suspend fun reverseHostname(address: String): String? = withTimeoutOrNull(ReverseLookupTimeoutMillis) {
        withContext(Dispatchers.IO) {
            runCatching {
                InetAddress.getByName(address).canonicalHostName
                    .takeIf { it.isNotBlank() && !it.equals(address, ignoreCase = true) }
                    ?.take(MaxHostnameLength)
            }.getOrNull()
        }
    }

    private suspend fun fetchLocation(address: String): HopLocation? = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(address, StandardCharsets.UTF_8.name())
        val url = "https://stat.ripe.net/data/geoloc/data.json?resource=$encoded&sourceapp=networktools"
        val connection = java.net.URL(url).openConnection() as HttpsURLConnection
        connection.connectTimeout = GeolocationTimeoutMillis
        connection.readTimeout = GeolocationTimeoutMillis
        connection.instanceFollowRedirects = false
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "NetworkTools/1.0")
        val cancellation = currentCoroutineContext().job.invokeOnCompletion { connection.disconnect() }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val length = connection.contentLengthLong
            if (length > MaximumGeolocationResponseBytes) return@withContext null
            val body = connection.inputStream.bufferedReader().use { reader ->
                val output = StringBuilder()
                val buffer = CharArray(8_192)
                while (output.length <= MaximumGeolocationResponseBytes) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    output.append(buffer, 0, count)
                }
                output.toString()
            }
            if (body.length > MaximumGeolocationResponseBytes) null else parseGeolocation(body)
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            null
        } finally {
            cancellation.dispose()
            connection.disconnect()
        }
    }

    companion object {
        private val Ipv4Pattern = Regex("(?<![0-9.])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?![0-9.])")
        private val Ipv6Pattern = Regex("(?<![0-9a-fA-F:])(?:[0-9a-fA-F]{0,4}:){2,}[0-9a-fA-F:.]*(?![0-9a-fA-F:])")
        private val TimePattern = Regex("time[=<]([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
        private const val MaximumProbeOutputCharacters = 16_384
        private const val ReverseLookupTimeoutMillis = 1_200L
        private const val GeolocationTimeoutMillis = 2_500
        private const val MaximumGeolocationResponseBytes = 256 * 1024
        private const val MaximumGeolocationRequests = 12
        private const val MaxHostnameLength = 253

        internal fun parseProbeOutput(output: String, elapsedMillis: Double): TraceAttempt {
            val lower = output.lowercase()
            val address = extractAddress(output)
            val reportedTime = TimePattern.find(output)?.groupValues?.get(1)?.toDoubleOrNull()
            return when {
                "time to live exceeded" in lower || "hop limit exceeded" in lower -> TraceAttempt(
                    TraceAttemptStatus.HOP_REPLY,
                    address,
                    reportedTime ?: elapsedMillis,
                )
                "destination" in lower && "unreachable" in lower -> TraceAttempt(
                    TraceAttemptStatus.UNREACHABLE,
                    address,
                    reportedTime ?: elapsedMillis,
                )
                "bytes from" in lower && address != null -> TraceAttempt(
                    TraceAttemptStatus.DESTINATION_REPLY,
                    address,
                    reportedTime ?: elapsedMillis,
                )
                else -> TraceAttempt(TraceAttemptStatus.TIMEOUT)
            }
        }

        private fun extractAddress(output: String): String? {
            for (line in output.lineSequence()) {
                if (!line.contains("from", ignoreCase = true)) continue
                val candidate = Ipv4Pattern.find(line)?.value ?: Ipv6Pattern.find(line)?.value ?: continue
                val normalized = runCatching { InetAddress.getByName(candidate).hostAddress }.getOrNull()
                if (!normalized.isNullOrBlank()) return normalized
            }
            return null
        }

        internal fun parseGeolocation(body: String): HopLocation? {
            val response = runCatching { JSONObject(body) }.getOrNull() ?: return null
            if (response.optString("status") != "ok" || response.optString("data_call_status") != "supported") {
                return null
            }
            val resources = response.optJSONObject("data")?.optJSONArray("located_resources") ?: return null
            for (resourceIndex in 0 until resources.length()) {
                val locations = resources.optJSONObject(resourceIndex)?.optJSONArray("locations") ?: continue
                for (locationIndex in 0 until locations.length()) {
                    val location = locations.optJSONObject(locationIndex) ?: continue
                    val latitude = location.optDouble("latitude", Double.NaN)
                    val longitude = location.optDouble("longitude", Double.NaN)
                    if (!latitude.isFinite() || latitude !in -90.0..90.0 ||
                        !longitude.isFinite() || longitude !in -180.0..180.0
                    ) continue
                    return HopLocation(
                        latitude = latitude,
                        longitude = longitude,
                        countryCode = location.optString("country").take(2).takeIf(String::isNotBlank),
                        city = location.optString("city").take(120).takeIf(String::isNotBlank),
                    )
                }
            }
            return null
        }

        internal fun isPublicAddress(value: String): Boolean {
            val address = runCatching { InetAddress.getByName(value) }.getOrNull() ?: return false
            if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
                address.isSiteLocalAddress || address.isMulticastAddress
            ) return false
            return when (address) {
                is Inet4Address -> {
                    val bytes = address.address.map { it.toInt() and 0xff }
                    when {
                        bytes[0] == 0 || bytes[0] >= 224 -> false
                        bytes[0] == 100 && bytes[1] in 64..127 -> false
                        bytes[0] == 192 && bytes[1] == 0 && bytes[2] in 0..2 -> false
                        bytes[0] == 198 && bytes[1] in 18..19 -> false
                        bytes[0] == 198 && bytes[1] == 51 && bytes[2] == 100 -> false
                        bytes[0] == 203 && bytes[1] == 0 && bytes[2] == 113 -> false
                        else -> true
                    }
                }
                is Inet6Address -> {
                    val bytes = address.address
                    val documentation = (bytes[0].toInt() and 0xff) == 0x20 &&
                        (bytes[1].toInt() and 0xff) == 0x01 &&
                        (bytes[2].toInt() and 0xff) == 0x0d &&
                        (bytes[3].toInt() and 0xff) == 0xb8
                    val uniqueLocal = (bytes[0].toInt() and 0xfe) == 0xfc
                    !documentation && !uniqueLocal
                }
                else -> false
            }
        }

        private fun elapsedMillis(startedNanos: Long): Double =
            (System.nanoTime() - startedNanos) / 1_000_000.0
    }
}
