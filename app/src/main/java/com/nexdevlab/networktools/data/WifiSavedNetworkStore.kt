package com.nexdevlab.networktools.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

class WifiSavedNetworkStore(context: Context) {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun load(): List<SavedWifiNetwork> {
        val stored = preferences.getString(SavedKey, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(stored)
            buildList {
                for (index in 0 until minOf(array.length(), MaximumEntries)) {
                    parse(array.optJSONObject(index))?.let(::add)
                }
            }.sortedByDescending(SavedWifiNetwork::lastSeenMillis)
        }.getOrDefault(emptyList())
    }

    fun toggle(accessPoint: WifiAccessPoint): List<SavedWifiNetwork> {
        val current = load()
        val existing = current.firstOrNull { it.bssid.equals(accessPoint.bssid, ignoreCase = true) }
        return if (existing != null) {
            save(current.filterNot { it.bssid.equals(accessPoint.bssid, ignoreCase = true) })
        } else {
            val now = System.currentTimeMillis()
            val entry = SavedWifiNetwork(
                bssid = accessPoint.bssid,
                ssid = accessPoint.ssid.take(MaximumSsidLength),
                hidden = accessPoint.hidden,
                security = accessPoint.security.take(MaximumSecurityLength),
                band = accessPoint.band,
                channel = accessPoint.channel,
                firstSavedMillis = now,
                lastSeenMillis = accessPoint.lastSeenMillis.coerceAtMost(now),
            )
            save((listOf(entry) + current).take(MaximumEntries))
        }
    }

    fun updateSeen(accessPoints: List<WifiAccessPoint>): List<SavedWifiNetwork> {
        val current = load()
        if (current.isEmpty()) return current
        val byBssid = accessPoints.associateBy { it.bssid.lowercase() }
        return save(current.map { saved ->
            byBssid[saved.bssid.lowercase()]?.let { accessPoint ->
                saved.copy(
                    ssid = accessPoint.ssid.take(MaximumSsidLength),
                    hidden = accessPoint.hidden,
                    security = accessPoint.security.take(MaximumSecurityLength),
                    band = accessPoint.band,
                    channel = accessPoint.channel,
                    lastSeenMillis = maxOf(saved.lastSeenMillis, accessPoint.lastSeenMillis),
                )
            } ?: saved
        }.sortedByDescending(SavedWifiNetwork::lastSeenMillis))
    }

    fun recordConnected(accessPoints: List<WifiAccessPoint>): List<SavedWifiNetwork> {
        val connected = accessPoints.firstOrNull(WifiAccessPoint::connected) ?: return load()
        val current = load()
        val now = System.currentTimeMillis()
        val existing = current.firstOrNull { it.bssid.equals(connected.bssid, ignoreCase = true) }
        val observed = SavedWifiNetwork(
            bssid = connected.bssid,
            ssid = connected.ssid.take(MaximumSsidLength),
            hidden = connected.hidden,
            security = connected.security.take(MaximumSecurityLength),
            band = connected.band,
            channel = connected.channel,
            firstSavedMillis = existing?.firstSavedMillis ?: now,
            lastSeenMillis = connected.lastSeenMillis.coerceAtMost(now),
        )
        return save(
            (listOf(observed) + current.filterNot { it.bssid.equals(connected.bssid, ignoreCase = true) })
                .take(MaximumEntries),
        )
    }

    fun remove(bssid: String): List<SavedWifiNetwork> =
        save(load().filterNot { it.bssid.equals(bssid, ignoreCase = true) })

    fun clear(): List<SavedWifiNetwork> = save(emptyList())

    private fun save(entries: List<SavedWifiNetwork>): List<SavedWifiNetwork> {
        val safeEntries = entries.take(MaximumEntries)
        val array = JSONArray()
        safeEntries.forEach { entry ->
            array.put(JSONObject().apply {
                put("bssid", entry.bssid)
                put("ssid", entry.ssid)
                put("hidden", entry.hidden)
                put("security", entry.security)
                put("band", entry.band.name)
                put("channel", entry.channel)
                put("firstSaved", entry.firstSavedMillis)
                put("lastSeen", entry.lastSeenMillis)
            })
        }
        preferences.edit { putString(SavedKey, array.toString()) }
        return safeEntries
    }

    private fun parse(value: JSONObject?): SavedWifiNetwork? {
        value ?: return null
        val bssid = value.optString("bssid").lowercase()
        if (!BssidPattern.matches(bssid)) return null
        val ssid = value.optString("ssid").take(MaximumSsidLength).takeIf(String::isNotBlank) ?: return null
        val security = value.optString("security").take(MaximumSecurityLength).takeIf(String::isNotBlank) ?: return null
        val band = WifiBand.entries.firstOrNull { it.name == value.optString("band") } ?: return null
        val channel = value.optInt("channel", -1).takeIf { it in 1..255 } ?: return null
        val firstSaved = value.optLong("firstSaved", 0L).takeIf { it > 0 } ?: return null
        val lastSeen = value.optLong("lastSeen", 0L).takeIf { it > 0 } ?: return null
        return SavedWifiNetwork(
            bssid = bssid,
            ssid = ssid,
            hidden = value.optBoolean("hidden"),
            security = security,
            band = band,
            channel = channel,
            firstSavedMillis = firstSaved,
            lastSeenMillis = lastSeen,
        )
    }

    companion object {
        const val PreferencesName = "wifi_analyzer_saved"
        private const val SavedKey = "networks"
        private const val MaximumEntries = 30
        private const val MaximumSsidLength = 64
        private const val MaximumSecurityLength = 48
        private val BssidPattern = Regex("^(?:[0-9a-f]{2}:){5}[0-9a-f]{2}$")
    }
}
