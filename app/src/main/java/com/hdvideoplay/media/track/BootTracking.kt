package com.hdvideoplay.media.track

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.hdvideoplay.media.MediaApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 启动期埋点任务（通用规范 E01 / E23 / E24）：
 * - E01 app_first_open：BI 已由 TrackSDK 自动上报（含规范全部必带参数），这里补 adjust/firebase；
 * - E24 retention_d1/d7/d14/d30：安装后第 N 天回访各报一次（按自然日）；
 * - E23 activated：成功进入 App 即上报（每设备一次，BI/Firebase/Adjust）。
 */
object BootTracking {
    private const val TAG = "BootTracking"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun run() {
        scope.launch {
            // E23 activated：成功进入 App 即上报（每设备一次；is_first_install 由参数携带）。
            AttributionState.evaluateOnce(MediaApp.get())
            // E01：等 SDK 内部 install referrer 拉取（超时 1.5s）完成后再取数。
            delay(3_000)
            TrackEvents.appFirstOpenExternalsIfFirstInstall()
            // E24：留存节点判定。
            TrackEvents.retentionCheckOnce()
        }
    }
}

/**
 * E23 activated：成功进入 App 上报一次（每设备一次；BI/Firebase/Adjust）。
 */
object AttributionState {
    private const val TAG = "AttributionState"
    private const val PREFS = "track_attribution"
    private const val KEY_ACTIVATED = "activated_reported"

    fun evaluateOnce(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ACTIVATED, false)) return
        prefs.edit().putBoolean(KEY_ACTIVATED, true).apply()
        Log.i(TAG, "买量用户 → activated")
        TrackEvents.activated()
    }
}

/**
 * E24 留存节点一次性上报状态（按事件名落盘，进程无关）。
 */
object RetentionState {
    private const val PREFS = "track_retention"

    fun wasReported(event: String): Boolean = prefs().getBoolean(event, false)

    fun markReported(event: String) {
        prefs().edit().putBoolean(event, true).apply()
    }

    private fun prefs(): SharedPreferences =
        MediaApp.get().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
