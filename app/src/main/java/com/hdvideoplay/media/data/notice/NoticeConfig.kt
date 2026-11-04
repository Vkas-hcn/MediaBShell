package com.hdvideoplay.media.data.notice

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NoticeConfig(
    @SerialName("copop_config") val pop: PopConfig = PopConfig(),
    @SerialName("banner_notify") val banner: BannerNotifyConfig = BannerNotifyConfig(),
    /** 服务端下发的通知内容列表（按序轮换；media_id<=0 的条目走客户端随机池）。 */
    @SerialName("news_list") val newsList: List<NoticeNewsItem> = emptyList(),
)

/**
 * 服务端下发的一条通知内容：
 * - media_id <=0 视为未填 → 客户端随机选一部电影和封面（与现有随机池一致）；
 * - 标题/简介/海报缺失时按 media_id 调详情接口补全；
 * - 海报始终缺失时通知回退固定图标。
 */
@Serializable
data class NoticeNewsItem(
    @SerialName("media_id") val mediaId: Int = 0,
    val title: String = "",
    val overview: String = "",
    @SerialName("poster_url") val posterUrl: String = "",
    val action: String = "",
)

@Serializable
data class PopConfig(
    @SerialName("co_a") val alarmCooldownMinutes: Int = 10,
    @SerialName("co_t") val intervalCooldownMinutes: Int = 1,
    @SerialName("co_u") val unlockCooldownMinutes: Int = 0,
    @SerialName("co_on") val enabled: Int = 1,
    @SerialName("nopop_end") val quietEndHour: Int = 6,
    @SerialName("co_a_limit") val alarmDailyLimit: Int = 140,
    @SerialName("co_t_limit") val intervalDailyLimit: Int = 240,
    @SerialName("co_u_limit") val unlockDailyLimit: Int = 140,
    @SerialName("nopop_start") val quietStartHour: Int = 23,
)

@Serializable
data class BannerNotifyConfig(
    @SerialName("ring_on") val ringEnabled: Int = 0,
    @SerialName("banner_on") val enabled: Int = 0,
    @SerialName("banner_time") val durationSeconds: Int = 10,
)

/** Local notice preferences; server payload stays the canonical serialized source. */
object NoticeConfigStore {
    private const val PREFS = "notice_config"
    private const val KEY_RAW = "raw"
    @Volatile private var appContext: android.content.Context? = null

    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun init(context: android.content.Context) {
        appContext = context.applicationContext
    }

    fun load(): NoticeConfig {
        val context = appContext ?: return NoticeConfig()
        val raw = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getString(KEY_RAW, null) ?: return NoticeConfig()
        return runCatching {
            json.decodeFromString(NoticeConfig.serializer(), raw)
        }.getOrDefault(NoticeConfig())
    }

    fun save(config: NoticeConfig) {
        appContext?.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            ?.edit()
            ?.putString(
                KEY_RAW,
                kotlinx.serialization.json.Json.encodeToString(NoticeConfig.serializer(), config),
            )
            ?.apply()
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long {
        val context = appContext ?: return defaultValue
        return context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getLong(key, defaultValue)
    }

    fun putLong(key: String, value: Long) {
        appContext?.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            ?.edit()?.putLong(key, value)?.apply()
    }

    /** 允许时段判定：from=min(start,end)，to=max(start,end)，允许 hour in from..to。 */
    internal fun isHourAllowed(hour: Int, start: Int, end: Int): Boolean {
        val from = minOf(start, end)
        val to = maxOf(start, end)
        return hour in from..to
    }
}
