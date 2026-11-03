package com.hdvideoplay.media.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

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
        onResult: (AdLoadResult) -> Unit,
    ) {
        val appContext = context.applicationContext
        var finished = false
        val timeout = Runnable {
            if (finished) return@Runnable
            finished = true
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
                        Log.i(TAG, "INT loaded unit=${node.unitId}")
                        onResult(
                            AdLoadResult.Success(
                                CachedAd(
                                    scene = "",
                                    node = node,
                                    format = AdFormat.INTERSTITIAL,
                                    loadedAtMs = System.currentTimeMillis(),
                                    payload = ad,
                                ),
                            ),
                        )
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        if (finished) return
                        finished = true
                        mainHandler.removeCallbacks(timeout)
                        Log.w(TAG, "INT fail unit=${node.unitId} code=${error.code} ${error.message}")
                        onResult(AdLoadResult.Failure(node.unitId, "${error.code}:${error.message}"))
                    }
                },
            )
        }.onFailure { e ->
            if (finished) return@onFailure
            finished = true
            mainHandler.removeCallbacks(timeout)
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
        var settled = false
        fun settle(shown: Boolean) {
            if (settled) return
            settled = true
            onDismiss(shown)
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.i(TAG, "INT dismissed loc=${location.jsonKey}")
                settle(true)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(TAG, "INT show fail ${adError.code} ${adError.message}")
                settle(false)
            }

            override fun onAdShowedFullScreenContent() {
                Log.i(TAG, "INT showed loc=${location.jsonKey} unit=${entry.node.unitId}")
            }

            override fun onAdClicked() {
                Log.i(TAG, "INT clicked (user) — do not auto-click in tests")
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
