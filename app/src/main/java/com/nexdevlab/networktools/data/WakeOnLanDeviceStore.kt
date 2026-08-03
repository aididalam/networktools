package com.nexdevlab.networktools.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

class WakeOnLanDeviceStore(context: Context) {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun load(): List<WakeOnLanDevice> {
        val stored = preferences.getString(DevicesKey, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(stored)
            buildList {
                for (index in 0 until minOf(array.length(), MaximumDevices)) {
                    parse(array.optJSONObject(index))?.let(::add)
                }
            }.distinctBy(WakeOnLanDevice::id).sortedByDescending(WakeOnLanDevice::updatedAtMillis)
        }.getOrDefault(emptyList())
    }

    fun upsert(device: WakeOnLanDevice): List<WakeOnLanDevice> {
        val safe = device.validated()
        val updated = listOf(safe) + load().filterNot { it.id == safe.id }
        return save(updated.sortedByDescending(WakeOnLanDevice::updatedAtMillis).take(MaximumDevices))
    }

    fun remove(id: String): List<WakeOnLanDevice> = save(load().filterNot { it.id == id })

    fun clear(): List<WakeOnLanDevice> = save(emptyList())

    private fun save(devices: List<WakeOnLanDevice>): List<WakeOnLanDevice> {
        val safe = devices.mapNotNull { runCatching { it.validated() }.getOrNull() }
            .distinctBy(WakeOnLanDevice::id)
            .sortedByDescending(WakeOnLanDevice::updatedAtMillis)
            .take(MaximumDevices)
        val array = JSONArray()
        safe.forEach { device ->
            array.put(JSONObject().apply {
                put("id", device.id)
                put("name", device.name)
                put("mac", device.macAddress)
                put("target", device.targetAddress)
                put("broadcast", device.broadcastAddress)
                put("port", device.udpPort)
                put("mode", device.mode.name)
                put("created", device.createdAtMillis)
                put("updated", device.updatedAtMillis)
            })
        }
        preferences.edit { putString(DevicesKey, array.toString()) }
        return safe
    }

    private fun parse(value: JSONObject?): WakeOnLanDevice? {
        value ?: return null
        val id = value.optString("id").takeIf { IdPattern.matches(it) } ?: return null
        val mode = WakeOnLanMode.entries.firstOrNull { it.name == value.optString("mode") } ?: return null
        val created = value.optLong("created", 0).takeIf { it > 0 } ?: return null
        val updated = value.optLong("updated", 0).takeIf { it >= created } ?: return null
        return runCatching {
            WakeOnLanDeviceDraft(
                name = value.optString("name").take(WakeOnLanDeviceDraft.MaximumNameLength),
                macAddress = value.optString("mac").take(17),
                targetAddress = value.optString("target").take(WakeOnLanDeviceDraft.MaximumTargetLength),
                broadcastAddress = value.optString("broadcast").take(15),
                udpPort = value.optInt("port", -1).toString(),
                mode = mode,
            ).validate(id, created, updated)
        }.getOrNull()
    }

    companion object {
        const val PreferencesName = "wake_on_lan_devices"
        const val MaximumDevices = 30
        private const val DevicesKey = "devices"
        private val IdPattern = Regex("^[0-9a-fA-F-]{36}$")
    }
}
