package com.hdvideoplay.media.ads

import android.content.Context
import android.content.Intent

/**
 * 冷启动场景：进入 App 一律按「买量」用户处理，固定使用 cold_open 场景。
 * 自然量（cold_open_a）场景已移除。
 */
object AdGate {

    fun coldScene(context: Context, intent: Intent? = null): String = AdScenes.COLD_OPEN
}
