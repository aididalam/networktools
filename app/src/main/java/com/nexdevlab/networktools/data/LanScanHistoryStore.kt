package com.nexdevlab.networktools.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

class LanScanHistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun load(): List<LanScanHistoryEntry> {
        val stored = preferences.getString(HistoryKey, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(stored)
            buildList {
                for (index in 0 until minOf(array.length(), MaximumHistoryEntries)) {
                    parseEntry(array.optJSONObject(index))?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(result: LanScanResult): List<LanScanHistoryEntry> {
        val entry = LanScanHistoryEntry(
            id = result.completedAtMillis,
            subnet = result.subnet,
            mode = result.mode,
            devices = result.devices.take(MaximumDevicesPerEntry),
            scannedHosts = result.scannedHosts.coerceIn(0, MaximumScannedHosts),
            stopped = result.stopped,
            completedAtMillis = result.completedAtMillis,
        )
        return save((listOf(entry) + load()).distinctBy(LanScanHistoryEntry::id).take(MaximumHistoryEntries))
    }

    fun remove(id: Long): List<LanScanHistoryEntry> = save(load().filterNot { it.id == id })

    fun clear(): List<LanScanHistoryEntry> = save(emptyList())

    private fun save(entries: List<LanScanHistoryEntry>): List<LanScanHistoryEntry> {
        val safe = entries.take(MaximumHistoryEntries)
        val array = JSONArray()
        safe.forEach { entry ->
            array.put(JSONObject().apply {
                put("id", entry.id)
                put("subnet", entry.subnet)
                put("mode", entry.mode.name)
                put("scanned", entry.scannedHosts)
                put("stopped", entry.stopped)
                put("completed", entry.completedAtMillis)
                put("devices", JSONArray().apply {
                    entry.devices.take(MaximumDevicesPerEntry).forEach { put(deviceToJson(it)) }
                })
            })
        }
        preferences.edit { putString(HistoryKey, array.toString()) }
        return safe
    }

    private fun parseEntry(value: JSONObject?): LanScanHistoryEntry? {
        value ?: return null
        val subnet = runCatching { LanSubnet.parse(value.optString("subnet")) }.getOrNull() ?: return null
        val mode = LanScanMode.entries.firstOrNull { it.name == value.optString("mode") } ?: return null
        val devicesJson = value.optJSONArray("devices") ?: JSONArray()
        val devices = buildList {
            for (index in 0 until minOf(devicesJson.length(), MaximumDevicesPerEntry)) {
                parseDevice(devicesJson.optJSONObject(index), subnet)?.let(::add)
            }
        }.sortedBy(LanDevice::sortAddress)
        return LanScanHistoryEntry(
            id = value.optLong("id", 0).takeIf { it > 0 } ?: return null,
            subnet = subnet.display,
            mode = mode,
            devices = devices,
            scannedHosts = value.optInt("scanned", -1).takeIf { it in 0..MaximumScannedHosts } ?: return null,
            stopped = value.optBoolean("stopped"),
            completedAtMillis = value.optLong("completed", 0).takeIf { it > 0 } ?: return null,
        )
    }

    private fun deviceToJson(device: LanDevice) = JSONObject().apply {
        put("ip", device.ipAddress)
        put("hostname", device.hostname ?: JSONObject.NULL)
        put("mac", device.macAddress ?: JSONObject.NULL)
        put("manufacturer", device.manufacturer ?: JSONObject.NULL)
        put("type", device.deviceType.take(MaximumTextLength))
        put("protocols", JSONArray(device.protocols.map(Enum<*>::name)))
        put("services", JSONArray(device.services.take(MaximumServices).map { it.take(MaximumTextLength) }))
        put("self", device.thisDevice)
        put("gateway", device.gateway)
        put("seen", device.lastSeenMillis)
    }

    private fun parseDevice(value: JSONObject?, subnet: LanSubnet): LanDevice? {
        value ?: return null
        val ip = value.optString("ip")
        val address = LanSubnet.parseIpv4(ip)?.takeIf(subnet::contains) ?: return null
        val protocolsJson = value.optJSONArray("protocols") ?: JSONArray()
        val protocols = buildSet {
            for (index in 0 until minOf(protocolsJson.length(), LanDiscoveryProtocol.entries.size)) {
                LanDiscoveryProtocol.entries.firstOrNull { it.name == protocolsJson.optString(index) }?.let(::add)
            }
        }
        val servicesJson = value.optJSONArray("services") ?: JSONArray()
        val services = buildSet {
            for (index in 0 until minOf(servicesJson.length(), MaximumServices)) {
                servicesJson.optString(index).take(MaximumTextLength).takeIf(String::isNotBlank)?.let(::add)
            }
        }
        return LanDevice(
            ipAddress = LanSubnet.formatIpv4(address),
            hostname = value.optString("hostname").take(MaximumTextLength).takeIf(String::isNotBlank),
            macAddress = value.optString("mac").lowercase().takeIf(MacPattern::matches),
            manufacturer = value.optString("manufacturer").take(MaximumTextLength).takeIf(String::isNotBlank),
            deviceType = value.optString("type").take(MaximumTextLength).takeIf(String::isNotBlank) ?: "Unknown device",
            protocols = protocols,
            services = services,
            thisDevice = value.optBoolean("self"),
            gateway = value.optBoolean("gateway"),
            lastSeenMillis = value.optLong("seen", 0).takeIf { it > 0 } ?: return null,
        )
    }

    companion object {
        const val PreferencesName = "lan_scan_history"
        private const val HistoryKey = "entries"
        private const val MaximumHistoryEntries = 20
        private const val MaximumDevicesPerEntry = 128
        private const val MaximumScannedHosts = 254
        private const val MaximumServices = 20
        private const val MaximumTextLength = 160
        private val MacPattern = Regex("^(?:[0-9a-f]{2}:){5}[0-9a-f]{2}$")
    }
}
