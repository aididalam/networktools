package com.nexdevlab.networktools.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

class SpeedTestHistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun load(): List<SpeedTestResult> {
        val raw = preferences.getString(HistoryKey, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until minOf(array.length(), MaximumEntries)) {
                    parse(array.optJSONObject(index))?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(result: SpeedTestResult): List<SpeedTestResult> =
        save((listOf(result.sanitized()) + load()).distinctBy(SpeedTestResult::completedAtMillis).take(MaximumEntries))

    fun remove(completedAtMillis: Long): List<SpeedTestResult> =
        save(load().filterNot { it.completedAtMillis == completedAtMillis })

    fun clear(): List<SpeedTestResult> = save(emptyList())

    private fun save(entries: List<SpeedTestResult>): List<SpeedTestResult> {
        val safe = entries.map(SpeedTestResult::sanitized).take(MaximumEntries)
        val json = JSONArray()
        safe.forEach { result ->
            json.put(JSONObject().apply {
                put("download", result.downloadMbps)
                put("upload", result.uploadMbps)
                put("latency", result.latencyMillis ?: JSONObject.NULL)
                put("jitter", result.jitterMillis ?: JSONObject.NULL)
                put("city", result.serverCity)
                put("country", result.serverCountryCode)
                put("machine", result.serverMachine)
                put("network", result.networkType)
                put("completed", result.completedAtMillis)
            })
        }
        preferences.edit { putString(HistoryKey, json.toString()) }
        return safe
    }

    private fun parse(value: JSONObject?): SpeedTestResult? {
        value ?: return null
        fun number(name: String): Double? = value.optDouble(name, Double.NaN).takeIf {
            it.isFinite() && it in 0.0..MaximumStoredValue
        }
        val download = number("download") ?: return null
        val upload = number("upload") ?: return null
        val completed = value.optLong("completed", 0).takeIf { it > 0 } ?: return null
        return SpeedTestResult(
            downloadMbps = download,
            uploadMbps = upload,
            latencyMillis = number("latency"),
            jitterMillis = number("jitter"),
            serverCity = value.optString("city").clean(MaximumLabelLength),
            serverCountryCode = value.optString("country").clean(2).uppercase(),
            serverMachine = value.optString("machine").clean(MaximumMachineLength),
            networkType = value.optString("network").clean(MaximumLabelLength).ifBlank { "Unknown" },
            completedAtMillis = completed,
        )
    }

    private fun SpeedTestResult.sanitized() = copy(
        downloadMbps = downloadMbps.safeNumber(),
        uploadMbps = uploadMbps.safeNumber(),
        latencyMillis = latencyMillis?.safeNumber(),
        jitterMillis = jitterMillis?.safeNumber(),
        serverCity = serverCity.clean(MaximumLabelLength),
        serverCountryCode = serverCountryCode.clean(2).uppercase(),
        serverMachine = serverMachine.clean(MaximumMachineLength),
        networkType = networkType.clean(MaximumLabelLength).ifBlank { "Unknown" },
    )

    private fun Double.safeNumber() = takeIf { it.isFinite() && it >= 0 }?.coerceAtMost(MaximumStoredValue) ?: 0.0
    private fun String.clean(maximum: Int) = filter { !it.isISOControl() }.trim().take(maximum)

    companion object {
        const val PreferencesName = "speed_test_history"
        private const val HistoryKey = "entries"
        private const val MaximumEntries = 20
        private const val MaximumStoredValue = 100_000.0
        private const val MaximumLabelLength = 80
        private const val MaximumMachineLength = 253
    }
}
