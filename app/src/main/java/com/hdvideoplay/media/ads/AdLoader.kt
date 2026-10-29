package com.hdvideoplay.media.ads

import android.app.Activity
import android.content.Context

/**
 * Per-format loader contract. Employee1 owns [AdFormat.LAUNCH_SCREEN].
 * Employee2 hooks [AdFormat.NATIVE] / [AdFormat.INTERSTITIAL] by replacing stubs.
 *
 * Load ≠ Show: [load] only fills cache; [show] presents a previously loaded entry.
 */
interface AdFormatLoader {
    val format: AdFormat

    fun load(
        context: Context,
        node: HdAdNode,
        timeoutMs: Long,
        onResult: (AdLoadResult) -> Unit,
    )

    /**
     * @return true if show was started (caller waits for [onDismiss]).
     */
    fun show(
        activity: Activity,
        entry: CachedAd,
        location: AdLocation,
        onDismiss: (shown: Boolean) -> Unit,
    ): Boolean

    fun isExpired(entry: CachedAd, nowMs: Long, ttlMs: Long): Boolean =
        nowMs - entry.loadedAtMs > ttlMs
}

sealed class AdLoadResult {
    data class Success(val entry: CachedAd) : AdLoadResult()
    data class Failure(val unitId: String, val reason: String) : AdLoadResult()
}

/**
 * Opaque cache slot. Format-specific payload lives in [payload]
 * (e.g. AppOpenAd instance). Do not click / invoke CTA from code.
 */
data class CachedAd(
    val scene: String,
    val node: HdAdNode,
    val format: AdFormat,
    val loadedAtMs: Long,
    val payload: Any,
)
