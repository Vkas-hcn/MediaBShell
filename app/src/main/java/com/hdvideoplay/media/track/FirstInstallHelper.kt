package com.hdvideoplay.media.track

import android.content.Context
import android.content.SharedPreferences

/**
 * 统一的「首次安装」判断（需求 3.1）。
 *
 * 定义：首次安装 = 该安装后的第一次启动进程。判断结果在进程内记忆化，
 * 保证 loading_show / activated / home_module_request / home_module_show /
 * home_view 在同一轮启动里拿到完全一致的值，不会把每次启动误判为首次安装。
 */
object FirstInstallHelper {

    private const val PREFS_NAME = "track_first_install"
    private const val KEY_FIRST_INSTALL_DONE = "first_install_done"

    @Volatile
    private var memoized: Boolean? = null

    /**
     * 首次调用返回「是否为首次安装」并立即落盘标记；
     * 之后同进程内直接返回记忆值，跨进程（后续启动）恒为 false。
     */
    @Synchronized
    fun isFirstInstall(context: Context): Boolean {
        memoized?.let { return it }
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val done = prefs.getBoolean(KEY_FIRST_INSTALL_DONE, false)
        if (!done) {
            prefs.edit().putBoolean(KEY_FIRST_INSTALL_DONE, true).apply()
        }
        val value = !done
        memoized = value
        return value
    }

    internal fun resetForTest() {
        memoized = null
    }
}
