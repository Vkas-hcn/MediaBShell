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
import com.google.android.gms.ads.appopen.AppOpenAd

/**
 * AdMob App Open (`launch_screen`) loader.
 *
 * Acceptance: show / waterfall / dismiss only — never auto-click CTAs.
 */
class AppOpenAdLoader : AdFormatLoader {

    override val format: AdFormat = AdFormat.LAUNCH_SCREEN

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
            Log.w(TAG, "AppOpen load timeout unit=${node.unitId}")
            onResult(AdLoadResult.Failure(node.unitId, "timeout"))
        }
        mainHandler.postDelayed(timeout, timeoutMs)

        runCatching {
            AppOpenAd.load(
                appContext,
                node.unitId,
                AdRequest.Builder().build(),
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        if (finished) return
                        finished = true
                        mainHandler.removeCallbacks(timeout)
                        Log.i(TAG, "AppOpen loaded unit=${node.unitId}")
                        onResult(
                            AdLoadResult.Success(
                                CachedAd(
                                    scene = "",
                                    node = node,
                                    format = AdFormat.LAUNCH_SCREEN,
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
                        Log.w(TAG, "AppOpen fail unit=${node.unitId} code=${error.code} ${error.message}")
                        onResult(AdLoadResult.Failure(node.unitId, "${error.code}:${error.message}"))
                    }
                },
            )
        }.onFailure { e ->
            if (finished) return@onFailure
            finished = true
            mainHandler.removeCallbacks(timeout)
            Log.e(TAG, "AppOpen load threw unit=${node.unitId}", e)
            onResult(AdLoadResult.Failure(node.unitId, e.message ?: "throw"))
        }
    }

    override fun show(
        activity: Activity,
        entry: CachedAd,
        location: AdLocation,
        onDismiss: (shown: Boolean) -> Unit,
    ): Boolean {
        val ad = entry.payload as? AppOpenAd
        if (ad == null) {
            Log.w(TAG, "show: payload missing AppOpenAd")
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
                Log.i(TAG, "AppOpen dismissed loc=${location.jsonKey}")
                settle(true)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(TAG, "AppOpen show fail ${adError.code} ${adError.message}")
                settle(false)
            }

            override fun onAdShowedFullScreenContent() {
                Log.i(TAG, "AppOpen showed loc=${location.jsonKey} unit=${entry.node.unitId}")
            }

            override fun onAdClicked() {
                // Observability only — never synthesize clicks from automation.
                Log.i(TAG, "AppOpen clicked (user) — do not auto-click in tests")
            }
        }

        return runCatching {
            ad.show(activity)
            true
        }.getOrElse { e ->
            Log.e(TAG, "AppOpen show threw", e)
            settle(false)
            false
        }
    }

    companion object {
        private const val TAG = "AppOpenAdLoader"
    }
}
