package com.nexdevlab.networktools.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

class PingHistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun load(): List<PingHistoryEntry> {
        val stored = preferences.getString(HistoryKey, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(stored)
            buildList {
                for (index in 0 until minOf(array.length(), MaxHistoryEntries)) {
                    parseEntry(array.optJSONObject(index))?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(summary: PingSummary): List<PingHistoryEntry> {
        val entry = PingHistoryEntry(
            id = summary.completedAtMillis,
            target = summary.target.take(MaxTargetLength),
            resolvedAddress = summary.resolvedAddress.take(MaxAddressLength),
            method = summary.method,
            port = summary.port,
            sentCount = summary.sentCount,
            receivedCount = summary.receivedCount,
            lossPercentage = summary.lossPercentage,
            averageLatency = summary.averageLatency,
            stopped = summary.stopped,
            completedAtMillis = summary.completedAtMillis,
        )
        return save((listOf(entry) + load()).distinctBy(PingHistoryEntry::id).take(MaxHistoryEntries))
    }

    fun remove(id: Long): List<PingHistoryEntry> = save(load().filterNot { it.id == id })

    fun clear(): List<PingHistoryEntry> = save(emptyList())

    private fun save(entries: List<PingHistoryEntry>): List<PingHistoryEntry> {
        val safeEntries = entries.take(MaxHistoryEntries)
        val array = JSONArray()
        safeEntries.forEach { entry ->
            array.put(JSONObject().apply {
                put("id", entry.id)
                put("target", entry.target)
                put("address", entry.resolvedAddress)
                put("method", entry.method.name)
                put("port", entry.port ?: JSONObject.NULL)
                put("sent", entry.sentCount)
                put("received", entry.receivedCount)
                put("loss", entry.lossPercentage)
                put("average", entry.averageLatency ?: JSONObject.NULL)
                put("stopped", entry.stopped)
                put("completed", entry.completedAtMillis)
            })
        }
        preferences.edit { putString(HistoryKey, array.toString()) }
        return safeEntries
    }

    private fun parseEntry(value: JSONObject?): PingHistoryEntry? {
        value ?: return null
        val target = value.optString("target").takeIf { it.isNotBlank() }?.take(MaxTargetLength) ?: return null
        val method = PingMethod.entries.firstOrNull { it.name == value.optString("method") } ?: return null
        val sent = value.optInt("sent", -1)
        val received = value.optInt("received", -1)
        if (sent !in 0..PingConfig.PacketCountRange.last || received !in 0..sent) return null
        return PingHistoryEntry(
            id = value.optLong("id", 0L).takeIf { it > 0 } ?: return null,
            target = target,
            resolvedAddress = value.optString("address").take(MaxAddressLength),
            method = method,
            port = value.optInt("port").takeIf { it in PingConfig.PortRange },
            sentCount = sent,
            receivedCount = received,
            lossPercentage = value.optDouble("loss", 0.0).coerceIn(0.0, 100.0),
            averageLatency = value.optDouble("average").takeIf { it.isFinite() && it >= 0.0 },
            stopped = value.optBoolean("stopped"),
            completedAtMillis = value.optLong("completed", 0L).takeIf { it > 0 } ?: return null,
        )
    }

    companion object {
        const val PreferencesName = "ping_history"
        private const val HistoryKey = "entries"
        private const val MaxHistoryEntries = 20
        private const val MaxTargetLength = 253
        private const val MaxAddressLength = 64
    }
}
