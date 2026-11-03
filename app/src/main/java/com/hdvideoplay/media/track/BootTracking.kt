package com.hdvideoplay.media.track

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.hdvideoplay.media.MediaApp
import com.hdvideoplay.media.data.ref.DataTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 启动期埋点任务（通用规范 E01 / E23 / E24 / E25）：
 * - E01 app_first_open：BI 已由 TrackSDK 自动上报（含规范全部必带参数），这里补 adjust/firebase；
 * - E24 retention_d1/d7/d14/d30：安装后第 N 天回访各报一次（按自然日）；
 * - E23 activated / E25 biorganic：dispatch 配置买量判定（acquired_user），每设备只报一次、仅出 BI；
 *   配置缺失时不触发（不猜）。
 */
object BootTracking {
    private const val TAG = "BootTracking"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun run() {
        scope.launch {
            // E01：等 SDK 内部 install referrer 拉取（超时 1.5s）完成后再取数。
            delay(3_000)
            TrackEvents.appFirstOpenExternalsIfFirstInstall()
            // E24：留存节点判定。
            TrackEvents.retentionCheckOnce()
            // E23/E25：买量/自然量判定（dispatch 拉取成功后评估；幂等）。
            delay(5_000)
            AttributionState.evaluateOnce(MediaApp.get())
        }
    }
}

/**
 * E23/E25 买量/自然量判定：
 * - dispatch payload 的 acquired_user=true → activated（仅 bi，每设备一次）；
 * - acquired_user=false 且三方归因未归因 → biorganic（仅 bi，每设备一次）；
 * - acquired_user=false 且已归因 → 两者都不报；
 * - 配置缺失（null）→ 不触发。
 */
object AttributionState {
    private const val TAG = "AttributionState"
    private const val PREFS = "track_attribution"
    private const val KEY_ACTIVATED = "activated_reported"
    private const val KEY_BIORGANIC = "biorganic_reported"

    /** Adjust 归因回调置位（v5 无静态 getAttribution，只能由回调得知）。 */
    @Volatile
    private var attributedFlag = false

    fun markAttributed() {
        attributedFlag = true
        Log.i(TAG, "Adjust 已归因")
    }

    fun evaluateOnce(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ACTIVATED, false) || prefs.getBoolean(KEY_BIORGANIC, false)) return
        when (acquiredUserFromPayload()) {
            true -> {
                prefs.edit().putBoolean(KEY_ACTIVATED, true).apply()
                Log.i(TAG, "acquired_user=true → activated")
                TrackEvents.activated()
            }
            false -> {
                val attributed = attributedFlag
                if (!attributed) {
                    prefs.edit().putBoolean(KEY_BIORGANIC, true).apply()
                    Log.i(TAG, "acquired_user=false 且未归因 → biorganic")
                    TrackEvents.biorganic()
                } else {
                    Log.i(TAG, "acquired_user=false 但已归因 → 均不报")
                }
            }
            null -> Log.i(TAG, "acquired_user 配置缺失 → 不触发")
        }
    }

    /** 从 dispatch payload 读取买量判定；字段缺失返回 null。 */
    fun acquiredUserFromPayload(): Boolean? = try {
        val payload = DataTool.dispatch_payload
        if (payload.isBlank()) {
            null
        } else {
            Json { ignoreUnknownKeys = true }
                .parseToJsonElement(payload)
                .jsonObject["acquired_user"]
                ?.jsonPrimitive
                ?.booleanOrNull
        }
    } catch (t: Throwable) {
        Log.w(TAG, "acquired_user parse failed", t)
        null
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