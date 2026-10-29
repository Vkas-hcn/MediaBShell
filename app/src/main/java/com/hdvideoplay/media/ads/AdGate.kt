package com.hdvideoplay.media.ads

import android.content.Context
import android.content.Intent

/**
 * A/B cold-start UserGate (HaloDrift jb3.x parity).
 * true → paid / deeplink / AF → [AdScenes.COLD_OPEN]
 * false → organic → [AdScenes.COLD_OPEN_A]
 *
 * Distinct from [LoadGate] (jb3.u = load Job concurrency only).
 *
 * Full deeplink + AppsFlyer OR lands with Sprint gate; until then defaults organic
 * unless [forcePaidTraffic] or an Intent extra flips it.
 */
object AdGate {

    const val EXTRA_PAID_TRAFFIC = "extra_paid_traffic"

    /** Debug / QA override. Production should leave false and use Intent / AF. */
    @Volatile
    var forcePaidTraffic: Boolean = false

    fun isPaidTraffic(context: Context, intent: Intent? = null): Boolean {
        if (forcePaidTraffic) return true
        if (intent?.getBooleanExtra(EXTRA_PAID_TRAFFIC, false) == true) return true
        // Placeholder for install-period DL / AF deep-link flags.
        return false
    }

    fun coldScene(context: Context, intent: Intent? = null): String =
        if (isPaidTraffic(context, intent)) AdScenes.COLD_OPEN else AdScenes.COLD_OPEN_A
}
