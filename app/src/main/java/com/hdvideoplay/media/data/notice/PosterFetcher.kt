package com.hdvideoplay.media.data.notice

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.hdvideoplay.media.data.api.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/** 通知封面：折叠小图 128px、展开大图 360px。 */
data class NotificationPoster(
    val small: Bitmap,
    val large: Bitmap,
)

/**
 * 通知封面下载器：按 URL 进程内缓存（最多 8 条，超出整体清空）。
 * 下载失败/超时返回 null，调用方回退固定图标。
 */
object PosterFetcher {
    private const val TAG = "NewsPoster"
    private const val SMALL_PX = 128
    private const val LARGE_PX = 360
    private const val DOWNLOAD_TIMEOUT_MS = 5_000L
    private const val MAX_CACHE_ENTRIES = 8

    private val cache = ConcurrentHashMap<String, NotificationPoster>()

    suspend fun load(content: NotificationContent): NotificationPoster? {
        val url = content.posterUrl
        if (url.isBlank()) return null
        cache[url]?.let { return it }
        val bytes = download(url) ?: return null
        val small = decode(bytes, SMALL_PX) ?: return null
        val large = decode(bytes, LARGE_PX) ?: return null
        val poster = NotificationPoster(small, large)
        if (cache.size >= MAX_CACHE_ENTRIES) cache.clear()
        cache[url] = poster
        return poster
    }

    private suspend fun download(url: String): ByteArray? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) {
            runCatching {
                val request = Request.Builder().url(url).get().build()
                NetworkModule.okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.bytes() else null
                }
            }.onFailure { Log.w(TAG, "poster download failed", it) }
                .getOrNull()
        }
    }

    private fun decode(bytes: ByteArray, targetPx: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetPx) sample *= 2
        BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }.onFailure { Log.w(TAG, "poster decode failed", it) }.getOrNull()
}
