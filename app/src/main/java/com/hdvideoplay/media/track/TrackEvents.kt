package com.hdvideoplay.media.track

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustEvent
import com.flux.tracksdk.core.TrackSDK
import com.flux.tracksdk.model.TrackPolicy
import com.flux.tracksdk.utils.DeviceUtils
import com.flux.tracksdk.utils.ReferrerHelper
import androidx.core.content.ContextCompat
import com.google.firebase.analytics.FirebaseAnalytics
import com.hdvideoplay.media.MediaApp

/**
 * 埋点统一封装：事件名 / 参数名常量 + 各平台上报入口。
 *
 * 平台原则：
 * - BI：TrackSDK（MediaApp 已初始化），业务需求事件（loading_show 等 17 个）+ 通用规范事件；
 * - Firebase Analytics：loading_show / home_view / 通知链路 / 广告链路 / retention 等指定事件；
 * - Adjust：notification_agree（ryg9dc）与规范事件的占位 token（【待填】）。
 *
 * 每个平台调用独立 runCatching：任一平台失败不影响其他平台，也不影响业务流程。
 */
object TrackEvents {

    private const val TAG = "TrackEvents"

    // ---- 事件名 ----
    const val LOADING_SHOW = "loading_show"
    const val ACTIVATED = "activated"
    const val HOME_MODULE_REQUEST = "home_module_request"
    const val HOME_MODULE_SHOW = "home_module_show"
    const val HOME_VIEW = "home_view"
    const val DETAIL_REQUEST = "detail_request"
    const val DETAIL_VIEW = "detail_view"
    const val DETAIL_ERROR = "detail_error"
    const val PLAY_START = "play_start"
    const val PLAY_PROGRESS = "play_progress"
    const val PLAY_END = "play_end"
    const val PLAY_FAIL = "play_fail"
    const val IMAGE_LOAD_FAIL = "image_load_fail"
    const val API_ERROR = "api_error"
    const val RECALL_SHOW = "recall_show"
    const val RECALL_CLICK = "recall_click"
    const val RECALL_DISMISS = "recall_dismiss"

    // ---- 通知权限及通知链路（需求 6.x） ----
    const val NOTIFICATION_PERMISSION_REQUEST = "notification_permission_request"
    const val NOTIFICATION_AGREE = "notification_agree"
    const val NOTIFICATION_TRIGGER = "notification_trigger"
    const val NOTIFICATION_SEND = "notification_send"
    const val NOTIFICATION_CLICK = "notification_click"

    // ---- 通用埋点规范（E01-E25）在工程内的补充事件 ----
    const val APP_FIRST_OPEN = "app_first_open"
    const val NOTIFICATION_RECEIVED = "notification_received"
    const val NOTIFICATION_SHOW = "notification_show"
    const val NOTIFICATION_FREQUENCY_LIMITED = "notification_frequency_limited"
    const val REACTIVATION_LANDING = "reactivation_landing"
    const val KEEPALIVE_TRIGGER = "keepalive_trigger"
    const val AD_BLOCKED = "ad_blocked"
    const val AD_SHOW_TIMEOUT = "ad_show_timeout"
    const val BIORGANIC = "biorganic"
    const val RETENTION_D1 = "retention_d1"
    const val RETENTION_D7 = "retention_d7"
    const val RETENTION_D14 = "retention_d14"
    const val RETENTION_D30 = "retention_d30"

    // ---- 参数名 ----
    const val P_IS_FIRST_INSTALL = "is_first_install"
    const val P_MEDIA_ID = "media_id"
    const val P_AUTO_PLAY = "auto_play"
    const val P_FROM_POSITION = "from_position"
    const val P_MODULE_ID = "module_id"
    const val P_MODULE_NAME = "module_name"
    const val P_MODULE_INDEX = "module_index"
    const val P_EPISODE = "episode"
    const val P_LINE_NAME = "line_name"
    const val P_STARTUP_MS = "startup_ms"
    const val P_PCT = "pct"
    const val P_WATCHED_MS = "watched_ms"
    const val P_END_REASON = "end_reason"
    const val P_MAX_PCT = "max_pct"
    const val P_ERROR_CODE = "error_code"
    const val P_ERROR_MSG = "error_msg"
    const val P_URL_HOST = "url_host"
    const val P_API = "api"
    const val P_HTTP_CODE = "http_code"
    const val P_COST_MS = "cost_ms"
    const val P_IS_FIRST_PROMPT = "is_first_prompt"

    // ---- 通用规范参数 ----
    const val P_POS_ID = "pos_id"
    const val P_TRIGGER_SOURCE = "trigger_source"
    const val P_TRIGGER_TYPE = "trigger_type"
    const val P_AD_TYPE = "ad_type"
    const val P_SDK_NAME = "sdk_name"
    const val P_AD_ID = "ad_id"
    const val P_AD_SOURCE = "ad_source"
    const val P_BLOCK_REASON = "block_reason"
    const val P_WAIT_MS = "wait_ms"
    const val P_LIMIT_REASON = "limit_reason"
    const val P_MSG_ID = "msg_id"
    const val P_SOURCE = "source"
    const val P_LANDING_PAGE = "landing_page"

    // ---- from_position 取值（需求 2.2 + 本应用既有入口的扩展值） ----
    const val FROM_HOME_MODULE = "home_module"
    const val FROM_SEARCH = "search"
    const val FROM_HISTORY = "history"
    const val FROM_NOTIFY = "notify"
    const val FROM_CHANNEL = "channel"
    const val FROM_MY = "my"
    const val FROM_RESUME = "resume"
    const val FROM_RECOMMEND = "recommend"

    // ---- play_end.end_reason 取值（需求 3.3） ----
    const val END_COMPLETE = "complete"
    const val END_BACK = "back"
    const val END_ERROR = "error"

    /** Adjust 事件识别码：文档未提供的一律占位（规范 1.2 硬规则 4），交付前由运营替换。 */
    const val ADJUST_TOKEN_PLACEHOLDER = "【待填】"

    /** Adjust notification_agree 事件识别码（需求 6.2）。 */
    private const val ADJUST_AGREE_TOKEN = "ryg9dc"

    // ---- 各平台底层上报（相互独立，互不影响） ----

    /** BI 上报；policy 传 null 时使用 TrackConfig 默认（BATCH）。 */
    internal fun bi(eventName: String, props: Map<String, Any?>?, policy: TrackPolicy? = null) {
        Log.i(TAG, "埋点 event=$eventName platform=BI params=${props ?: "{}"}")
        runCatching { TrackSDK.track(eventName, props, policy) }
            .onFailure { Log.w(TAG, "BI track failed: $eventName", it) }
    }

    private fun firebase(eventName: String, params: Map<String, Any>) {
        Log.i(TAG, "埋点 event=$eventName platform=Firebase params=$params")
        runCatching {
            val context = MediaApp.get()
            val bundle = Bundle()
            params.forEach { (k, v) ->
                when (v) {
                    is Int -> bundle.putInt(k, v)
                    is Long -> bundle.putLong(k, v)
                    is Double -> bundle.putDouble(k, v)
                    is Boolean -> bundle.putInt(k, if (v) 1 else 0)
                    else -> bundle.putString(k, v.toString())
                }
            }
            FirebaseAnalytics.getInstance(context).logEvent(eventName, bundle)
        }.onFailure { Log.w(TAG, "Firebase track failed: $eventName", it) }
    }

    private fun adjust(eventName: String, eventToken: String) {
        Log.i(TAG, "埋点 event=$eventName platform=Adjust token=$eventToken")
        runCatching { Adjust.trackEvent(AdjustEvent(eventToken)) }
            .onFailure { Log.w(TAG, "Adjust track failed: $eventToken", it) }
    }

    // ---- 公共事件入口 ----

    /** 统一首次安装判断（需求 3.1）。 */
    fun isFirstInstall(): Boolean = FirstInstallHelper.isFirstInstall(MediaApp.get())

    /** 1.1 Loading 页面成功展示：BI + Firebase。 */
    fun loadingShow() {
        bi(LOADING_SHOW, mapOf(P_IS_FIRST_INSTALL to isFirstInstall()))
        firebase(LOADING_SHOW, mapOf(P_IS_FIRST_INSTALL to (if (isFirstInstall()) 1 else 0)))
    }

    /**
     * E23 activated（买量用户标记）：仅出 BI，每设备只报一次。
     * 判定与一次性去重由 [AttributionState.evaluateOnce] 负责，本方法只做 BI 上报。
     */
    fun activated() {
        bi(ACTIVATED, null)
    }

    /** E25 biorganic（自然量用户标记）：仅出 BI，每设备只报一次。 */
    fun biorganic() {
        bi(BIORGANIC, null)
    }

    /** E19 reactivation_landing：拉活打开 App 后落到目标页（source + landing_page + pos_id 可空）。 */
    fun reactivationLanding(source: String, landingPage: String, posId: String? = null) {
        val props = linkedMapOf<String, Any?>(
            P_SOURCE to source,
            P_LANDING_PAGE to landingPage,
        )
        if (!posId.isNullOrBlank()) props[P_POS_ID] = posId
        bi(REACTIVATION_LANDING, props)
    }

    /** 1.3 首页资源请求：BI。 */
    fun homeModuleRequest() {
        bi(HOME_MODULE_REQUEST, mapOf(P_IS_FIRST_INSTALL to isFirstInstall()))
    }

    /** 1.4 首页模块展示成功：BI。 */
    fun homeModuleShow(moduleId: Int, moduleName: String, moduleIndex: Int) {
        bi(
            HOME_MODULE_SHOW,
            mapOf(
                P_IS_FIRST_INSTALL to isFirstInstall(),
                P_MODULE_ID to moduleId,
                P_MODULE_NAME to moduleName,
                P_MODULE_INDEX to moduleIndex,
            ),
        )
    }

    /** 1.5 首页展示成功：BI + Firebase。 */
    fun homeView() {
        bi(HOME_VIEW, mapOf(P_IS_FIRST_INSTALL to isFirstInstall()))
        firebase(HOME_VIEW, mapOf(P_IS_FIRST_INSTALL to (if (isFirstInstall()) 1 else 0)))
    }

    /** 2.1 详情页资源请求：BI。 */
    fun detailRequest() {
        bi(DETAIL_REQUEST, null)
    }

    /** 2.2 详情页资源成功展示：BI。 */
    fun detailView(mediaId: Int, autoPlay: Boolean, fromPosition: String, moduleId: Int?) {
        val props = linkedMapOf<String, Any?>(
            P_MEDIA_ID to mediaId,
            P_AUTO_PLAY to autoPlay,
            P_FROM_POSITION to fromPosition,
        )
        if (moduleId != null && moduleId > 0) props[P_MODULE_ID] = moduleId
        bi(DETAIL_VIEW, props)
    }

    /** 2.3 详情页资源请求/加载失败：BI。 */
    fun detailError(mediaId: Int, errorCode: String, errorMsg: String) {
        bi(
            DETAIL_ERROR,
            mapOf(
                P_MEDIA_ID to mediaId,
                P_ERROR_CODE to errorCode,
                P_ERROR_MSG to errorMsg,
            ),
        )
    }

    /** 3.1 真正开始播放并成功出画：BI。 */
    fun playStart(mediaId: Int, episode: Int, lineName: String, startupMs: Long) {
        bi(
            PLAY_START,
            mapOf(
                P_MEDIA_ID to mediaId,
                P_EPISODE to episode,
                P_LINE_NAME to lineName,
                P_STARTUP_MS to startupMs,
            ),
        )
    }

    /** 3.2 播放进度档位（25/50/75/100）：BI。 */
    fun playProgress(mediaId: Int, episode: Int, pct: Int, watchedMs: Long) {
        bi(
            PLAY_PROGRESS,
            mapOf(
                P_MEDIA_ID to mediaId,
                P_EPISODE to episode,
                P_PCT to pct,
                P_WATCHED_MS to watchedMs,
            ),
        )
    }

    /** 3.3 播放结束：BI。 */
    fun playEnd(mediaId: Int, episode: Int, endReason: String, watchedMs: Long, maxPct: Int) {
        bi(
            PLAY_END,
            mapOf(
                P_MEDIA_ID to mediaId,
                P_EPISODE to episode,
                P_END_REASON to endReason,
                P_WATCHED_MS to watchedMs,
                P_MAX_PCT to maxPct,
            ),
        )
    }

    /** 3.4 线路播放失败：BI。 */
    fun playFail(mediaId: Int, episode: Int, lineName: String, errorMsg: String) {
        bi(
            PLAY_FAIL,
            mapOf(
                P_MEDIA_ID to mediaId,
                P_EPISODE to episode,
                P_LINE_NAME to lineName,
                P_ERROR_MSG to errorMsg,
            ),
        )
    }

    /** 4.1 海报加载失败：BI（只传域名，不传完整 URL）。 */
    fun imageLoadFail(urlHost: String, mediaId: Int) {
        bi(
            IMAGE_LOAD_FAIL,
            mapOf(
                P_URL_HOST to urlHost,
                P_MEDIA_ID to mediaId,
            ),
        )
    }

    /** 4.2 MediaApi 接口失败：BI。 */
    fun apiError(api: String, httpCode: Int, costMs: Long) {
        bi(
            API_ERROR,
            mapOf(
                P_API to api,
                P_HTTP_CODE to httpCode,
                P_COST_MS to costMs,
            ),
        )
    }

    // ---- 通知权限及通知链路（需求 6.x） ----

    /** 6.1 应用主动发起通知权限申请（系统权限框 launch / 自定义引导展示）：BI + Firebase。 */
    fun notificationPermissionRequest() {
        bi(NOTIFICATION_PERMISSION_REQUEST, mapOf(P_IS_FIRST_INSTALL to isFirstInstall()))
        firebase(
            NOTIFICATION_PERMISSION_REQUEST,
            mapOf(P_IS_FIRST_INSTALL to (if (isFirstInstall()) 1 else 0)),
        )
    }

    /**
     * 6.2 用户同意通知授权（系统权限请求同意 / 自定义引导同意 / 送达补报）：Adjust + BI + Firebase。
     * 标记 agree 已上报，保证后续送达补报不重复统计。
     */
    fun notificationAgree(isFirstPrompt: Boolean) {
        NotificationPromptState.markAgreeReported()
        bi(NOTIFICATION_AGREE, mapOf(P_IS_FIRST_PROMPT to isFirstPrompt))
        firebase(NOTIFICATION_AGREE, mapOf(P_IS_FIRST_PROMPT to (if (isFirstPrompt) 1 else 0)))
        adjust(NOTIFICATION_AGREE, ADJUST_AGREE_TOKEN)
    }

    /**
     * E14/6.3 通知生成进入发送流程：NOTIFY-BASE（pos_id、trigger_source），
     * 出口 bi、adjust、firebase（adjust token 占位）。
     */
    fun notificationTrigger(posId: String, triggerSource: String) {
        val params = mapOf(P_POS_ID to posId, P_TRIGGER_SOURCE to triggerSource)
        bi(NOTIFICATION_TRIGGER, params)
        firebase(NOTIFICATION_TRIGGER, params)
        adjust(NOTIFICATION_TRIGGER, ADJUST_TOKEN_PLACEHOLDER)
    }

    /** E15 notification_received：下发消息到达客户端（FCM/data 消息），仅 bi。 */
    fun notificationReceived(posId: String?, msgId: String?) {
        val props = linkedMapOf<String, Any?>()
        if (!posId.isNullOrBlank()) props[P_POS_ID] = posId
        if (!msgId.isNullOrBlank()) props[P_MSG_ID] = msgId
        bi(NOTIFICATION_RECEIVED, props.ifEmpty { null })
    }

    /**
     * E16 notification_show：通知实际展示（含常驻），NOTIFY-BASE，出口 bi、adjust、firebase。
     * 刷新同 ID 更新不重复上报（由调用侧 trackSent 门控）。
     */
    fun notificationShow(posId: String, triggerSource: String) {
        val params = mapOf(P_POS_ID to posId, P_TRIGGER_SOURCE to triggerSource)
        bi(NOTIFICATION_SHOW, params)
        firebase(NOTIFICATION_SHOW, params)
        adjust(NOTIFICATION_SHOW, ADJUST_TOKEN_PLACEHOLDER)
    }

    /** E18 notification_frequency_limited：通知被频控拦截未展示，仅 bi。 */
    fun notificationFrequencyLimited(posId: String, limitReason: String) {
        bi(NOTIFICATION_FREQUENCY_LIMITED, mapOf(P_POS_ID to posId, P_LIMIT_REASON to limitReason))
    }

    /** E12 ad_blocked：到达展示点但被判定拦截且未展示，仅 bi。 */
    fun adBlocked(adType: String, posId: String, blockReason: String) {
        bi(AD_BLOCKED, mapOf(P_AD_TYPE to adType, P_POS_ID to posId, P_BLOCK_REASON to blockReason))
    }

    /** E13 ad_show_timeout：到达展示点等兜底超时后放行，仅 bi。 */
    fun adShowTimeout(adType: String, posId: String, waitMs: Long) {
        bi(AD_SHOW_TIMEOUT, mapOf(P_AD_TYPE to adType, P_POS_ID to posId, P_WAIT_MS to waitMs))
    }

    /** E21 keepalive_trigger：自拉起成功（trigger_type：boot/user_present/power/alarm/binder），仅 bi。 */
    fun keepaliveTrigger(triggerType: String) {
        bi(KEEPALIVE_TRIGGER, mapOf(P_TRIGGER_TYPE to triggerType))
    }

    /**
     * E01 app_first_open 补出口：BI 已由 TrackSDK 内部自动上报（含规范全部必带参数），
     * 这里补 adjust（token 占位）与 firebase。仅在首次安装启动时调用一次。
     */
    fun appFirstOpenExternalsIfFirstInstall() {
        if (!isFirstInstall()) return
        val context = MediaApp.get()
        val ref = ReferrerHelper.getInstance(context)
        val params = mapOf<String, Any>(
            "gaid" to DeviceUtils.getAdvertisingId(context).orEmpty(),
            "install_referrer" to ref.installReferrer.orEmpty(),
            "utm_source" to ref.utmSource.orEmpty(),
            "click_timestamp" to ref.referrerClickTimestampSeconds.toString(),
            "install_first_time" to DeviceUtils.getFirstInstallTimeMs(context).toString(),
            "install_channel" to "google_play",
        )
        Log.i(TAG, "埋点 event=$APP_FIRST_OPEN platform=Firebase params=$params")
        firebase(APP_FIRST_OPEN, params)
        adjust(APP_FIRST_OPEN, ADJUST_TOKEN_PLACEHOLDER)
    }

    /** E24 retention_d1/d7/d14/d30：安装后第 N 天回访各报一次，仅 adjust（token 占位）、firebase。 */
    fun retentionCheckOnce() {
        val context = MediaApp.get()
        val installMs = DeviceUtils.getFirstInstallTimeMs(context)
        if (installMs <= 0L) return
        val installEpochDay = java.time.Instant.ofEpochMilli(installMs)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .toEpochDay()
        val today = java.time.LocalDate.now().toEpochDay()
        val days = (today - installEpochDay).toInt()
        val nodes = listOf(
            1 to RETENTION_D1,
            7 to RETENTION_D7,
            14 to RETENTION_D14,
            30 to RETENTION_D30,
        )
        for ((day, event) in nodes) {
            if (days == day && !RetentionState.wasReported(event)) {
                RetentionState.markReported(event)
                Log.i(TAG, "埋点 event=$event platform=Firebase,Adjust")
                firebase(event, emptyMap())
                adjust(event, ADJUST_TOKEN_PLACEHOLDER)
            }
        }
    }

    /**
     * 6.4 通知成功送达（notify() 成功且权限/开关可用）：BI + Firebase。
     * 送达时若 notification_agree 尚未记录且系统运行时权限已授予，按落盘的首次提示状态补报一次。
     */
    fun notificationSend() {
        bi(NOTIFICATION_SEND, null)
        firebase(NOTIFICATION_SEND, emptyMap())
        backfillAgreeIfNeeded()
    }

    /**
     * E17/6.5 用户点击通知：NOTIFY-BASE（pos_id、trigger_source）+ media_id（产品需求参数），
     * 出口 bi、adjust、firebase（adjust token 占位）。
     * media_id 取自通知携带的 Intent（无片源 ID 时为 0，不填充其他 ID）；
     * 同一通知 10s 窗口内跨 Activity 重建/热启动转场只上报一次。
     */
    fun notificationClick(mediaId: Int, posId: String, triggerSource: String, showType: Int, notificationId: Int) {
        if (!NotificationPromptState.shouldReportClick(showType, notificationId)) {
            Log.i(TAG, "埋点 event=$NOTIFICATION_CLICK platform=dedup-skip params={media_id=$mediaId}")
            return
        }
        val params = mapOf(
            P_MEDIA_ID to mediaId,
            P_POS_ID to posId,
            P_TRIGGER_SOURCE to triggerSource,
        )
        bi(NOTIFICATION_CLICK, params)
        firebase(NOTIFICATION_CLICK, params)
        adjust(NOTIFICATION_CLICK, ADJUST_TOKEN_PLACEHOLDER)
    }

    /** 送达补报：未记录过 agree 且运行时权限已授予 → 补报一次（is_first_prompt 用落盘状态）。 */
    private fun backfillAgreeIfNeeded() {
        if (NotificationPromptState.wasAgreeReported()) return
        val context = MediaApp.get()
        val runtimeGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        if (!runtimeGranted) return
        Log.i(TAG, "埋点 event=$NOTIFICATION_AGREE platform=backfill is_first_prompt=${!NotificationPromptState.isFirstPromptDone()}")
        notificationAgree(isFirstPrompt = !NotificationPromptState.isFirstPromptDone())
    }

    /** 5.1 召回弹窗成功展示：BI。 */
    fun recallShow() = bi(RECALL_SHOW, null)

    /** 5.2 点击召回弹窗有效按钮：BI。 */
    fun recallClick() = bi(RECALL_CLICK, null)

    /** 5.3 关闭/忽略召回弹窗：BI。 */
    fun recallDismiss() = bi(RECALL_DISMISS, null)

    // ---- MediaApi 路径 → api 类型映射（供 api_error 使用） ----

    private val API_PATH_MAP: List<Pair<String, String>> = listOf(
        "home_data" to "home",
        "home_stream_data" to "home_stream",
        "module_data" to "module",
        "search" to "search",
        "filter/options" to "filter",
        "discover" to "discover",
        "streaming" to "streaming",
        "daily" to "daily",
        "config" to "config",
        "detail" to "detail",
        "outside/links" to "outsideLinks",
        "play_report" to "playReport",
    )

    /** 根据接口路径返回 api 类型；未知路径返回 null（不上报，避免误报）。 */
    fun apiNameForPath(encodedPath: String): String? {
        val path = encodedPath.trim().trim('/')
        for ((segment, name) in API_PATH_MAP) {
            if (path.contains(segment, ignoreCase = false)) return name
        }
        return null
    }
}
