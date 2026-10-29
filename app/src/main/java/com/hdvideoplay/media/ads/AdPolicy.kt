package com.hdvideoplay.media.ads

/**
 * Ad policy helpers (HaloDrift ka/la parity).
 * closeAreaDp: hd_close_area — missing or &lt;24 → force 24dp. Do not trust percent alone.
 */
object AdPolicy {

    const val MIN_CLOSE_AREA_DP = 24

    /**
     * Resolve close hot-zone in dp. Spec §B.5:
     *   if (v != null && v >= 24) v else 24
     */
    fun resolveCloseAreaDp(cfg: HdAdConfig? = null): Int {
        val v = cfg?.closeAreaDp ?: AdScheduler.config().closeAreaDp
        return if (v >= MIN_CLOSE_AREA_DP) v else MIN_CLOSE_AREA_DP
    }

    fun adIntervalMs(cfg: HdAdConfig? = null): Long {
        val sec = (cfg ?: AdScheduler.config()).adIntervalSec
        return if (sec <= 0) 0L else sec * 1000L
    }

    fun playIntervalMs(cfg: HdAdConfig? = null): Long {
        val sec = (cfg ?: AdScheduler.config()).playIntervalSec
        return if (sec <= 0) 0L else sec * 1000L
    }

    fun nodeTimeoutMs(cfg: HdAdConfig? = null): Long {
        val sec = (cfg ?: AdScheduler.config()).nodeTimeoutSec
        return (if (sec <= 0) 30 else sec) * 1000L
    }

    fun cacheTtlMs(cfg: HdAdConfig? = null): Long {
        val sec = (cfg ?: AdScheduler.config()).cacheTtlSec
        return (if (sec <= 0) 14400 else sec) * 1000L
    }
}
