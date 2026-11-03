package com.hdvideoplay.media.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.hdvideoplay.media.track.AdTrackingHelper

/**
 * Interstitial (`interstitial_ad`) capability reserved.
 * Default [hd_config.json] has **no** INT nodes — loader exists so remote JSON can enable later.
 * No rewarded / banner paths.
 *
 * Acceptance: show / waterfall / dismiss only — NEVER auto-click CTAs.
 */
class InterstitialAdLoader : AdFormatLoader {

    override val format: AdFormat = AdFormat.INTERSTITIAL

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun load(
        context: Context,
        node: HdAdNode,
        timeoutMs: Long,
        posId: String,
        onResult: (AdLoadResult) -> Unit,
    ) {
        val appContext = context.applicationContext
        val adType = AdTrack.adTypeOf(format)
        // 广告请求（load）：ad_id = 广告单元 ID（规范 §2.3），请求/填充/失败/关闭/收入各环同一值。
        val adId = AdTrackingHelper.trackAdRequestGMA(adType, posId, node.unitId)
        val requestStartMs = SystemClock.elapsedRealtime()
        var finished = false
        val timeout = Runnable {
            if (finished) return@Runnable
            finished = true
            AdTrackingHelper.trackAdFailed(adType, posId, adId, "-1", "timeout")
            Log.w(TAG, "INT load timeout unit=${node.unitId}")
            onResult(AdLoadResult.Failure(node.unitId, "timeout"))
        }
        mainHandler.postDelayed(timeout, timeoutMs)

        runCatching {
            InterstitialAd.load(
                appContext,
                node.unitId,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        if (finished) return
                        finished = true
                        mainHandler.removeCallbacks(timeout)
                        // 加载成功（填充）：duration = 请求耗时 ms。
                        AdTrackingHelper.trackAdFilledWithSource(
                            adType, posId, adId, "AdMob",
                            AdTrack.adSourceOf(ad.responseInfo),
                            SystemClock.elapsedRealtime() - requestStartMs,
                        )
                        Log.i(TAG, "INT loaded unit=${node.unitId}")
                        onResult(
                            AdLoadResult.Success(
                                CachedAd(
                                    scene = "",
                                    node = node,
                                    format = AdFormat.INTERSTITIAL,
                                    loadedAtMs = System.currentTimeMillis(),
                                    payload = ad,
                                    adId = adId,
                                ),
                            ),
                        )
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        if (finished) return
                        finished = true
                        mainHandler.removeCallbacks(timeout)
                        AdTrackingHelper.trackAdFailed(
                            adType, posId, adId, error.code.toString(), error.message.orEmpty(),
                        )
                        Log.w(TAG, "INT fail unit=${node.unitId} code=${error.code} ${error.message}")
                        onResult(AdLoadResult.Failure(node.unitId, "${error.code}:${error.message}"))
                    }
                },
            )
        }.onFailure { e ->
            if (finished) return@onFailure
            finished = true
            mainHandler.removeCallbacks(timeout)
            AdTrackingHelper.trackAdFailed(adType, posId, adId, "-1", e.message ?: "throw")
            Log.e(TAG, "INT load threw unit=${node.unitId}", e)
            onResult(AdLoadResult.Failure(node.unitId, e.message ?: "throw"))
        }
    }

    override fun show(
        activity: Activity,
        entry: CachedAd,
        location: AdLocation,
        onDismiss: (shown: Boolean) -> Unit,
    ): Boolean {
        val ad = entry.payload as? InterstitialAd
        if (ad == null) {
            Log.w(TAG, "show: payload missing InterstitialAd")
            onDismiss(false)
            return false
        }
        if (activity.isFinishing) {
            onDismiss(false)
            return false
        }
        val adType = AdTrack.adTypeOf(entry.format)
        var settled = false
        var showStartedAtMs = -1L
        var closeReported = false
        fun settle(shown: Boolean) {
            if (settled) return
            settled = true
            onDismiss(shown)
        }

        // 收入：真实曝光由 AdMob 触发 OnPaidEvent → BI/Firebase + Adjust（展示前挂载）。
        ad.setOnPaidEventListener { adValue ->
            AdTrackingHelper.trackAdImpressionRevenueMicros(
                activity, adValue, adType, location.jsonKey,
                adId = entry.adId,
                adSource = AdTrack.adSourceOf(ad.responseInfo),
                sdkName = "AdMob",
            )
            AdTrackingHelper.trackAdRevenueAdjust(adValue, ad.responseInfo, entry.node.unitId)
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.i(TAG, "INT dismissed loc=${location.jsonKey}")
                // 关闭：实际展示过才上报，展示时长 = 出全屏到关闭。
                if (!closeReported && showStartedAtMs > 0) {
                    closeReported = true
                    AdTrackingHelper.trackAdClose(
                        adType,
                        location.jsonKey,
                        entry.adId,
                        SystemClock.elapsedRealtime() - showStartedAtMs,
                    )
                }
                settle(true)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(TAG, "INT show fail ${adError.code} ${adError.message}")
                settle(false)
            }

            override fun onAdShowedFullScreenContent() {
                showStartedAtMs = SystemClock.elapsedRealtime()
                Log.i(TAG, "INT showed loc=${location.jsonKey} unit=${entry.node.unitId}")
            }

            override fun onAdClicked() {
                Log.i(TAG, "INT clicked (user) — do not auto-click in tests")
                AdTrackingHelper.trackAdClickWithSource(
                    adType, location.jsonKey, entry.adId, "AdMob",
                    AdTrack.adSourceOf(ad.responseInfo), "",
                )
            }
        }
        return runCatching {
            ad.show(activity)
            true
        }.getOrElse { e ->
            Log.e(TAG, "INT show threw", e)
            settle(false)
            false
        }
    }

    companion object {
        private const val TAG = "MediaAds"
    }
}
