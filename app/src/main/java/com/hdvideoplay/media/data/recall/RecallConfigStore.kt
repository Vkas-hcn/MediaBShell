package com.hdvideoplay.media.data.recall

import android.content.Context

internal interface RecallConfigStore {
    fun load(): RecallConfig?
    fun save(config: RecallConfig)
}

/**
 * Tiny persistent cache: value + last successful fetch time.
 * No value is written on network/API failure, so an old successful value is
 * never cleared by an offline launch.
 */
internal class SharedPrefsRecallConfigStore(context: Context) : RecallConfigStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun load(): RecallConfig? {
        val fetchedAt = prefs.getLong(KEY_FETCHED_AT, 0L)
        if (fetchedAt <= 0L) return null
        return RecallConfig(
            value = prefs.getString(KEY_VALUE, "").orEmpty(),
            fetchedAtMs = fetchedAt,
        )
    }

    override fun save(config: RecallConfig) {
        prefs.edit()
            .putString(KEY_VALUE, config.value)
            .putLong(KEY_FETCHED_AT, config.fetchedAtMs)
            .apply()
    }

    private companion object {
        const val PREFS = "recall_popup"
        const val KEY_VALUE = "value"
        const val KEY_FETCHED_AT = "fetched_at"
    }
}
