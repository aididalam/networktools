package com.nexdevlab.networktools.data

import java.util.Locale

data class TracerouteConfig(
    val target: String,
    val maxHops: Int = DefaultMaxHops,
    val attemptsPerHop: Int = DefaultAttempts,
    val timeoutSeconds: Int = DefaultTimeoutSeconds,
) {
    fun validate() {
        PingRepository.normalizeTarget(target)
        require(maxHops in MaxHopsRange) { "Maximum hops must be 1-30" }
        require(attemptsPerHop in AttemptsRange) { "Attempts per hop must be 1-3" }
        require(timeoutSeconds in TimeoutRange) { "Timeout must be 1-3 seconds" }
    }

    companion object {
        val MaxHopsRange = 1..30
        val AttemptsRange = 1..3
        val TimeoutRange = 1..3
        const val DefaultMaxHops = 20
        const val DefaultAttempts = 1
        const val DefaultTimeoutSeconds = 2
    }
}

enum class TraceAttemptStatus {
    HOP_REPLY,
    DESTINATION_REPLY,
    UNREACHABLE,
    TIMEOUT,
}

data class TraceAttempt(
    val status: TraceAttemptStatus,
    val address: String? = null,
    val responseTimeMillis: Double? = null,
) {
    val responded: Boolean get() = status != TraceAttemptStatus.TIMEOUT
}

data class HopLocation(
    val latitude: Double,
    val longitude: Double,
    val countryCode: String? = null,
    val city: String? = null,
) {
    val label: String?
        get() = listOfNotNull(city?.takeIf(String::isNotBlank), countryCode?.takeIf(String::isNotBlank))
            .joinToString(", ")
            .takeIf(String::isNotBlank)
}

data class TracerouteHop(
    val number: Int,
    val attempts: List<TraceAttempt>,
    val hostname: String? = null,
    val location: HopLocation? = null,
) {
    val addresses: List<String> get() = attempts.mapNotNull(TraceAttempt::address).distinct()
    val primaryAddress: String? get() = addresses.firstOrNull()
    val responseTimes: List<Double> get() = attempts.mapNotNull(TraceAttempt::responseTimeMillis)
    val reachedDestination: Boolean
        get() = attempts.any { it.status == TraceAttemptStatus.DESTINATION_REPLY }
    val unreachable: Boolean get() = attempts.any { it.status == TraceAttemptStatus.UNREACHABLE }
    val timedOut: Boolean get() = attempts.none(TraceAttempt::responded)
}

data class TracerouteResult(
    val target: String,
    val resolvedAddress: String,
    val hops: List<TracerouteHop>,
    val reachedDestination: Boolean,
    val stopped: Boolean,
    val completedAtMillis: Long,
) {
    fun asPlainText(): String = buildList {
        add("Traceroute result")
        add("Target: $target ($resolvedAddress)")
        add(
            "Status: " + when {
                stopped -> "Stopped"
                reachedDestination -> "Destination reached"
                else -> "Partial route"
            },
        )
        hops.forEach { hop ->
            val attempts = hop.attempts.joinToString("  ") { attempt ->
                attempt.responseTimeMillis?.let { "${formatTraceTime(it)} ms" } ?: "*"
            }
            val identity = hop.primaryAddress?.let { address ->
                hop.hostname?.let { "$it ($address)" } ?: address
            } ?: "No response"
            val location = hop.location?.label?.let { " [$it]" }.orEmpty()
            add("${hop.number}. $identity  $attempts$location")
        }
    }.joinToString("\n")
}

data class TracerouteHistoryEntry(
    val id: Long,
    val target: String,
    val resolvedAddress: String,
    val hopCount: Int,
    val reachedDestination: Boolean,
    val stopped: Boolean,
    val completedAtMillis: Long,
)

enum class TraceroutePhase(val label: String) {
    IDLE("Ready"),
    RESOLVING("Resolving target"),
    TRACING("Tracing route"),
    MAPPING("Mapping public hops"),
}

data class TracerouteUiState(
    val running: Boolean = false,
    val phase: TraceroutePhase = TraceroutePhase.IDLE,
    val currentHop: Int = 0,
    val maximumHops: Int = TracerouteConfig.DefaultMaxHops,
    val target: String? = null,
    val resolvedAddress: String? = null,
    val hops: List<TracerouteHop> = emptyList(),
    val result: TracerouteResult? = null,
    val history: List<TracerouteHistoryEntry> = emptyList(),
    val error: String? = null,
)

internal fun formatTraceTime(value: Double): String = String.format(Locale.US, "%.1f", value)
