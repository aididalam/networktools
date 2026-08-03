package com.nexdevlab.networktools.data

enum class WifiBand(val label: String) {
    BAND_2_4_GHZ("2.4 GHz"),
    BAND_5_GHZ("5 GHz"),
    BAND_6_GHZ("6 GHz"),
    BAND_60_GHZ("60 GHz"),
    UNKNOWN("Other"),
}

data class RawWifiObservation(
    val ssid: String?,
    val bssid: String?,
    val levelDbm: Int,
    val frequencyMhz: Int,
    val capabilities: String?,
    val channelWidthCode: Int,
    val lastSeenMillis: Long,
)

data class WifiAccessPoint(
    val ssid: String,
    val hidden: Boolean,
    val bssid: String,
    val levelDbm: Int,
    val frequencyMhz: Int,
    val channel: Int,
    val band: WifiBand,
    val security: String,
    val channelWidth: String,
    val channelWidthMhz: Int,
    val lastSeenMillis: Long,
    val connected: Boolean = false,
    val saved: Boolean = false,
) {
    val signalQuality: Int get() = ((levelDbm + 100) * 2).coerceIn(0, 100)
    val signalLabel: String
        get() = when {
            levelDbm >= -50 -> "Excellent"
            levelDbm >= -60 -> "Good"
            levelDbm >= -70 -> "Fair"
            else -> "Weak"
        }
}

data class WifiScanSnapshot(
    val accessPoints: List<WifiAccessPoint>,
    val stale: Boolean,
    val scannedAtMillis: Long,
    val notice: String? = null,
) {
    fun asPlainText(): String = buildList {
        add("Wi-Fi Analyzer result")
        add("Access points: ${accessPoints.size}")
        add("Data: ${if (stale) "Cached" else "Current scan"}")
        accessPoints.forEach { accessPoint ->
            add(accessPoint.ssid + if (accessPoint.hidden) " (Hidden network)" else "")
            add("  BSSID: ${accessPoint.bssid}")
            add("  Signal: ${accessPoint.levelDbm} dBm (${accessPoint.signalLabel})")
            add("  Channel: ${accessPoint.channel} • ${accessPoint.frequencyMhz} MHz • ${accessPoint.band.label}")
            add("  Security: ${accessPoint.security} • Width: ${accessPoint.channelWidth}")
        }
    }.joinToString("\n")
}

data class WifiChannelScore(
    val band: WifiBand,
    val channel: Int,
    val accessPointCount: Int,
    val score: Double,
)

data class SavedWifiNetwork(
    val bssid: String,
    val ssid: String,
    val hidden: Boolean,
    val security: String,
    val band: WifiBand,
    val channel: Int,
    val firstSavedMillis: Long,
    val lastSeenMillis: Long,
)

enum class WifiAnalyzerView(val label: String) {
    NETWORKS("Networks"),
    CHANNELS("Channels"),
    SAVED("Saved"),
}

data class WifiAnalyzerUiState(
    val scanning: Boolean = false,
    val snapshot: WifiScanSnapshot? = null,
    val savedNetworks: List<SavedWifiNetwork> = emptyList(),
    val comparisonBssids: Set<String> = emptySet(),
    val error: String? = null,
)
