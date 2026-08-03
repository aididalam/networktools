package com.nexdevlab.networktools.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

class TracerouteHistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun load(): List<TracerouteHistoryEntry> {
        val stored = preferences.getString(HistoryKey, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(stored)
            buildList {
                for (index in 0 until minOf(array.length(), MaximumEntries)) {
                    parse(array.optJSONObject(index))?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(result: TracerouteResult): List<TracerouteHistoryEntry> {
        val entry = TracerouteHistoryEntry(
            id = result.completedAtMillis,
            target = result.target.take(MaximumTargetLength),
            resolvedAddress = result.resolvedAddress.take(MaximumAddressLength),
            hopCount = result.hops.size,
            reachedDestination = result.reachedDestination,
            stopped = result.stopped,
            completedAtMillis = result.completedAtMillis,
        )
        return save((listOf(entry) + load()).distinctBy(TracerouteHistoryEntry::id).take(MaximumEntries))
    }

    fun remove(id: Long): List<TracerouteHistoryEntry> = save(load().filterNot { it.id == id })

    fun clear(): List<TracerouteHistoryEntry> = save(emptyList())

    private fun save(entries: List<TracerouteHistoryEntry>): List<TracerouteHistoryEntry> {
        val safeEntries = entries.take(MaximumEntries)
        val array = JSONArray()
        safeEntries.forEach { entry ->
            array.put(JSONObject().apply {
                put("id", entry.id)
                put("target", entry.target)
                put("address", entry.resolvedAddress)
                put("hops", entry.hopCount)
                put("reached", entry.reachedDestination)
                put("stopped", entry.stopped)
                put("completed", entry.completedAtMillis)
            })
        }
        preferences.edit { putString(HistoryKey, array.toString()) }
        return safeEntries
    }

    private fun parse(value: JSONObject?): TracerouteHistoryEntry? {
        value ?: return null
        val target = value.optString("target").takeIf(String::isNotBlank)?.take(MaximumTargetLength) ?: return null
        val address = value.optString("address").takeIf(String::isNotBlank)?.take(MaximumAddressLength) ?: return null
        val hopCount = value.optInt("hops", -1)
        if (hopCount !in 1..TracerouteConfig.MaxHopsRange.last) return null
        val completedAt = value.optLong("completed", 0L).takeIf { it > 0 } ?: return null
        return TracerouteHistoryEntry(
            id = value.optLong("id", 0L).takeIf { it > 0 } ?: return null,
            target = target,
            resolvedAddress = address,
            hopCount = hopCount,
            reachedDestination = value.optBoolean("reached"),
            stopped = value.optBoolean("stopped"),
            completedAtMillis = completedAt,
        )
    }

    companion object {
        const val PreferencesName = "traceroute_history"
        private const val HistoryKey = "entries"
        private const val MaximumEntries = 20
        private const val MaximumTargetLength = 253
        private const val MaximumAddressLength = 64
    }
}
