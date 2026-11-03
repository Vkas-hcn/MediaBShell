package com.hdvideoplay.media.ads

import android.content.Context
import android.util.Log
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
        if (!loadFromJson(raw)) {
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
