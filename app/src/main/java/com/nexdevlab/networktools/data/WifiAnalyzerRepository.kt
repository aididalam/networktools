package com.nexdevlab.networktools.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class WifiAnalyzerRepository(private val context: Context) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun connectedLanTarget(): WifiLanTarget? = runCatching {
        LanScannerRepository(context).detectNetwork().let { network ->
            WifiLanTarget(localAddress = network.localAddress, subnet = network.subnet.display)
        }
    }.getOrNull()

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    suspend fun scan(savedBssids: Set<String>): WifiScanSnapshot {
        require(wifiManager.isWifiEnabled) { "Turn on Wi-Fi to scan nearby networks" }
        require(isLocationEnabled()) { "Turn on Location services to allow Wi-Fi scanning" }
        require(hasScanPermission(context)) { "Wi-Fi scan permission is required" }

        val outcome = withContext(Dispatchers.Main.immediate) {
            withTimeoutOrNull(ScanResultTimeoutMillis) { requestScan() }
        }
        val resultsUpdated = outcome?.first == true
        val rawResults = outcome?.second ?: runCatching { wifiManager.scanResults }.getOrDefault(emptyList())
        val connectedBssid = runCatching { wifiManager.connectionInfo?.bssid }.getOrNull()
        val accessPoints = withContext(Dispatchers.Default) {
            normalize(
                observations = rawResults.take(MaximumRawResults).map(::toObservation),
                connectedBssid = connectedBssid,
                savedBssids = savedBssids,
            )
        }
        val staleByAge = accessPoints.isNotEmpty() && accessPoints.all {
            System.currentTimeMillis() - it.lastSeenMillis > StaleResultAgeMillis
        }
        val stale = !resultsUpdated || staleByAge
        return WifiScanSnapshot(
            accessPoints = accessPoints,
            stale = stale,
            scannedAtMillis = System.currentTimeMillis(),
            notice = when {
                outcome == null -> "Scan timed out; showing available cached results"
                !resultsUpdated -> "Scan was throttled or unavailable; showing cached results"
                else -> null
            },
        )
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private suspend fun requestScan(): Pair<Boolean, List<ScanResult>> =
        suspendCancellableCoroutine { continuation ->
            var registered = false
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (!continuation.isActive) return
                    val updated = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) == true
                    val results = runCatching { wifiManager.scanResults }.getOrDefault(emptyList())
                    if (registered) runCatching { this@WifiAnalyzerRepository.context.unregisterReceiver(this) }
                    registered = false
                    continuation.resume(updated to results)
                }
            }
            continuation.invokeOnCancellation {
                if (registered) runCatching { context.unregisterReceiver(receiver) }
                registered = false
            }
            try {
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                registered = true
                if (!wifiManager.startScan() && continuation.isActive) {
                    val results = runCatching { wifiManager.scanResults }.getOrDefault(emptyList())
                    runCatching { context.unregisterReceiver(receiver) }
                    registered = false
                    continuation.resume(false to results)
                }
            } catch (failure: SecurityException) {
                if (registered) runCatching { context.unregisterReceiver(receiver) }
                registered = false
                if (continuation.isActive) continuation.cancel(failure)
            }
        }

    @Suppress("DEPRECATION")
    private fun toObservation(result: ScanResult): RawWifiObservation {
        val ageMillis = ((SystemClock.elapsedRealtime() * 1_000L - result.timestamp).coerceAtLeast(0L) / 1_000L)
        return RawWifiObservation(
            ssid = result.SSID,
            bssid = result.BSSID,
            levelDbm = result.level,
            frequencyMhz = result.frequency,
            capabilities = result.capabilities,
            channelWidthCode = result.channelWidth,
            lastSeenMillis = System.currentTimeMillis() - ageMillis,
        )
    }

    private fun isLocationEnabled(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        locationManager.isLocationEnabled
    } else {
        @Suppress("DEPRECATION")
        (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
    }

    companion object {
        private val BssidPattern = Regex("^(?:[0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}$")
        private const val MaximumRawResults = 512
        private const val MaximumAccessPoints = 256
        private const val ScanResultTimeoutMillis = 15_000L
        private const val StaleResultAgeMillis = 120_000L

        fun hasScanPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        internal fun normalize(
            observations: List<RawWifiObservation>,
            connectedBssid: String?,
            savedBssids: Set<String>,
        ): List<WifiAccessPoint> = observations.asSequence()
            .mapNotNull(::normalizeObservation)
            .groupBy(WifiAccessPoint::bssid)
            .mapNotNull { (_, duplicates) -> duplicates.maxByOrNull(WifiAccessPoint::lastSeenMillis) }
            .map { accessPoint ->
                accessPoint.copy(
                    connected = accessPoint.bssid.equals(connectedBssid, ignoreCase = true),
                    saved = savedBssids.any { it.equals(accessPoint.bssid, ignoreCase = true) },
                )
            }
            .sortedWith(
                compareByDescending<WifiAccessPoint> { it.connected }
                    .thenByDescending(WifiAccessPoint::levelDbm)
                    .thenBy(WifiAccessPoint::ssid)
                    .thenBy(WifiAccessPoint::bssid),
            )
            .take(MaximumAccessPoints)
            .toList()

        internal fun normalizeObservation(observation: RawWifiObservation): WifiAccessPoint? {
            val bssid = observation.bssid?.trim()?.lowercase(Locale.US)
                ?.takeIf(BssidPattern::matches) ?: return null
            if (observation.levelDbm !in -127..0) return null
            val channel = channelForFrequency(observation.frequencyMhz) ?: return null
            val band = bandForFrequency(observation.frequencyMhz)
            if (band == WifiBand.UNKNOWN) return null
            val cleanedSsid = observation.ssid.orEmpty()
                .filterNot(Char::isISOControl)
                .trim()
                .take(64)
            val hidden = cleanedSsid.isBlank()
            val width = channelWidth(observation.channelWidthCode)
            return WifiAccessPoint(
                ssid = if (hidden) "Hidden network" else cleanedSsid,
                hidden = hidden,
                bssid = bssid,
                levelDbm = observation.levelDbm,
                frequencyMhz = observation.frequencyMhz,
                channel = channel,
                band = band,
                security = securityType(observation.capabilities.orEmpty()),
                channelWidth = width.first,
                channelWidthMhz = width.second,
                lastSeenMillis = observation.lastSeenMillis.coerceAtLeast(0L),
            )
        }

        internal fun bandForFrequency(frequencyMhz: Int): WifiBand = when (frequencyMhz) {
            in 2_400..2_500 -> WifiBand.BAND_2_4_GHZ
            in 4_900..5_925 -> WifiBand.BAND_5_GHZ
            in 5_925..7_125 -> WifiBand.BAND_6_GHZ
            in 58_320..70_200 -> WifiBand.BAND_60_GHZ
            else -> WifiBand.UNKNOWN
        }

        internal fun channelForFrequency(frequencyMhz: Int): Int? = when {
            frequencyMhz == 2_484 -> 14
            frequencyMhz in 2_412..2_472 && (frequencyMhz - 2_407) % 5 == 0 ->
                (frequencyMhz - 2_407) / 5
            frequencyMhz in 4_910..4_980 && (frequencyMhz - 4_000) % 5 == 0 ->
                (frequencyMhz - 4_000) / 5
            frequencyMhz in 5_005..5_925 && (frequencyMhz - 5_000) % 5 == 0 ->
                (frequencyMhz - 5_000) / 5
            frequencyMhz == 5_935 -> 2
            frequencyMhz in 5_955..7_115 && (frequencyMhz - 5_950) % 5 == 0 ->
                (frequencyMhz - 5_950) / 5
            frequencyMhz in 58_320..70_200 && (frequencyMhz - 56_160) % 2_160 == 0 ->
                (frequencyMhz - 56_160) / 2_160
            else -> null
        }

        internal fun securityType(capabilities: String): String {
            val upper = capabilities.uppercase(Locale.US)
            val sae = "SAE" in upper
            val psk = "PSK" in upper
            return when {
                "OWE_TRANSITION" in upper -> "Open/Enhanced Open"
                "OWE" in upper && !upper.contains("OWE_TRANSITION") -> "Enhanced Open"
                "SUITE_B_192" in upper || ("EAP" in upper && "WPA3" in upper) -> "WPA3 Enterprise"
                sae && psk -> "WPA2/WPA3 Personal"
                sae -> "WPA3 Personal"
                "EAP" in upper && ("RSN" in upper || "WPA2" in upper) -> "WPA2 Enterprise"
                "EAP" in upper -> "WPA Enterprise"
                psk && ("RSN" in upper || "WPA2" in upper) -> "WPA2 Personal"
                psk -> "WPA Personal"
                "WEP" in upper -> "WEP"
                else -> "Open"
            }
        }

        private fun channelWidth(code: Int): Pair<String, Int> = when (code) {
            0 -> "20 MHz" to 20
            1 -> "40 MHz" to 40
            2 -> "80 MHz" to 80
            3 -> "160 MHz" to 160
            4 -> "80+80 MHz" to 160
            5 -> "320 MHz" to 320
            else -> "Unknown" to 20
        }

        internal fun channelScores(accessPoints: List<WifiAccessPoint>, band: WifiBand): List<WifiChannelScore> {
            val inBand = accessPoints.filter { it.band == band }
            if (inBand.isEmpty()) return emptyList()
            val candidates = when (band) {
                WifiBand.BAND_2_4_GHZ -> (inBand.map(WifiAccessPoint::channel) + listOf(1, 6, 11)).distinct()
                else -> inBand.map(WifiAccessPoint::channel).distinct()
            }.sorted()
            return candidates.map { candidate ->
                var count = 0
                var score = 0.0
                inBand.forEach { accessPoint ->
                    val signalWeight = ((accessPoint.levelDbm + 100).coerceIn(5, 70) / 70.0)
                    val overlap = when (band) {
                        WifiBand.BAND_2_4_GHZ -> {
                            val distance = kotlin.math.abs(candidate - accessPoint.channel)
                            ((5 - distance).coerceAtLeast(0) / 5.0)
                        }
                        else -> {
                            val halfWidthChannels = maxOf(1, accessPoint.channelWidthMhz / 10)
                            if (kotlin.math.abs(candidate - accessPoint.channel) <= halfWidthChannels) 1.0 else 0.0
                        }
                    }
                    if (overlap > 0) count++
                    score += signalWeight * overlap * maxOf(1.0, accessPoint.channelWidthMhz / 20.0)
                }
                WifiChannelScore(band, candidate, count, score)
            }
        }
    }
}
