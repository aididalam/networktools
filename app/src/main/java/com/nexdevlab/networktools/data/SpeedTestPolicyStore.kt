package com.nexdevlab.networktools.data

import android.content.Context
import androidx.core.content.edit

class SpeedTestPolicyStore(context: Context) {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun hasCurrentConsent(): Boolean = preferences.getInt(ConsentKey, 0) == ConsentVersion

    fun acceptConsent() = preferences.edit { putInt(ConsentKey, ConsentVersion) }

    fun remainingTests(nowMillis: Long = System.currentTimeMillis()): Int {
        val starts = recentStarts(nowMillis)
        saveStarts(starts)
        return (MaximumTestsPerDay - starts.size).coerceAtLeast(0)
    }

    fun recordStart(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val starts = recentStarts(nowMillis)
        if (starts.size >= MaximumTestsPerDay) return false
        saveStarts(starts + nowMillis)
        return true
    }

    private fun recentStarts(nowMillis: Long): List<Long> = preferences.getString(StartsKey, "")
        .orEmpty()
        .split(',')
        .mapNotNull(String::toLongOrNull)
        .filter { it in (nowMillis - OneDayMillis)..nowMillis }
        .distinct()
        .sorted()

    private fun saveStarts(starts: List<Long>) = preferences.edit {
        putString(StartsKey, starts.takeLast(MaximumTestsPerDay).joinToString(","))
    }

    companion object {
        const val PreferencesName = "speed_test_policy"
        const val MaximumTestsPerDay = 20
        private const val ConsentKey = "consent_version"
        private const val StartsKey = "starts"
        private const val ConsentVersion = 2
        private const val OneDayMillis = 24L * 60L * 60L * 1_000L
    }
}
