package com.hdvideoplay.media.ads

import android.content.Context
import android.util.Log
import com.hdvideoplay.media.data.notice.NoticeConfigStore
import kotlinx.serialization.json.Json

/**
 * Loads the merged bundled config ([hd_config.json]) and applies api/dispatch
 * overrides. Callers keep using [scene] / [nodesSorted] / [config].
 */
class AdSceneRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Volatile
    var scenes: Map<String, HdLoadScene> = emptyMap()
        private set

    @Volatile
    var config: HdAdConfig = HdAdConfig()
        private set

    fun loadFromAssets() {
        val raw = runCatching {
            context.assets.open(CONFIG_ASSET).bufferedReader().use { it.readText() }
        }.getOrNull()
        if (raw == null) {
            Log.e(TAG, "Failed to open $CONFIG_ASSET")
            return
        }
        if (loadFromJson(raw)) {
            // 资产配置同样落盘 notice（与 api/dispatch 路径对称）
            runCatching {
                val dto = json.decodeFromString(HdDispatchConfig.serializer(), raw)
                NoticeConfigStore.save(dto.notice)
            }.onFailure { Log.e(TAG, "Failed to apply notice config", it) }
        } else {
            Log.e(TAG, "Failed to parse $CONFIG_ASSET")
        }
    }

    /** Apply a merged config (from asset or remote JSON). */
    fun applyMerged(dto: HdDispatchConfig) {
        scenes = dto.scenes.associateBy { it.name }
        config = dto.adConfig ?: HdAdConfig()
        Log.i(TAG, "Loaded scenes=" + scenes.keys + " interval=" + config.adIntervalSec + "s ttl=" + config.cacheTtlSec + "s")
    }

    /** Parse merged JSON and apply it. Returns false on malformed JSON. */
    fun loadFromJson(raw: String): Boolean = runCatching {
        applyMerged(json.decodeFromString(HdDispatchConfig.serializer(), raw))
        true
    }.getOrElse { e ->
        Log.e(TAG, "Failed to parse config JSON", e)
        false
    }

    fun scene(name: String): HdLoadScene? = scenes[name]

    fun nodesSorted(sceneName: String): List<HdAdNode> =
        scene(sceneName)?.nodes?.sortedBy { it.priority }.orEmpty()

    companion object {
        private const val TAG = "AdSceneRepo"
        const val CONFIG_ASSET = "hd_config.json"
    }
}
