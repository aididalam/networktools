package com.nexdevlab.networktools.data

import java.util.Locale

enum class PingMethod(val label: String) {
    ICMP("ICMP"),
    TCP("TCP"),
}

enum class PingProbeStatus(val label: String) {
    REPLY("Reply"),
    OPEN("Open"),
    CLOSED("Closed"),
    TIMEOUT("Timed out"),
    ERROR("Error"),
}

data class PingConfig(
    val target: String,
    val method: PingMethod,
    val port: Int = DefaultPort,
    val packetCount: Int = DefaultPacketCount,
    val packetSize: Int = DefaultPacketSize,
    val timeoutMillis: Int = DefaultTimeoutMillis,
    val intervalMillis: Int = DefaultIntervalMillis,
) {
    fun validate() {
        PingRepository.normalizeTarget(target)
        require(packetCount in PacketCountRange) {
            "Packet count must be ${PacketCountRange.first}-${PacketCountRange.last}"
        }
        require(timeoutMillis in TimeoutRange) {
            "Timeout must be ${TimeoutRange.first}-${TimeoutRange.last} ms"
        }
        require(intervalMillis in IntervalRange) {
            "Interval must be ${IntervalRange.first}-${IntervalRange.last} ms"
        }
        if (method == PingMethod.ICMP) {
            require(packetSize in PacketSizeRange) {
                "Packet size must be ${PacketSizeRange.first}-${PacketSizeRange.last} bytes"
            }
        } else {
            require(port in PortRange) { "Port must be 1-65535" }
        }
    }

    companion object {
        val PacketCountRange = 1..10
        val PacketSizeRange = 16..1400
        val TimeoutRange = 500..5000
        val IntervalRange = 250..2000
        val PortRange = 1..65535
        const val DefaultPort = 443
        const val DefaultPacketCount = 4
        const val DefaultPacketSize = 56
        const val DefaultTimeoutMillis = 2000
        const val DefaultIntervalMillis = 1000
    }
}

data class PingProbe(
    val sequence: Int,
    val status: PingProbeStatus,
    val latencyMillis: Double? = null,
    val ttl: Int? = null,
    val detail: String,
) {
    val received: Boolean
        get() = status == PingProbeStatus.REPLY ||
            status == PingProbeStatus.OPEN || status == PingProbeStatus.CLOSED
}

data class PingSummary(
    val target: String,
    val resolvedAddress: String,
    val method: PingMethod,
    val port: Int?,
    val probes: List<PingProbe>,
    val stopped: Boolean,
    val completedAtMillis: Long,
) {
    val sentCount: Int get() = probes.size
    val receivedCount: Int get() = probes.count(PingProbe::received)
    val lostCount: Int get() = sentCount - receivedCount
    val lossPercentage: Double
        get() = if (sentCount == 0) 0.0 else lostCount * 100.0 / sentCount
    private val latencies: List<Double> get() = probes.mapNotNull(PingProbe::latencyMillis)
    val minimumLatency: Double? get() = latencies.minOrNull()
    val maximumLatency: Double? get() = latencies.maxOrNull()
    val averageLatency: Double? get() = latencies.takeIf { it.isNotEmpty() }?.average()

    fun asPlainText(): String = buildList {
        add("Ping result")
        add("Target: $target ($resolvedAddress)")
        add("Method: ${method.label}${port?.let { ":$it" }.orEmpty()}")
        add("Status: ${if (stopped) "Stopped" else "Completed"}")
        probes.forEach { probe ->
            add(
                "#${probe.sequence}: ${probe.status.label}" +
                    probe.latencyMillis?.let { " ${formatLatency(it)} ms" }.orEmpty() +
                    probe.ttl?.let { " TTL $it" }.orEmpty(),
            )
        }
        add("Sent: $sentCount")
        add("Received: $receivedCount")
        add("Lost: $lostCount")
        add("Loss: ${String.format(Locale.US, "%.1f", lossPercentage)}%")
        add("Minimum: ${minimumLatency?.let(::formatLatency) ?: "Unavailable"} ms")
        add("Maximum: ${maximumLatency?.let(::formatLatency) ?: "Unavailable"} ms")
        add("Average: ${averageLatency?.let(::formatLatency) ?: "Unavailable"} ms")
    }.joinToString("\n")
}

data class PingHistoryEntry(
    val id: Long,
    val target: String,
    val resolvedAddress: String,
    val method: PingMethod,
    val port: Int?,
    val sentCount: Int,
    val receivedCount: Int,
    val lossPercentage: Double,
    val averageLatency: Double?,
    val stopped: Boolean,
    val completedAtMillis: Long,
)

data class PingUiState(
    val running: Boolean = false,
    val activeTarget: String? = null,
    val probes: List<PingProbe> = emptyList(),
    val result: PingSummary? = null,
    val history: List<PingHistoryEntry> = emptyList(),
    val error: String? = null,
)

internal fun formatLatency(value: Double): String = String.format(Locale.US, "%.1f", value)
