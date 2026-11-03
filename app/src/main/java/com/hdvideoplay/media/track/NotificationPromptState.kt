package com.hdvideoplay.media.track

import android.content.Context
import android.content.SharedPreferences
import com.hdvideoplay.media.MediaApp

/**
 * 通知埋点本地状态（需求 6.2/6.5 的去重与首次提示判定）：
 * - first_prompt_done：是否已向用户展示过通知授权提示（落盘，进程无关）；
 * - agree_reported：notification_agree 是否已上报过（含正常同意与送达补报），保证补报不重复；
 * - 点击去重：同一通知 10s 窗口内不重复上报（覆盖 Activity 重建/热启动转场导致的重复处理）。
 */
object NotificationPromptState {

    private const val PREFS = "track_notification_state"
    private const val KEY_FIRST_PROMPT_DONE = "first_prompt_done"
    private const val KEY_AGREE_REPORTED = "agree_reported"
    private const val KEY_CLICK_LAST_MS = "click_last_ms_"

    /** 点击去重窗口：Activity 重建/热启动转场通常在秒级完成，窗口只拦截同一通知的重复处理。 */
    private const val CLICK_DEDUP_WINDOW_MS = 10_000L

    private fun prefs(): SharedPreferences =
        MediaApp.get().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * 提示真正展示（系统框确认弹出 / 自定义引导展示）时调用；
     * 返回是否为首次提示并落盘，供 notification_agree 的 is_first_prompt 使用。
     */
    @Synchronized
    fun markPromptShown(): Boolean {
        val p = prefs()
        val done = p.getBoolean(KEY_FIRST_PROMPT_DONE, false)
        if (!done) p.edit().putBoolean(KEY_FIRST_PROMPT_DONE, true).apply()
        return !done
    }

    /** 是否已提示过（落盘状态；送达补报时使用该值而非当前会话状态）。 */
    fun isFirstPromptDone(): Boolean = prefs().getBoolean(KEY_FIRST_PROMPT_DONE, false)

    @Synchronized
    fun markAgreeReported() {
        prefs().edit().putBoolean(KEY_AGREE_REPORTED, true).apply()
    }

    fun wasAgreeReported(): Boolean = prefs().getBoolean(KEY_AGREE_REPORTED, false)

    /** 通知点击跨实例去重：同一 (showType, notificationId) 在窗口内只上报一次。 */
    @Synchronized
    fun shouldReportClick(showType: Int, notificationId: Int): Boolean {
        val p = prefs()
        val key = KEY_CLICK_LAST_MS + showType + "_" + notificationId
        val now = System.currentTimeMillis()
        val last = p.getLong(key, 0L)
        if (now - last < CLICK_DEDUP_WINDOW_MS) return false
        p.edit().putLong(key, now).apply()
        return true
    }
}
