package com.hdvideoplay.media.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions

/**
 * AdMob Native loader. Show path opens [AdActivity] with [NativePresentMode]
 * from [NativePresentHints] (midcard or fullscreen shell). Not InterstitialAd.
 *
 * Acceptance: show / waterfall / dismiss only — NEVER auto-click CTAs.
 * Dismiss callbacks AdScheduler via AdActivity.settle → onDismiss.
 */
class NativeAdLoader : AdFormatLoader {

    override val format: AdFormat = AdFormat.NATIVE

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
            Log.w(TAG, "Native load timeout unit=${node.unitId}")
            onResult(AdLoadResult.Failure(node.unitId, "timeout"))
        }
        mainHandler.postDelayed(timeout, timeoutMs)

        runCatching {
            val adLoader = AdLoader.Builder(appContext, node.unitId)
                .forNativeAd { nativeAd ->
                    if (finished) {
                        nativeAd.destroy()
                        return@forNativeAd
                    }
                    finished = true
                    mainHandler.removeCallbacks(timeout)
                    Log.i(TAG, "Native loaded unit=${node.unitId}")
                    onResult(
                        AdLoadResult.Success(
                            CachedAd(
                                scene = "",
                                node = node,
                                format = AdFormat.NATIVE,
                                loadedAtMs = System.currentTimeMillis(),
                                payload = nativeAd,
                            ),
                        ),
                    )
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        if (finished) return
                        finished = true
                        mainHandler.removeCallbacks(timeout)
                        Log.w(TAG, "Native fail unit=${node.unitId} code=${error.code} ${error.message}")
                        onResult(AdLoadResult.Failure(node.unitId, "${error.code}:${error.message}"))
                    }
                })
                .withNativeAdOptions(
                    NativeAdOptions.Builder()
                        .setReturnUrlsForImageAssets(false)
                        .build(),
                )
                .build()
            adLoader.loadAd(AdRequest.Builder().build())
        }.onFailure { e ->
            if (finished) return@onFailure
            finished = true
            mainHandler.removeCallbacks(timeout)
            Log.e(TAG, "Native load threw unit=${node.unitId}", e)
            onResult(AdLoadResult.Failure(node.unitId, e.message ?: "throw"))
        }
    }

    override fun show(
        activity: Activity,
        entry: CachedAd,
        location: AdLocation,
        onDismiss: (shown: Boolean) -> Unit,
    ): Boolean {
        val nativeAd = entry.payload as? NativeAd
        if (nativeAd == null) {
            Log.w(TAG, "show: payload missing NativeAd")
            onDismiss(false)
            return false
        }
        if (activity.isFinishing) {
            onDismiss(false)
            return false
        }
        // Prefer explicit location; else one-shot hint from e1/e2.
        val hinted = NativePresentHints.take()
        val mode = when (location) {
            AdLocation.FULLSCREEN_SHELL -> NativePresentMode.FULLSCREEN
            else -> hinted
        }
        Log.i(
            TAG,
            "show Native → AdActivity mode=$mode loc=${location.jsonKey} unit=${entry.node.unitId}",
        )
        return AdActivity.launch(activity, entry, mode, onDismiss)
    }

    companion object {
        private const val TAG = "MediaAds"
    }
}
