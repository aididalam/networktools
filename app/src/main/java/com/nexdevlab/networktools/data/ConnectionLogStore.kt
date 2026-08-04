package com.nexdevlab.networktools.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

class ConnectionLogStore(context: Context) {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun load(): List<ConnectionLogEntry> {
        val stored = preferences.getString(EntriesKey, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(stored)
            buildList {
                for (index in 0 until minOf(array.length(), MaximumEntries)) {
                    parse(array.optJSONObject(index))?.let(::add)
                }
            }.distinctBy(ConnectionLogEntry::id).sortedByDescending(ConnectionLogEntry::timestampMillis)
        }.getOrDefault(emptyList())
    }

    fun add(entry: ConnectionLogEntry): List<ConnectionLogEntry> {
        val current = load()
        val safeId = maxOf(entry.id, (current.maxOfOrNull(ConnectionLogEntry::id) ?: 0L) + 1L)
        return save((listOf(entry.copy(id = safeId)) + current).take(MaximumEntries))
    }

    fun clear(): List<ConnectionLogEntry> = save(emptyList())

    private fun save(entries: List<ConnectionLogEntry>): List<ConnectionLogEntry> {
        val safe = entries.sortedByDescending(ConnectionLogEntry::timestampMillis)
            .distinctBy(ConnectionLogEntry::id).take(MaximumEntries)
        val array = JSONArray()
        safe.forEach { entry ->
            array.put(JSONObject().apply {
                put("id", entry.id)
                put("event", entry.event.name)
                put("previous", entry.previous?.toJson() ?: JSONObject.NULL)
                put("current", entry.current.toJson())
                put("time", entry.timestampMillis)
            })
        }
        preferences.edit { putString(EntriesKey, array.toString()) }
        return safe
    }

    private fun parse(value: JSONObject?): ConnectionLogEntry? {
        value ?: return null
        val event = ConnectionEventType.entries.firstOrNull { it.name == value.optString("event") } ?: return null
        val current = value.optJSONObject("current")?.toSnapshot() ?: return null
        val previous = value.optJSONObject("previous")?.toSnapshot()
        val time = value.optLong("time", 0).takeIf { it > 0 } ?: return null
        val id = value.optLong("id", 0).takeIf { it > 0 } ?: return null
        val expected = ConnectionLogEntry.transition(previous, current, time, id) ?: return null
        if (expected.event != event) return null
        return expected
    }

    private fun ConnectionStateSnapshot.toJson() = JSONObject().apply {
        put("kind", kind.name)
        put("metered", metered)
        put("validated", validated)
    }

    private fun JSONObject.toSnapshot(): ConnectionStateSnapshot? {
        val kind = ConnectionKind.entries.firstOrNull { it.name == optString("kind") } ?: return null
        return ConnectionStateSnapshot(
            kind = kind,
            metered = optBoolean("metered").takeIf { kind != ConnectionKind.OFFLINE } ?: false,
            validated = optBoolean("validated").takeIf { kind != ConnectionKind.OFFLINE } ?: false,
        )
    }

    companion object {
        const val PreferencesName = "connections_log"
        const val MaximumEntries = 100
        private const val EntriesKey = "entries"
    }
}
