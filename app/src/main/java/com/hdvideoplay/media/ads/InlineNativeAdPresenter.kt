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

/**
 * 内联小原生广告展示器：把 NativeAd 绑定到宿主容器内嵌展示（非全屏，和 HyperVideo 一致）。
 *
 * - 复用现有 [NativeAdLoader] 加载（含请求/填充/失败埋点与超时），只做「加载→绑定→内嵌展示」；
 * - 同一容器同一时刻最多展示一条广告；重复调用不会重复绑定；
 * - [release] 之后到达的回调全部忽略并销毁实例。
 */
class InlineNativeAdPresenter(
    private val host: ViewGroup,
    private val unitId: String,
    private val slot: String = "uninstall_retention",
) {

    private val loader = NativeAdLoader()
    private var displayedAd: NativeAd? = null
    private var displayedView: NativeAdView? = null
    private var released = false
    private var loading = false

    /** 已展示则跳过；否则请求一次加载并内嵌展示。 */
    fun show() {
        if (released) return
        if (displayedAd != null && displayedView?.parent === host) return
        if (loading) return
        loading = true
        val node = HdAdNode(platform = "admob", format = "native", unitId = unitId, priority = 1)
        loader.load(host.context, node, 30_000L, slot) { result ->
            loading = false
            if (released) return@load
            when (result) {
                is AdLoadResult.Success -> bind(result.entry.payload as? NativeAd)
                is AdLoadResult.Failure -> {
                    AdLogger.loadFailed(slot, "native", unitId, result.reason)
                    host.visibility = View.GONE
                }
            }
        }
    }

    /** 宿主销毁时调用：销毁实例并丢弃在途回调。 */
    fun release() {
        released = true
        loading = false
        displayedAd?.destroy()
        displayedAd = null
        displayedView = null
        host.removeAllViews()
        host.visibility = View.GONE
    }

    private fun bind(ad: NativeAd?) {
        if (ad == null || released) return
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

        // 旧实例在广告位取新实例时销毁，保证同一时刻只有一条有效展示。
        displayedAd?.destroy()
        host.removeAllViews()
        host.addView(view)
        host.visibility = View.VISIBLE
        displayedAd = ad
        displayedView = view
        view.setNativeAd(ad)
        AdLogger.loadSuccess(slot, "native", unitId)
    }
}
