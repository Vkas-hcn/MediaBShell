package com.hdvideoplay.media.data.notice

import android.content.Context
import androidx.core.content.edit
import com.hdvideoplay.media.data.dto.MediaItemDto
import com.hdvideoplay.media.data.repo.MediaRepository
import com.hdvideoplay.media.util.PosterUtils
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/**
 * 通知内容源：
 * - 优先：随机拉取首页影视资源（home_data 各模块展平后随机取 5 条，进程内缓存 1 小时，
 *   每次发送从池中随机轮换并避免连续重复）；通知图使用该影片封面（见 PosterFetcher）。
 * - 兜底：拉取失败/无可用条目时回退本地静态文案池（3 组英文文案 + 固定图标）。
 */
object NotificationContentRepository {
    private const val PREFS_NAME = "notification_copy_rotation"
    private const val KEY_NEXT_INDEX = "next_index"
    private const val KEY_REMOTE_INDEX = "remote_index"
    private const val POOL_SIZE = 5
    private const val POOL_TTL_MS = 60 * 60 * 1000L
    private const val FETCH_TIMEOUT_MS = 5_000L
    private const val SUMMARY_MAX_LEN = 200

    /** 可注入（单测替换）。 */
    internal var mediaRepository: MediaRepository = MediaRepository()
    internal var nowProvider: () -> Long = System::currentTimeMillis
    internal var noticeConfigProvider: () -> NoticeConfig = { NoticeConfigStore.load() }

    private val lock = Any()
    private var lastId: String? = null
    private var remoteIndex = 0
    private var dynamicPool: List<NotificationContent> = emptyList()
    private var dynamicPoolFetchedAt = 0L

    private val staticPool = listOf(
        NotificationContent(
            id = "copy_1",
            title = "Fresh files, ready to play",
            summary = "New videos just landed on your device. Open and hit play.",
            action = "Browse library",
            url = "",
            icon = NotificationIcon.VIDEO,
        ),
        NotificationContent(
            id = "copy_2",
            title = "New videos just dropped in",
            summary = "Fresh files are waiting in your library. Open up and start watching.",
            action = "Open library",
            url = "",
            icon = NotificationIcon.BELL,
        ),
        NotificationContent(
            id = "copy_3",
            title = "Your library just got bigger",
            summary = "New clips showed up. Pick one and press play.",
            action = "Check it out",
            url = "",
            icon = NotificationIcon.VIDEO,
        ),
    )

    suspend fun next(context: Context?): NotificationContent? {
        // 影片/封面/跳转 ID：随机选一部（与之前完全一致）
        refreshDynamicPoolIfNeeded()
        val base = pickDynamic() ?: pickStatic(context)
        if (base == null) return null
        // 下发文案：按序轮换，覆盖随机影片的 title/overview/action（空字段回退原文案）
        val copy = pickRemoteItem(context, noticeConfigProvider().newsList)
        val content = if (copy == null) {
            base
        } else {
            base.copy(
                title = copy.title.trim().ifBlank { base.title },
                summary = copy.overview.trim().ifBlank { base.summary }.take(SUMMARY_MAX_LEN),
                action = copy.action.trim().ifBlank { base.action },
            )
        }
        return gate(content, allowRepeat = false)
    }

    fun resetCopyCache() {
        synchronized(lock) { lastId = null }
    }

    /** 测试复位（单测注入后清理）。 */
    internal fun resetPoolForTest() {
        synchronized(lock) {
            dynamicPool = emptyList()
            dynamicPoolFetchedAt = 0L
            lastId = null
            remoteIndex = 0
        }
    }

    /** 去重门闩：允许重复（单条下发列表）则直接放行；否则与上一条相同就跳过本次发送。 */
    private fun gate(content: NotificationContent, allowRepeat: Boolean): NotificationContent? {
        if (!allowRepeat && content.id == lastId) return null
        lastId = content.id
        return content
    }

    /** 远端列表按序轮换（游标持久化，跨进程累计，避免每次冷启动都从第一条开始）。 */
    private fun pickRemoteItem(context: Context?, list: List<NoticeNewsItem>): NoticeNewsItem? {
        if (list.isEmpty()) return null
        val index = advanceRemoteIndex(context, list.size)
        return list[index]
    }

    private fun advanceRemoteIndex(context: Context?, size: Int): Int {
        val prefs = context?.applicationContext
            ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs?.getInt(KEY_REMOTE_INDEX, 0) ?: remoteIndex
        val index = stored.mod(size)
        val next = (index + 1) % size
        remoteIndex = next
        prefs?.edit { putInt(KEY_REMOTE_INDEX, next) }
        return index
    }

    private suspend fun refreshDynamicPoolIfNeeded() {
        synchronized(lock) {
            if (dynamicPool.isNotEmpty() && nowProvider() - dynamicPoolFetchedAt < POOL_TTL_MS) return
        }
        val modules = withTimeoutOrNull(FETCH_TIMEOUT_MS) {
            mediaRepository.homeData().getOrNull()
        } ?: return
        val items = modules
            .flatMap { it.data }
            .filter { item ->
                item.localId() > 0 && item.title.isNotBlank() &&
                    PosterUtils.resolvePosterUrl(item.posterPath) != null
            }
        if (items.isEmpty()) return
        val pool = items.shuffled(Random).take(POOL_SIZE).map { it.toContent() }
        synchronized(lock) {
            dynamicPool = pool
            dynamicPoolFetchedAt = nowProvider()
        }
    }

    private fun pickDynamic(): NotificationContent? {
        val pool = dynamicPool
        if (pool.isEmpty()) return null
        val candidates = pool.filterNot { it.id == lastId }
        return if (candidates.isEmpty()) null
        else candidates[Random.nextInt(candidates.size)]
    }

    private fun pickStatic(context: Context?): NotificationContent? {
        context ?: return null
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val index = prefs.getInt(KEY_NEXT_INDEX, 0)
        val content = staticPool[index % staticPool.size]
        prefs.edit { putInt(KEY_NEXT_INDEX, (index + 1) % staticPool.size) }
        return content
    }

    private fun MediaItemDto.toContent(): NotificationContent = NotificationContent(
        id = "media_" + localId(),
        title = title,
        summary = overview.trim().take(SUMMARY_MAX_LEN),
        action = "Play now",
        url = "",
        icon = NotificationIcon.VIDEO,
        mediaId = localId(),
        posterUrl = PosterUtils.resolvePosterUrl(posterPath).orEmpty(),
    )
}
