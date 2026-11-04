package com.hdvideoplay.media.ads

import android.content.Context
import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Per-scene load/cache controller (HaloDrift nb / mi0 / ul2 / r94 / s94).
 *
 * Load API only — never shows. Show path is [AdBus.emit] / [AdScheduler.show].
 *
 * Rules (§B.3):
 * - LoadGate (jb3.u): no concurrent load Job for this scene
 * - If Ready or Loading → return
 * - Hot: if Cold still has Ready → return (skip warm preload)
 * - Waterfall by priority; timeout per node; success → Ready cache
 * - On consume: remove; if refill_on_consume → load() again
 */
class SceneController(
    val sceneName: String,
    private val appContext: () -> Context,
    private val repo: () -> AdSceneRepository?,
    private val loaders: () -> Map<AdFormat, AdFormatLoader>,
    private val coldReadyProbe: (() -> Boolean)? = null,
) {
    private val ready = CopyOnWriteArrayList<CachedAd>()

    fun hasReady(): Boolean {
        pruneExpired()
        return ready.isNotEmpty()
    }

    fun readySnapshot(): List<CachedAd> {
        pruneExpired()
        return ready.toList()
    }

    /** Lowest-priority Ready entry, or null. Does not consume. */
    fun peekLowestPriority(preferredFormat: AdFormat? = null): CachedAd? {
        pruneExpired()
        val pool = if (preferredFormat == null) ready else ready.filter { it.format == preferredFormat }
        return pool.minByOrNull { it.node.priority }
    }

    /**
     * Remove [entry] from cache; if scene refill_on_consume → [load].
     */
    fun consume(entry: CachedAd) {
        ready.removeAll { it === entry || sameSlot(it, entry) }
        val refill = repo()?.scene(sceneName)?.refillOnConsume == true
        Log.i(TAG, "consume($sceneName) unit=${entry.node.unitId} refill=$refill")
        AdLogger.consume(sceneName, entry.node.unitId, refill)
        if (refill) load()
    }

    fun refillIfNeeded() {
        if (!hasReady() && !LoadGate.isLoading(sceneName)) {
            load()
        }
    }

    /**
     * LOAD only (HaloDrift *.j()). Never shows.
     */
    /**
     * LOAD only, ignoring coldReadyProbe (V2 hot must-show refill).
     * Ordinary [load] still skips when cold Ready.
     */
    fun loadIgnoringColdProbe() {
        loadInternal(ignoreColdProbe = true)
    }

    fun load() = loadInternal(ignoreColdProbe = false)

    private fun loadInternal(ignoreColdProbe: Boolean) {
        val repository = repo()
        if (repository == null) {
            Log.w(TAG, "load($sceneName) before repo init")
            return
        }
        // Hot: if Cold still has Ready → skip warm preload (§B.3 / ul2)
        // V2 A: [loadIgnoringColdProbe] bypasses this so must-show can refill.
        if (!ignoreColdProbe &&
            sceneName == AdScenes.HOT_OPEN &&
            coldReadyProbe?.invoke() == true
        ) {
            Log.d(TAG, "load($sceneName) skipped — cold pool still Ready")
            AdLogger.loadBlocked(sceneName, "hot_skip_cold_ready")
            return
        }
        pruneExpired()
        if (ready.isNotEmpty()) {
            Log.d(TAG, "load($sceneName) skip — already Ready P${ready.first().node.priority}")
            AdLogger.loadBlocked(sceneName, "already_ready")
            return
        }
        if (!LoadGate.tryAcquire(sceneName)) {
            Log.d(TAG, "load($sceneName) skip — LoadGate (jb3.u) in flight")
            AdLogger.loadBlocked(sceneName, "load_in_flight")
            return
        }
        val nodes = repository.nodesSorted(sceneName)
        if (nodes.isEmpty()) {
            LoadGate.release(sceneName)
            Log.w(TAG, "load($sceneName) no nodes")
            AdLogger.loadBlocked(sceneName, "no_nodes")
            return
        }
        Log.i(TAG, "load($sceneName) nodes=${nodes.size} forceHot=$ignoreColdProbe")
        AdLogger.loadStart(sceneName, nodes.first().format)
        tryLoadWaterfall(nodes, index = 0)
    }

    private fun tryLoadWaterfall(nodes: List<HdAdNode>, index: Int) {
        if (index >= nodes.size) {
            LoadGate.release(sceneName)
            Log.w(TAG, "load($sceneName) waterfall exhausted")
            AdLogger.waterfallExhausted(sceneName)
            return
        }
        val node = nodes[index]
        AdLogger.waterfallTry(sceneName, index, node.platform, node.format, node.unitId)
        val format = AdFormat.fromJson(node.format)
        if (format == null) {
            Log.w(TAG, "Unknown format ${node.format}, skip")
            tryLoadWaterfall(nodes, index + 1)
            return
        }
        val loader = loaders()[format]
        if (loader == null) {
            Log.d(TAG, "No loader for ${format.jsonKey}, skip P${node.priority}")
            tryLoadWaterfall(nodes, index + 1)
            return
        }
        val timeoutMs = AdPolicy.nodeTimeoutMs()
        runCatching {
            // posId = 加载场景名（装弹池）；展示侧事件用 AdLocation.jsonKey。
            loader.load(appContext(), node, timeoutMs, sceneName) { result ->
                when (result) {
                    is AdLoadResult.Success -> {
                        val entry = result.entry.copy(scene = sceneName)
                        ready.add(entry)
                        LoadGate.release(sceneName)
                        Log.i(TAG, "load($sceneName) OK P${node.priority} ${format.jsonKey} ${node.unitId}")
                        AdLogger.loadSuccess(sceneName, node.format, node.unitId)
                    }
                    is AdLoadResult.Failure -> {
                        Log.w(TAG, "load($sceneName) fail P${node.priority}: ${result.reason}")
                        AdLogger.loadFailed(sceneName, node.format, node.unitId, result.reason)
                        tryLoadWaterfall(nodes, index + 1)
                    }
                }
            }
        }.onFailure { e ->
            // Non-cancel exceptions swallowed — do not crash (§B.3)
            Log.e(TAG, "load($sceneName) threw P${node.priority}", e)
            tryLoadWaterfall(nodes, index + 1)
        }
    }

    private fun pruneExpired() {
        val ttl = AdPolicy.cacheTtlMs()
        val now = System.currentTimeMillis()
        val loadersMap = loaders()
        ready.removeAll { entry ->
            val loader = loadersMap[entry.format]
            val expired = loader?.isExpired(entry, now, ttl)
                ?: (now - entry.loadedAtMs > ttl)
            if (expired) {
                Log.d(TAG, "prune($sceneName) expired unit=${entry.node.unitId}")
                AdLogger.cacheExpired(sceneName, entry.node.unitId, now - entry.loadedAtMs, ttl)
            }
            expired
        }
    }

    private fun sameSlot(a: CachedAd, b: CachedAd): Boolean =
        a.scene == b.scene &&
            a.node.unitId == b.node.unitId &&
            a.node.priority == b.node.priority &&
            a.loadedAtMs == b.loadedAtMs

    companion object {
        private const val TAG = "SceneController"
    }
}
