package com.hdvideoplay.media.ads

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.google.android.gms.ads.nativead.AdChoicesView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.hdvideoplay.media.R
import com.hdvideoplay.media.track.AdTrackingHelper

/**
 * 内联小原生广告展示器：复用 normal_1/normal_2 已缓存的原生广告（单元 ID 来自配置），
 * 绑定到宿主容器内嵌展示（非全屏，和 HyperVideo 一致）。不单独加载广告位。
 */
class InlineNativeAdPresenter(private val host: ViewGroup) {

    private var displayedAd: NativeAd? = null
    private var displayedView: NativeAdView? = null
    private var released = false

    /** 从 normal_1/normal_2 缓存取一条原生广告内嵌展示；无缓存则保持隐藏。 */
    fun show() {
        if (released) return
        if (displayedAd != null && displayedView?.parent === host) return
        val entry = AdScheduler.takeInlineNative()
        val ad = entry?.payload as? NativeAd
        if (entry == null || ad == null) {
            host.visibility = View.GONE
            return
        }
        bind(ad, entry)
    }

    /** 宿主销毁时调用：销毁实例并丢弃在途回调。 */
    fun release() {
        released = true
        displayedAd?.destroy()
        displayedAd = null
        displayedView = null
        host.removeAllViews()
        host.visibility = View.GONE
    }

    private fun bind(ad: NativeAd, entry: CachedAd) {
        val view = LayoutInflater.from(host.context)
            .inflate(R.layout.item_native_ad_small, host, false) as NativeAdView

        val iconView = view.findViewById<ImageView>(R.id.ad_app_icon)
        val icon = ad.icon?.drawable
        if (icon != null) {
            iconView.setImageDrawable(icon)
            view.iconView = iconView
        } else {
            iconView.visibility = View.GONE
        }

        view.headlineView = view.findViewById<TextView>(R.id.ad_headline).apply {
            text = ad.headline
            visibility = if (ad.headline.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        view.bodyView = view.findViewById<TextView>(R.id.ad_body).apply {
            text = ad.body
            visibility = if (ad.body.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        view.callToActionView = view.findViewById<TextView>(R.id.ad_call_to_action).apply {
            text = ad.callToAction
            visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        view.findViewById<AdChoicesView>(R.id.ad_choices)?.let { view.adChoicesView = it }

        // 收入：真实曝光由 AdMob 触发 OnPaidEvent → BI/Firebase + Adjust。
        ad.setOnPaidEventListener { adValue ->
            AdTrackingHelper.trackAdImpressionRevenueMicros(
                host.context,
                adValue,
                AdTrack.adTypeOf(entry.format),
                "uninstall_retention",
                adId = entry.adId,
                adSource = AdTrack.adSourceOf(ad.responseInfo),
                sdkName = "AdMob",
            )
            AdTrackingHelper.trackAdRevenueAdjust(adValue, ad.responseInfo, entry.node.unitId)
        }

        displayedAd?.destroy()
        host.removeAllViews()
        host.addView(view)
        host.visibility = View.VISIBLE
        displayedAd = ad
        displayedView = view
        view.setNativeAd(ad)
        AdLogger.loadSuccess("uninstall_retention", "native", entry.node.unitId)
    }
}
