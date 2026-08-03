package com.nexdevlab.networktools.data

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.IDN
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

class PingRepository {
    suspend fun run(
        config: PingConfig,
        onResolved: (String) -> Unit = {},
        onProbe: (PingProbe) -> Unit,
    ): PingSummary =
        withContext(Dispatchers.IO) {
            config.validate()
            val target = normalizeTarget(config.target)
            val address = try {
                InetAddress.getAllByName(target).firstOrNull()
                    ?: throw IllegalArgumentException("No address was found for this target")
            } catch (_: UnknownHostException) {
                throw IllegalArgumentException("The host could not be resolved")
            }
            val numericAddress = address.hostAddress
                ?: throw IllegalArgumentException("The host could not be resolved")
            onResolved(numericAddress)
            val probes = when (config.method) {
                PingMethod.ICMP -> runIcmp(config, numericAddress, onProbe)
                PingMethod.TCP -> runTcp(config, address, onProbe)
            }
            PingSummary(
                target = target,
                resolvedAddress = numericAddress,
                method = config.method,
                port = config.port.takeIf { config.method == PingMethod.TCP },
                probes = probes,
                stopped = false,
                completedAtMillis = System.currentTimeMillis(),
            )
        }

    private suspend fun runIcmp(
        config: PingConfig,
        numericAddress: String,
        onProbe: (PingProbe) -> Unit,
    ): List<PingProbe> {
        val timeoutSeconds = (config.timeoutMillis + 999) / 1000
        val intervalSeconds = String.format(Locale.US, "%.3f", config.intervalMillis / 1000.0)
        val command = listOf(
            "/system/bin/ping",
            "-n",
            "-c", config.packetCount.toString(),
            "-s", config.packetSize.toString(),
            "-W", timeoutSeconds.toString(),
            "-i", intervalSeconds,
            numericAddress,
        )
        val process = try {
            ProcessBuilder(command).redirectErrorStream(true).start()
        } catch (_: Exception) {
            throw IllegalStateException("ICMP Ping is unavailable on this device")
        }
        val job = currentCoroutineContext().job
        val cancellation = job.invokeOnCompletion { process.destroy() }
        val probes = linkedMapOf<Int, PingProbe>()
        try {
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = reader.readLine() ?: break
                    parseIcmpLine(line)?.let { probe ->
                        if (probe.sequence in 1..config.packetCount && probes.putIfAbsent(probe.sequence, probe) == null) {
                            onProbe(probe)
                        }
                    }
                }
            }
            process.waitFor()
            currentCoroutineContext().ensureActive()
        } finally {
            cancellation.dispose()
            process.destroy()
        }
        for (sequence in 1..config.packetCount) {
            if (sequence !in probes) {
                val timeout = PingProbe(
                    sequence = sequence,
                    status = PingProbeStatus.TIMEOUT,
                    detail = "No ICMP response",
                )
                probes[sequence] = timeout
                onProbe(timeout)
            }
        }
        return probes.values.sortedBy(PingProbe::sequence)
    }

    private suspend fun runTcp(
        config: PingConfig,
        address: InetAddress,
        onProbe: (PingProbe) -> Unit,
    ): List<PingProbe> {
        val probes = mutableListOf<PingProbe>()
        repeat(config.packetCount) { index ->
            currentCoroutineContext().ensureActive()
            val sequence = index + 1
            val socket = Socket()
            val cancellation = currentCoroutineContext().job.invokeOnCompletion { socket.close() }
            val started = System.nanoTime()
            val probe = try {
                socket.connect(InetSocketAddress(address, config.port), config.timeoutMillis)
                PingProbe(
                    sequence,
                    PingProbeStatus.OPEN,
                    elapsedMillis(started),
                    detail = "TCP port ${config.port} is open",
                )
            } catch (_: ConnectException) {
                PingProbe(
                    sequence,
                    PingProbeStatus.CLOSED,
                    elapsedMillis(started),
                    detail = "TCP port ${config.port} is closed",
                )
            } catch (_: SocketTimeoutException) {
                PingProbe(sequence, PingProbeStatus.TIMEOUT, detail = "TCP connection timed out")
            } catch (_: NoRouteToHostException) {
                PingProbe(sequence, PingProbeStatus.ERROR, detail = "No route to target")
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                PingProbe(sequence, PingProbeStatus.ERROR, detail = failure.toUserMessage())
            } finally {
                cancellation.dispose()
                runCatching { socket.close() }
            }
            probes += probe
            onProbe(probe)
            if (sequence < config.packetCount) delay(config.intervalMillis.toLong())
        }
        return probes
    }

    companion object {
        private val ReplySequence = Regex("icmp_seq[= ](\\d+)", RegexOption.IGNORE_CASE)
        private val TtlValue = Regex("ttl[= ](\\d+)", RegexOption.IGNORE_CASE)
        private val TimeValue = Regex("time[=<]([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
        private val TimeoutSequence = Regex("no answer yet for icmp_seq[= ](\\d+)", RegexOption.IGNORE_CASE)
        private val Ipv4 = Regex("^(?:\\d{1,3}\\.){3}\\d{1,3}$")
        private val Ipv6Characters = Regex("^[0-9a-fA-F:.]+$")

        fun normalizeTarget(input: String): String {
            val trimmed = input.trim().removeSuffix(".")
            require(trimmed.isNotEmpty()) { "Enter a hostname or IP address" }
            require(trimmed.length <= 253) { "Target is too long" }
            require(trimmed.none(Char::isWhitespace) &&
                listOf("/", "\\", "://", "?", "#", "@", "%").none(trimmed::contains)
            ) { "Enter only a hostname or IP address" }
            if (Ipv4.matches(trimmed)) {
                require(trimmed.split('.').all { it.toIntOrNull() in 0..255 }) { "Enter a valid IP address" }
                return trimmed
            }
            if (':' in trimmed) {
                require(Ipv6Characters.matches(trimmed) && trimmed.count { it == ':' } >= 2) {
                    "Enter a valid IPv6 address"
                }
                return trimmed.lowercase(Locale.US)
            }
            val ascii = try {
                IDN.toASCII(trimmed, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.US)
            } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("Enter a valid hostname")
            }
            require(ascii.split('.').all { label ->
                label.isNotEmpty() && label.length <= 63 &&
                    label.first() != '-' && label.last() != '-' &&
                    label.all { it.isLetterOrDigit() || it == '-' }
            }) { "Enter a valid hostname" }
            return ascii
        }

        internal fun parseIcmpLine(line: String): PingProbe? {
            TimeoutSequence.find(line)?.groupValues?.get(1)?.toIntOrNull()?.let { sequence ->
                return PingProbe(sequence, PingProbeStatus.TIMEOUT, detail = "No ICMP response")
            }
            val sequence = ReplySequence.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: return null
            if (line.contains("unreachable", ignoreCase = true)) {
                return PingProbe(sequence, PingProbeStatus.ERROR, detail = "Destination unreachable")
            }
            val latency = TimeValue.find(line)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
            val ttl = TtlValue.find(line)?.groupValues?.get(1)?.toIntOrNull()
            return PingProbe(sequence, PingProbeStatus.REPLY, latency, ttl, "ICMP reply")
        }

        private fun elapsedMillis(startedNanos: Long): Double =
            (System.nanoTime() - startedNanos) / 1_000_000.0

        private fun Exception.toUserMessage(): String = when (this) {
            is SecurityException -> "Network access was blocked by the device"
            else -> "TCP probe failed"
        }
    }
}
