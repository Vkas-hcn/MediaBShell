package com.hdvideoplay.media.data.ref

import android.content.Context
import android.content.SharedPreferences
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * SharedPreferences-backed app state holder (referrer + dispatch/cloak cache).
 * Replaces the previously-missing DataTool referenced by BeForTool.
 */
object DataTool {

    lateinit var appAll: Context
        private set

    var ref_can by string("ref_can", "")
    var ref_can_ts by string("ref_can_ts", "")
    var ref_can_tss by string("ref_can_tss", "")

    var cloak_reported by boolean("cloak_reported", false)

    var dispatch_fetched_at by long("dispatch_fetched_at", 0L)
    var dispatch_payload by string("dispatch_payload", "")

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        val app = context.applicationContext
        appAll = app
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private class StringPref(
        private val key: String,
        private val default: String,
    ) : ReadWriteProperty<Any?, String> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): String =
            DataTool.prefs.getString(key, default) ?: default

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            DataTool.prefs.edit().putString(key, value).apply()
        }
    }

    private class BooleanPref(
        private val key: String,
        private val default: Boolean,
    ) : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean =
            DataTool.prefs.getBoolean(key, default)

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            DataTool.prefs.edit().putBoolean(key, value).apply()
        }
    }

    private class LongPref(
        private val key: String,
        private val default: Long,
    ) : ReadWriteProperty<Any?, Long> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Long =
            DataTool.prefs.getLong(key, default)

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) {
            DataTool.prefs.edit().putLong(key, value).apply()
        }
    }

    private fun string(key: String, default: String = "") = StringPref(key, default)
    private fun boolean(key: String, default: Boolean = false) = BooleanPref(key, default)
    private fun long(key: String, default: Long = 0L) = LongPref(key, default)

    private const val PREFS = "data_tool_prefs"
}
