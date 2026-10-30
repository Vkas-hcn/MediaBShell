package com.hdvideoplay.media.data.recall

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single-flight config cache.
 *
 * - [refresh] force=true is used once per process (cold start).
 * - Otherwise a fresh cache is returned as-is; an expired/missing cache is
 *   fetched, and a failed attempt backs off for [retryBackoffMs].
 * - Failed fetches return the previous successful config unchanged.
 */
internal class RecallConfigRepository(
    private val remote: RecallConfigRemote,
    private val store: RecallConfigStore,
    private val key: String,
    private val ttlMs: Long = RECALL_CONFIG_TTL_MS,
    private val retryBackoffMs: Long = RECALL_CONFIG_RETRY_MS,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private val mutex = Mutex()

    @Volatile
    private var cache: RecallConfig? = store.load()

    private var lastAttemptAtMs = 0L

    fun cached(): RecallConfig? = cache

    fun isExpired(): Boolean = isExpired(now())

    fun isExpired(nowMs: Long): Boolean {
        val current = cache ?: return true
        return current.isExpired(nowMs, ttlMs)
    }

    suspend fun refresh(force: Boolean = false): RecallConfig? = mutex.withLock {
        val nowMs = now()
        val current = cache
        if (!force) {
            if (current != null && !current.isExpired(nowMs, ttlMs)) {
                return@withLock current
            }
            if (lastAttemptAtMs > 0L) {
                val elapsed = nowMs - lastAttemptAtMs
                if (elapsed in 0 until retryBackoffMs) return@withLock current
            }
        }
        lastAttemptAtMs = nowMs

        when (val fetch = remote.fetch(key)) {
            is RecallConfigFetch.Loaded -> {
                val next = RecallConfig(fetch.value.trim(), nowMs)
                cache = next
                store.save(next)
                next
            }

            RecallConfigFetch.Disabled -> {
                val next = RecallConfig("", nowMs)
                cache = next
                store.save(next)
                next
            }

            is RecallConfigFetch.Failed -> current
        }
    }
}
