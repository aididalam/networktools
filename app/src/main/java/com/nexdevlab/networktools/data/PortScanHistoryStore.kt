package com.nexdevlab.networktools.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

class PortScanHistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun load(): List<PortScanHistoryEntry> {
        val raw = preferences.getString(HistoryKey, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until minOf(array.length(), MaximumHistoryEntries)) {
                    parse(array.optJSONObject(index))?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(result: PortScanResult): List<PortScanHistoryEntry> {
        val entry = PortScanHistoryEntry(
            id = result.completedAtMillis,
            target = result.target.take(MaximumTargetLength),
            resolvedAddress = result.resolvedAddress.take(MaximumAddressLength),
            scannedCount = result.entries.size.coerceAtMost(PortScanConfig.MaximumPorts),
            openPorts = result.entries.filter { it.status == PortScanStatus.OPEN }.take(PortScanConfig.MaximumPorts),
            closedCount = result.closedCount,
            timedOutCount = result.timedOutCount,
            stopped = result.stopped,
            completedAtMillis = result.completedAtMillis,
        )
        return save((listOf(entry) + load()).distinctBy(PortScanHistoryEntry::id).take(MaximumHistoryEntries))
    }

    fun remove(id: Long): List<PortScanHistoryEntry> = save(load().filterNot { it.id == id })

    fun clear(): List<PortScanHistoryEntry> = save(emptyList())

    private fun save(entries: List<PortScanHistoryEntry>): List<PortScanHistoryEntry> {
        val safe = entries.take(MaximumHistoryEntries)
        val array = JSONArray()
        safe.forEach { entry ->
            array.put(JSONObject().apply {
                put("id", entry.id)
                put("target", entry.target)
                put("address", entry.resolvedAddress)
                put("scanned", entry.scannedCount)
                put("closed", entry.closedCount)
                put("timedOut", entry.timedOutCount)
                put("stopped", entry.stopped)
                put("completed", entry.completedAtMillis)
                put("open", JSONArray().apply {
                    entry.openPorts.forEach { port ->
                        put(JSONObject().apply {
                            put("port", port.port)
                            put("service", port.service.take(MaximumServiceLength))
                            put("latency", port.latencyMillis ?: JSONObject.NULL)
                        })
                    }
                })
            })
        }
        preferences.edit { putString(HistoryKey, array.toString()) }
        return safe
    }

    private fun parse(value: JSONObject?): PortScanHistoryEntry? {
        value ?: return null
        val target = value.optString("target").take(MaximumTargetLength).takeIf(String::isNotBlank) ?: return null
        val address = value.optString("address").take(MaximumAddressLength).takeIf(String::isNotBlank) ?: return null
        val scanned = value.optInt("scanned", -1).takeIf { it in 0..PortScanConfig.MaximumPorts } ?: return null
        val closed = value.optInt("closed", -1).takeIf { it in 0..scanned } ?: return null
        val timedOut = value.optInt("timedOut", -1).takeIf { it in 0..scanned } ?: return null
        val openJson = value.optJSONArray("open") ?: JSONArray()
        val openPorts = buildList {
            for (index in 0 until minOf(openJson.length(), PortScanConfig.MaximumPorts)) {
                val item = openJson.optJSONObject(index) ?: continue
                val port = item.optInt("port", -1).takeIf { it in 1..65535 } ?: continue
                add(
                    PortScanEntry(
                        port = port,
                        status = PortScanStatus.OPEN,
                        service = item.optString("service").take(MaximumServiceLength).takeIf(String::isNotBlank)
                            ?: PortScannerRepository.serviceForPort(port),
                        latencyMillis = item.optDouble("latency").takeIf { it.isFinite() && it >= 0 },
                    ),
                )
            }
        }.distinctBy(PortScanEntry::port).sortedBy(PortScanEntry::port)
        if (openPorts.size + closed + timedOut > scanned) return null
        return PortScanHistoryEntry(
            id = value.optLong("id", 0).takeIf { it > 0 } ?: return null,
            target = target,
            resolvedAddress = address,
            scannedCount = scanned,
            openPorts = openPorts,
            closedCount = closed,
            timedOutCount = timedOut,
            stopped = value.optBoolean("stopped"),
            completedAtMillis = value.optLong("completed", 0).takeIf { it > 0 } ?: return null,
        )
    }

    companion object {
        const val PreferencesName = "port_scan_history"
        private const val HistoryKey = "entries"
        private const val MaximumHistoryEntries = 20
        private const val MaximumTargetLength = 253
        private const val MaximumAddressLength = 64
        private const val MaximumServiceLength = 80
    }
}
