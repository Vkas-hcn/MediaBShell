package com.hdvideoplay.media.ads

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json

/**
 * Loads bundled [hd_load_scenes.json] + [hd_ad_config.json].
 * Remote override can replace [scenes] later without changing callers.
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
        scenes = runCatching {
            context.assets.open(SCENES_ASSET).bufferedReader().use { it.readText() }
                .let { json.decodeFromString(HdLoadScenesRoot.serializer(), it) }
                .scenes
                .associateBy { it.name }
        }.getOrElse { e ->
            Log.e(TAG, "Failed to parse $SCENES_ASSET", e)
            emptyMap()
        }

        config = runCatching {
            context.assets.open(CONFIG_ASSET).bufferedReader().use { it.readText() }
                .let { json.decodeFromString(HdAdConfig.serializer(), it) }
        }.getOrElse { e ->
            Log.e(TAG, "Failed to parse $CONFIG_ASSET", e)
            HdAdConfig()
        }

        Log.i(TAG, "Loaded scenes=${scenes.keys} interval=${config.adIntervalSec}s ttl=${config.cacheTtlSec}s")
    }

    fun scene(name: String): HdLoadScene? = scenes[name]

    fun nodesSorted(sceneName: String): List<HdAdNode> =
        scene(sceneName)?.nodes?.sortedBy { it.priority }.orEmpty()

    companion object {
        private const val TAG = "AdSceneRepo"
        const val SCENES_ASSET = "hd_load_scenes.json"
        const val CONFIG_ASSET = "hd_ad_config.json"
    }
}
