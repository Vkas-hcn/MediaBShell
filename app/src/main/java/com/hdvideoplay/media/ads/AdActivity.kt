package com.hdvideoplay.media.ads

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.hdvideoplay.media.R
import com.hdvideoplay.media.track.AdTrackingHelper

/**
 * Native display host — mid-card OR fullscreen shell by [NativePresentMode].
 *
 * Midcard: dark scrim + wrap_content card ([R.layout.activity_ad_native]).
 * Fullscreen: interstitial-feel edge shell ([R.layout.activity_ad_native_fullscreen]).
 * Not a standard InterstitialAd.
 *
 * Close / back / destroy → [settle] → bridge onDismiss → AdScheduler settlement.
 * NEVER synthesize CTA clicks. Pairing stage-2 midcard is scheduler-owned.
 */
class AdActivity : ComponentActivity() {

    private var nativeAd: NativeAd? = null
    private var dismissed = false
    private var onDismiss: ((Boolean) -> Unit)? = null
    private var shown = false
    private var presentMode: NativePresentMode = NativePresentMode.MIDCARD
    private var showStartedAtMs = -1L
    private var closeReported = false
    private var pendingEntry: CachedAd? = null
    private var pendingLocation: AdLocation = AdLocation.RETURN_AD

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pending = AdDisplayBridge.take()
        if (pending == null) {
            Log.w(TAG, "AdActivity opened with empty bridge — finish")
            finish()
            return
        }
        onDismiss = pending.onDismiss
        presentMode = pending.presentMode
        pendingEntry = pending.entry
        pendingLocation = pending.location
        val ad = pending.entry.payload as? NativeAd
        if (ad == null) {
            Log.w(TAG, "AdActivity payload not NativeAd")
            settle(false)
            finish()
            return
        }
        nativeAd = ad
        val layout = when (presentMode) {
            NativePresentMode.FULLSCREEN -> R.layout.activity_ad_native_fullscreen
            NativePresentMode.MIDCARD -> R.layout.activity_ad_native
        }
        setContentView(layout)
        applyStatusBarInsets()
        bindNative(ad)
        shown = true
        showStartedAtMs = SystemClock.elapsedRealtime()
        AdLogger.showPresented(pendingLocation.jsonKey, pending.entry.format.jsonKey, pending.entry.node.unitId)
        // 收入：真实曝光由 AdMob 触发 OnPaidEvent → BI/Firebase + Adjust（展示前挂载）。
        val entry = pendingEntry
        if (entry != null) {
            ad.setOnPaidEventListener { adValue ->
                AdTrackingHelper.trackAdImpressionRevenueMicros(
                    this@AdActivity,
                    adValue,
                    AdTrack.adTypeOf(entry.format),
                    pendingLocation.jsonKey,
                    adId = entry.adId,
                    adSource = AdTrack.adSourceOf(ad.responseInfo),
                    sdkName = "AdMob",
                )
                AdTrackingHelper.trackAdRevenueAdjust(
                    adValue,
                    ad.responseInfo,
                    entry.node.unitId,
                )
            }
        }
        Log.i(
            TAG,
            "Native shown mode=$presentMode unit=${pending.entry.node.unitId} scene=${pending.entry.scene}",
        )

        findViewById<View>(R.id.ad_close)?.setOnClickListener {
            Log.i(TAG, "Native close tapped mode=$presentMode — no CTA click")
            settle(true)
            finish()
        }
        applyCloseArea()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    Log.i(TAG, "Native back → dismiss mode=$presentMode")
                    settle(true)
                    finish()
                }
            },
        )
    }

    /**
     * Keep the native close / AdChoices area below the status bar while the ad
     * itself stays edge-to-edge. Without this the close button starts at 12dp
     * and the status bar eats its touch target on API 35.
     */
    private fun applyStatusBarInsets() {
        val root = findViewById<View>(R.id.ad_root) ?: return
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            if (presentMode == NativePresentMode.FULLSCREEN) {
                shiftTopMargin(R.id.ad_close, top, 12)
                shiftTopMargin(R.id.ad_close_area, top, 12)
                shiftTopMargin(R.id.ad_badge, top, 10)
                shiftTopMargin(R.id.ad_choices_container, top, 32)
            } else {
                // Midcard already has 60dp root top padding; only lift the close
                // if that still leaves it inside the status bar touch region.
                val close = findViewById<View>(R.id.ad_close) ?: return@setOnApplyWindowInsetsListener insets
                val lp = close.layoutParams as? ViewGroup.MarginLayoutParams
                    ?: return@setOnApplyWindowInsetsListener insets
                val minScreenTop = top + dp(8)
                val current = root.paddingTop + lp.topMargin
                if (current < minScreenTop) {
                    lp.topMargin += minScreenTop - current
                    close.layoutParams = lp
                }
            }
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun shiftTopMargin(id: Int, insetTop: Int, baseDp: Int) {
        val v = findViewById<View>(id) ?: return
        val lp = v.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val target = dp(baseDp) + insetTop
        if (lp.topMargin != target) {
            lp.topMargin = target
            v.layoutParams = lp
        }
    }

    /**
     * §B.5: hd_close_area is dp, not percent.
     * Uses e1 [AdPolicy.resolveCloseAreaDp] (floor ≥24); display only applies pixels.
     */
    private fun applyCloseArea() {
        val closeAreaDp = AdPolicy.resolveCloseAreaDp()
        val sizePx = dp(closeAreaDp)
        val close = findViewById<View>(R.id.ad_close)
        if (close != null) {
            close.minimumWidth = sizePx
            close.minimumHeight = sizePx
            val lp = close.layoutParams
            if (lp != null) {
                if (lp.width > 0 && lp.width < sizePx) lp.width = sizePx
                if (lp.height > 0 && lp.height < sizePx) lp.height = sizePx
                close.layoutParams = lp
            }
        }
        val hot = findViewById<View>(R.id.ad_close_area)
        if (hot != null) {
            val hlp = hot.layoutParams
            hlp.width = sizePx
            hlp.height = sizePx
            hot.layoutParams = hlp
            hot.visibility = View.VISIBLE
            hot.isClickable = true
            hot.setOnClickListener {
                Log.i(TAG, "Native close-area tapped mode=$presentMode — no CTA click")
                settle(true)
                finish()
            }
        }
        Log.d(TAG, "closeAreaDp=$closeAreaDp sizePx=$sizePx mode=$presentMode")
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun bindNative(ad: NativeAd) {
        val adView = findViewById<NativeAdView>(R.id.native_ad_view)
        val headline = findViewById<TextView>(R.id.ad_headline)
        val body = findViewById<TextView>(R.id.ad_body)
        val cta = findViewById<Button>(R.id.ad_call_to_action)
        val icon = findViewById<ImageView>(R.id.ad_app_icon)
        val advertiser = findViewById<TextView>(R.id.ad_advertiser)
        val media = findViewById<MediaView>(R.id.ad_media)

        headline.text = ad.headline
        adView.headlineView = headline

        body.text = ad.body
        body.visibility = if (ad.body.isNullOrBlank()) View.GONE else View.VISIBLE
        adView.bodyView = body

        cta.text = ad.callToAction
        cta.visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
        adView.callToActionView = cta

        val iconDrawable = ad.icon?.drawable
        if (iconDrawable != null) {
            icon.setImageDrawable(iconDrawable)
            icon.visibility = View.VISIBLE
        } else {
            icon.visibility = View.GONE
        }
        adView.iconView = icon

        advertiser.text = ad.advertiser
        advertiser.visibility = if (ad.advertiser.isNullOrBlank()) View.GONE else View.VISIBLE
        adView.advertiserView = advertiser

        adView.mediaView = media
        media.mediaContent = ad.mediaContent

        adView.setNativeAd(ad)
    }

    /**
     * Always invoke scheduler callback exactly once so paired stage-2 midcard
     * can be enqueued. Do not present midcard from here.
     */
    private fun settle(shownFlag: Boolean) {
        if (dismissed) return
        dismissed = true
        val cb = onDismiss
        onDismiss = null
        // 关闭：实际展示过才上报一次，展示时长 = 展示到关闭。
        val entry = pendingEntry
        if (!closeReported && shown && shownFlag && entry != null) {
            closeReported = true
            val stay = if (showStartedAtMs > 0) {
                SystemClock.elapsedRealtime() - showStartedAtMs
            } else {
                0L
            }
            AdTrackingHelper.trackAdClose(
                AdTrack.adTypeOf(entry.format),
                pendingLocation.jsonKey,
                entry.adId,
                stay,
            )
        }
        Log.i(TAG, "settle mode=$presentMode shown=${shownFlag && shown} → scheduler callback")
        AdLogger.showDismissed(pendingLocation.jsonKey, entry?.format?.jsonKey.orEmpty(), entry?.node?.unitId.orEmpty())
        runCatching { cb?.invoke(shownFlag && shown) }
    }

    override fun onDestroy() {
        if (!dismissed) settle(shown)
        nativeAd?.destroy()
        nativeAd = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MediaAds"

        fun launch(
            activity: Activity,
            entry: CachedAd,
            presentMode: NativePresentMode = NativePresentMode.MIDCARD,
            onDismiss: (shown: Boolean) -> Unit,
            location: AdLocation = AdLocation.RETURN_AD,
        ): Boolean {
            if (activity.isFinishing) {
                onDismiss(false)
                return false
            }
            AdDisplayBridge.offer(entry, presentMode, onDismiss, location)
            return runCatching {
                activity.startActivity(Intent(activity, AdActivity::class.java))
                Log.i(TAG, "AdActivity launch mode=$presentMode")
                true
            }.getOrElse { e ->
                Log.e(TAG, "AdActivity launch failed mode=$presentMode", e)
                AdDisplayBridge.take() // drop pending; invoke once
                onDismiss(false)
                false
            }
        }

        fun createIntent(context: Context): Intent = Intent(context, AdActivity::class.java)
    }
}
