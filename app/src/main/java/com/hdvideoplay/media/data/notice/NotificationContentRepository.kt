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
    private const val POOL_SIZE = 5
    private const val POOL_TTL_MS = 60 * 60 * 1000L
    private const val FETCH_TIMEOUT_MS = 5_000L
    private const val SUMMARY_MAX_LEN = 200

    /** 可注入（单测替换）。 */
    internal var mediaRepository: MediaRepository = MediaRepository()
    internal var nowProvider: () -> Long = System::currentTimeMillis

    private val lock = Any()
    private var lastId: String? = null
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
        refreshDynamicPoolIfNeeded()
        synchronized(lock) {
            val content = pickDynamic() ?: pickStatic(context)
            if (content == null) return null
            // 进程内去重：避免连续两条相同文案
            if (content.id == lastId) return null
            lastId = content.id
            return content
        }
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
        }
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
