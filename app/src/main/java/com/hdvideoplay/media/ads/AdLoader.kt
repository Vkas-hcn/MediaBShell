package com.hdvideoplay.media.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.ResponseInfo
import com.hdvideoplay.media.track.AdTrackingHelper

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
        /** 加载场景名（装弹池），作为广告请求/填充的 posId。 */
        posId: String,
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
    /** trackAdRequestGMA 生成的请求 ID；展示侧（关闭/点击/收益）沿用同一 adId。 */
    val adId: String = "",
)

/**
 * 广告埋点接线公共映射（仅调用 AdTrackingHelper，不改其实现）：
 * - AdFormat → AdTrackingHelper.AdType；
 * - ResponseInfo → 广告来源（聚合渠道）。
 */
internal object AdTrack {
    fun adTypeOf(format: AdFormat): String = when (format) {
        AdFormat.LAUNCH_SCREEN -> AdTrackingHelper.AdType.OPEN
        AdFormat.NATIVE -> AdTrackingHelper.AdType.NATIVE
        AdFormat.INTERSTITIAL -> AdTrackingHelper.AdType.INTERSTITIAL
    }

    fun adSourceOf(info: ResponseInfo?): String =
        info?.loadedAdapterResponseInfo?.adSourceName ?: info?.mediationAdapterClassName ?: ""
}
