package com.nexdevlab.networktools.data

import java.util.Locale
import kotlin.math.abs

enum class SpeedServerMode { LOCAL, AUTOMATIC, COUNTRY }

data class SpeedServerChoice(
    val label: String,
    val countryCode: String?,
    val mode: SpeedServerMode,
) {
    companion object {
        val LOCAL = SpeedServerChoice("Local / nearest edge", null, SpeedServerMode.LOCAL)
        val AUTOMATIC = SpeedServerChoice("Automatic (global)", null, SpeedServerMode.AUTOMATIC)

        fun all(displayLocale: Locale = Locale.getDefault()): List<SpeedServerChoice> = listOf(LOCAL, AUTOMATIC) +
            Locale.getISOCountries()
                .asSequence()
                .map { it.uppercase(Locale.ROOT) }
                .distinct()
                .map { code ->
                    val name = Locale.Builder().setRegion(code).build().getDisplayCountry(displayLocale).ifBlank { code }
                    SpeedServerChoice(name, code, SpeedServerMode.COUNTRY)
                }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
                .toList()
    }
}

data class SpeedEndpointChoice(
    val label: String,
    val machine: String?,
    val city: String = "",
    val countryCode: String = "",
) {
    companion object {
        val AUTOMATIC = SpeedEndpointChoice("Automatic (lowest latency)", null)
    }
}

internal fun speedEndpointChoices(servers: List<SpeedTestServer>): List<SpeedEndpointChoice> =
    listOf(SpeedEndpointChoice.AUTOMATIC) + servers
        .distinctBy(SpeedTestServer::machine)
        .mapIndexed { index, server ->
            SpeedEndpointChoice(
                label = "${server.city.ifBlank { server.countryCode.ifBlank { "Available" } }} · Server ${index + 1}",
                machine = server.machine,
                city = server.city,
                countryCode = server.countryCode,
            )
        }

enum class SpeedTestPhase(val label: String) {
    IDLE("Ready"),
    DISCOVERING("Finding server"),
    DOWNLOAD("Download test"),
    UPLOAD("Upload test"),
    COMPLETE("Complete"),
    STOPPED("Stopped"),
}

data class SpeedTestServer(
    val machine: String,
    val city: String,
    val countryCode: String,
    internal val endpointHost: String,
    internal val downloadUrl: String,
    internal val uploadUrl: String,
) {
    val displayLocation: String
        get() = listOf(city, countryCode).filter(String::isNotBlank).joinToString(", ")
            .ifBlank { "Test server" }
}

data class SpeedProgress(
    val phase: SpeedTestPhase,
    val bytes: Long,
    val elapsedMicros: Long,
    val megabitsPerSecond: Double = calculateMegabitsPerSecond(bytes, elapsedMicros),
    val rttMillis: Double? = null,
)

data class SpeedTestResult(
    val downloadMbps: Double,
    val uploadMbps: Double,
    val latencyMillis: Double?,
    val jitterMillis: Double?,
    val serverCity: String,
    val serverCountryCode: String,
    val serverMachine: String,
    val networkType: String,
    val completedAtMillis: Long,
) {
    fun asPlainText(): String = buildList {
        add("Internet Speed Test")
        add("Download: ${formatSpeed(downloadMbps)} Mbps")
        add("Upload: ${formatSpeed(uploadMbps)} Mbps")
        add("Latency: ${latencyMillis?.let(::formatMilliseconds) ?: "Unavailable"}")
        add("Jitter: ${jitterMillis?.let(::formatMilliseconds) ?: "Unavailable"}")
        add("Server: ${listOf(serverCity, serverCountryCode).filter(String::isNotBlank).joinToString(", ")}")
        add("Network: $networkType")
    }.joinToString("\n")
}

data class SpeedTestUiState(
    val running: Boolean = false,
    val phase: SpeedTestPhase = SpeedTestPhase.IDLE,
    val selectedServer: SpeedServerChoice = SpeedServerChoice.LOCAL,
    val selectedEndpoint: SpeedEndpointChoice = SpeedEndpointChoice.AUTOMATIC,
    val serverOptions: List<SpeedEndpointChoice> = listOf(SpeedEndpointChoice.AUTOMATIC),
    val serversLoading: Boolean = false,
    val serverOptionsError: String? = null,
    val regionalServerFallback: Boolean = false,
    val activeServer: SpeedTestServer? = null,
    val localCountryCode: String = "",
    val localEdgeName: String = "",
    val networkType: String = "Checking network",
    val metered: Boolean = false,
    val liveMbps: Double = 0.0,
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null,
    val latencyMillis: Double? = null,
    val jitterMillis: Double? = null,
    val elapsedMillis: Long = 0,
    val result: SpeedTestResult? = null,
    val history: List<SpeedTestResult> = emptyList(),
    val error: String? = null,
    val consentRequired: Boolean = false,
    val meteredConfirmationRequired: Boolean = false,
    val releaseBlocked: Boolean = false,
    val remainingTestsToday: Int = SpeedTestPolicyStore.MaximumTestsPerDay,
)

fun calculateMegabitsPerSecond(bytes: Long, elapsedMicros: Long): Double {
    if (bytes <= 0 || elapsedMicros <= 0) return 0.0
    return (bytes.toDouble() * 8.0) / elapsedMicros.toDouble()
}

fun calculateJitter(rttSamples: List<Double>): Double? {
    val safe = rttSamples.filter { it.isFinite() && it >= 0.0 }
    if (safe.size < 2) return null
    return safe.zipWithNext { first, second -> abs(second - first) }.average()
}

fun speedMeterMaximum(value: Double): Double = when {
    value <= 10.0 -> 10.0
    value <= 25.0 -> 25.0
    value <= 50.0 -> 50.0
    value <= 100.0 -> 100.0
    value <= 250.0 -> 250.0
    value <= 500.0 -> 500.0
    value <= 1_000.0 -> 1_000.0
    else -> 2_000.0
}

fun formatSpeed(value: Double): String = when {
    !value.isFinite() || value < 0 -> "0.0"
    value < 100 -> String.format(Locale.US, "%.1f", value)
    else -> String.format(Locale.US, "%.0f", value)
}

fun formatMilliseconds(value: Double): String =
    if (value.isFinite() && value >= 0) String.format(Locale.US, "%.1f ms", value) else "Unavailable"
