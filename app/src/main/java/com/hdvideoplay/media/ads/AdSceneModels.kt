package com.hdvideoplay.media.ads

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Merged config root shared by the bundled default asset and the api/dispatch
 * remote payload: hd_ad_config + hd_load_scenes.
 */
@Serializable
data class HdDispatchConfig(
    @SerialName("hd_ad_config") val adConfig: HdAdConfig? = null,
    @SerialName("hd_load_scenes") val scenes: List<HdLoadScene> = emptyList(),
)

@Serializable
data class HdLoadScene(
    @SerialName("hd_load_scene") val name: String,
    @SerialName("hd_refill_on_consume") val refillOnConsume: Boolean = false,
    @SerialName("hd_nodes") val nodes: List<HdAdNode> = emptyList(),
)

@Serializable
data class HdAdNode(
    @SerialName("hd_platform") val platform: String = "admob",
    @SerialName("hd_format") val format: String,
    @SerialName("hd_id") val unitId: String,
    @SerialName("hd_priority") val priority: Int = 1,
)

/**
 * Ad policy defaults (HaloDrift ka / hd_ad_config).
 * [closeAreaDp] maps hd_close_area — runtime enforces ≥24 via [AdPolicy.resolveCloseAreaDp].
 * [nativeClosePercent] is JSON-compat only; entity truth is dp close area (§A.2 #10).
 */
@Serializable
data class HdAdConfig(
    @SerialName("hd_ad_interval") val adIntervalSec: Int = 60,
    @SerialName("hd_play_interval") val playIntervalSec: Int = 600,
    /** Close hot-zone in dp. Missing/<24 → 24 at runtime. */
    @SerialName("hd_close_area") val closeAreaDp: Int = 24,
    /** Present in competitor default JSON; not the runtime close truth. */
    @SerialName("hd_native_close_percent") val nativeClosePercent: Int = 10,
    @SerialName("hd_node_timeout_sec") val nodeTimeoutSec: Int = 30,
    @SerialName("hd_cache_ttl_sec") val cacheTtlSec: Int = 14400,
) {
    /** Back-compat alias used by older call sites. */
    val closeArea: Int get() = closeAreaDp
}
