package com.hdvideoplay.media.data.local

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Local watch-history store backed by `filesDir/watch_history.json`.
 * Singleton via [get]; optional warm-up via [init].
 */
class WatchHistoryRepository private constructor(
    context: Context,
) {
    private val file = File(context.filesDir, FILE_NAME)
    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val _items = MutableStateFlow(loadFromDisk())

    suspend fun upsert(progress: WatchProgress) {
        mutex.withLock {
            val updated = progress.copy(updatedAt = System.currentTimeMillis())
            val next = (
                listOf(updated) + _items.value.filterNot { it.mediaId == updated.mediaId }
                )
                .sortedByDescending { it.updatedAt }
                .take(MAX_ITEMS)
            persist(next)
            _items.value = next
        }
    }

    fun observeContinueWatching(limit: Int = 20): Flow<List<WatchProgress>> =
        _items.map { list ->
            list.sortedByDescending { it.updatedAt }.take(limit)
        }

    fun observeHistory(): Flow<List<WatchProgress>> =
        _items.map { list -> list.sortedByDescending { it.updatedAt } }

    suspend fun get(mediaId: Int): WatchProgress? = mutex.withLock {
        _items.value.find { it.mediaId == mediaId }
    }

    suspend fun clear() {
        mutex.withLock {
            persist(emptyList())
            _items.value = emptyList()
        }
    }

    private fun loadFromDisk(): List<WatchProgress> {
        if (!file.exists()) return emptyList()
        return try {
            val text = file.readText()
            if (text.isBlank()) emptyList()
            else json.decodeFromString(ListSerializer(WatchProgress.serializer()), text)
                .sortedByDescending { it.updatedAt }
                .take(MAX_ITEMS)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun persist(items: List<WatchProgress>) {
        file.writeText(json.encodeToString(ListSerializer(WatchProgress.serializer()), items))
    }

    companion object {
        private const val FILE_NAME = "watch_history.json"
        private const val MAX_ITEMS = 100

        @Volatile
        private var instance: WatchHistoryRepository? = null

        /** Optional warm-up from [com.hdvideoplay.media.MediaApp.onCreate]. */
        fun init(context: Context): WatchHistoryRepository = get(context)

        fun get(context: Context): WatchHistoryRepository {
            val existing = instance
            if (existing != null) return existing
            return synchronized(this) {
                instance ?: WatchHistoryRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
