package com.hdvideoplay.media.ads

/**
 * Ad format keys aligned with HaloDrift / hd_load_scenes JSON.
 * Rewarded / Banner are intentionally absent from default business scenes.
 *
 * Acceptance = show / waterfall / dismiss only. NEVER auto-click ad CTAs.
 * V2: do not use InterstitialAd on V2 scene paths (loader may remain registered).
 */
enum class AdFormat(val jsonKey: String) {
    LAUNCH_SCREEN("launch_screen"),
    NATIVE("native"),
    INTERSTITIAL("interstitial_ad");

    companion object {
        fun fromJson(key: String): AdFormat? =
            entries.firstOrNull { it.jsonKey.equals(key, ignoreCase = true) }
    }
}

/**
 * Show-location bus (orthogonal to scene). Scene = load/cache; location = show trigger.
 *
 * V1 pinned: first_splash_cold / splash_cold / splash_hot / return_ad
 * V2 adds: fullscreen_shell — interstitial-feel **fullscreen native shell**
 *           (成对 with return_ad midcard; e2 owns shell UI presentation)
 *
 * notify_aoa is an analytics label ([AdAnalytics]), NOT a Location.
 */
enum class AdLocation(val jsonKey: String) {
    FIRST_SPLASH_COLD("first_splash_cold"),
    SPLASH_COLD("splash_cold"),
    SPLASH_HOT("splash_hot"),
    /** Mid-card dark-scrim native (e2 AdActivity midcard). */
    RETURN_AD("return_ad"),
    /**
     * Fullscreen native shell (interstitial-feel). Same Native inventory as return_ad;
     * presentation via [NativePresentMode.FULLSCREEN] / hints. Not InterstitialAd API.
     */
    FULLSCREEN_SHELL("fullscreen_shell");

    companion object {
        fun fromJson(key: String): AdLocation? =
            entries.firstOrNull { it.jsonKey.equals(key, ignoreCase = true) }
    }
}


/**
 * Analytics labels that are NOT show Locations (gb).
 * HaloDrift: Pair("notify_aoa", videoId) — OpenActivity still uses cold/hot splash locations.
 */
object AdAnalytics {
    const val NOTIFY_AOA = "notify_aoa"
}

enum class AdPlatform(val jsonKey: String) {
    ADMOB("admob"),
    /** Reserved for remote JSON; default scenes stay AdMob-only. */
    TOPON("topon");

    companion object {
        fun fromJson(key: String): AdPlatform =
            entries.firstOrNull { it.jsonKey.equals(key, ignoreCase = true) } ?: ADMOB
    }
}

object AdScenes {
    const val COLD_OPEN_A = "cold_open_a"
    const val COLD_OPEN = "cold_open"
    const val HOT_OPEN = "hot_open"
    const val NORMAL_1 = "normal_1"
    const val NORMAL_2 = "normal_2"
}
