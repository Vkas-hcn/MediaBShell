package com.hdvideoplay.media.track

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.core.content.edit
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustAdRevenue
import com.adjust.sdk.AdjustEvent

import com.flux.tracksdk.core.TrackSDK
import com.flux.tracksdk.model.TrackPolicy
import com.flux.tracksdk.utils.UsdFromAdMicros
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.ResponseInfo
import com.google.firebase.analytics.FirebaseAnalytics
import com.hdvideoplay.media.BuildConfig
import com.hdvideoplay.media.MediaApp

import java.util.Locale.getDefault
import java.util.TimeZone
import java.util.UUID

/**
 * 广告埋点辅助类
 * 用于统一管理广告相关的埋点上报
 */
object AdTrackingHelper {
    private const val TAG = "AdTrackingHelper"

    private val revenueLock = Any()
    private const val PREF_NAME = "ad_tracking_prefs"
    private const val KEY_TOTAL_ADS_REVENUE = "total_ads_revenue_001_accumulated"

    /**
     * 广告类型
     */
    object AdType {
        const val BANNER = "banner"
        const val NATIVE = "native"
        const val INTERSTITIAL = "interstitial"
        const val OPEN = "open"
        const val REWARDED = "rewarded"
    }

    /**
     * 上报广告请求（AdMob 聚合）。
     * 规范（§2.3）：ad_id = 广告单元 ID，同一形态同一次展示各环用同一个值，
     * 故调用方必须传单元 ID（默认仅兜底旧用法）。出口 bi、firebase。
     */
    fun trackAdRequestGMA(
        adType: String,
        posId: String,
        adId: String = UUID.randomUUID().toString(),
    ): String {
        TrackSDK.trackAdRequest(adType, posId, "AdMob", adId)
        firebaseAdBase("ad_request", mapOf(
            "ad_type" to adType,
            "pos_id" to posId,
            "sdk_name" to "AdMob",
            "ad_id" to adId,
        ))
        return adId
    }

    /**
     * 上报广告填充成功（显式传入来源，适配 AdMob）
     */
    fun trackAdFilledWithSource(
        adType: String,
        posId: String,
        adId: String,
        sdkName: String,
        adSource: String,
        duration: Long
    ) {
        TrackSDK.trackAdFilled(adType, posId, sdkName, adId, adSource, duration)
        firebaseAdBase("ad_filled", mapOf(
            "ad_type" to adType,
            "pos_id" to posId,
            "sdk_name" to sdkName,
            "ad_id" to adId,
            "ad_source" to adSource,
            "duration" to duration,
        ))
    }

    /**
     * 上报广告填充失败
     * @param adType 广告类型
     * @param posId 位置ID
     * @param adId 唯一请求ID
     * @param errCode 错误码
     * @param errMsg 错误描述
     */
    fun trackAdFailed(
        adType: String,
        posId: String,
        adId: String,
        errCode: String,
        errMsg: String
    ) {
        // 将 errCode 转换为 Int，如果转换失败使用 -1
        val errCodeInt = errCode.toIntOrNull() ?: -1
        TrackSDK.trackAdFailed(adType, posId, "AdMob", adId, errCodeInt, errMsg)
        firebaseAdBase("ad_failed", mapOf(
            "ad_type" to adType,
            "pos_id" to posId,
            "sdk_name" to "AdMob",
            "ad_id" to adId,
            "err_code" to errCodeInt,
            "err_msg" to errMsg,
        ))
    }

    /**
     * 上报广告点击（显式传入来源，适配 AdMob）
     */
    fun trackAdClickWithSource(
        adType: String,
        posId: String,
        adId: String,
        sdkName: String,
        adSource: String,
        clickArea: String = ""
    ) {
        TrackSDK.trackAdClick(adType, posId, sdkName, adId, adSource, clickArea)
        firebaseAdBase("ad_click", mapOf(
            "ad_type" to adType,
            "pos_id" to posId,
            "sdk_name" to sdkName,
            "ad_id" to adId,
            "ad_source" to adSource,
            "click_area" to clickArea,
        ))
    }

    /**
     * 上报广告关闭
     * @param adType 广告类型
     * @param posId 位置ID
     * @param adId 唯一请求ID
     * @param stayDuration 展示多少秒后关闭
     */
    fun trackAdClose(
        adType: String,
        posId: String,
        adId: String,
        stayDuration: Long
    ) {
        // SDK 的 trackAdClose 缺 sdk_name（规范 AD-BASE 必带）：直发补全。
        TrackSDK.track("ad_close", mapOf(
            "ad_type" to adType,
            "pos_id" to posId,
            "sdk_name" to "AdMob",
            "ad_id" to adId,
            "stay_duration" to stayDuration,
        ), TrackPolicy.IMMEDIATE)
        firebaseAdBase("ad_close", mapOf(
            "ad_type" to adType,
            "pos_id" to posId,
            "sdk_name" to "AdMob",
            "ad_id" to adId,
            "stay_duration" to stayDuration,
        ))
        MediaApp.get().flushTrackEvents()
    }


    fun trackAdRevenueAdjust(
        adValue: AdValue,
        responseInfo: ResponseInfo?,
        adUnitId: String,
    ) {
        runCatching {
            val adRevenue = AdjustAdRevenue("admob_sdk")
            val valueM = if (BuildConfig.DEBUG) {
                6000L
            } else {
                adValue.valueMicros
            }
            val usd: Double = UsdFromAdMicros.toUsd(
                valueM,
                adValue.currencyCode.uppercase(getDefault()),
                null
            )
            if (usd < 0) return
            adRevenue.setRevenue(usd, "USD")
            adRevenue.setAdRevenueNetwork("Admob")
            adRevenue.setAdRevenueUnit(adUnitId)
            Adjust.trackAdRevenue(adRevenue)
            Log.d(
                TAG,
                "trackAdRevenueAdjust usd=$usd unit=$adUnitId network=${responseInfo?.mediationAdapterClassName}"
            )
        }.onFailure {
            Log.e(TAG, "trackAdRevenueAdjust failed unit=$adUnitId", it)
        }
    }

    /**
     * 上报广告收益（AdMob OnPaidEvent，使用 micros）
     */
    fun trackAdImpressionRevenueMicros(
        context: Context,
        adValue: AdValue,
        adType: String,
        posId: String,
        adId: String = "",
        adSource: String = "admob",
        sdkName: String = "AdMob",
    ) {
        val valueM = if (BuildConfig.DEBUG) {
            6000L
        } else {
            adValue.valueMicros
        }
        val originalCurrency = adValue.currencyCode.uppercase(getDefault())
        val revenue: Double = UsdFromAdMicros.toUsd(valueM, originalCurrency, null)
        if (revenue < 0) return
        val currencyCode = "USD"
        trackRevenueEvent(
            context = context,
            event = "ad_impression_revenue",
            revenue = revenue,
            currencyCode = currencyCode,
            adType = adType,
            posId = posId,
            adId = adId,
            adSource = adSource,
            sdkName = sdkName,
        )
        trackTotalAdsRevenueIfNeeded(context, revenue)
        Log.d(TAG, "BI Firebase Facebook usd=$revenue adType=$adType")
    }


    /**
     * 各平台互相独立上报：任一平台抛出异常不得影响其余平台，否则排在后面的平台会被整条丢弃。
     */
    fun trackRevenueEvent(
        context: Context,
        event: String,
        revenue: Double,
        currencyCode: String = "USD",
        adType: String,
        posId: String,
        adId: String = "",
        adSource: String = "admob",
        sdkName: String = "AdMob",
    ) {
        //Firebase：AD-SOURCE + value + currency
        runCatching {
            val bundle = Bundle().apply {
                putDouble(FirebaseAnalytics.Param.VALUE, revenue)
                putString(FirebaseAnalytics.Param.CURRENCY, currencyCode)
                putString("ad_type", adType)
                putString("pos_id", posId)
                putString("sdk_name", sdkName)
                putString("ad_id", adId)
                putString("ad_source", adSource)
            }
            FirebaseAnalytics.getInstance(context).logEvent(event, bundle)
        }.onFailure {
            Log.e(TAG, "trackRevenueEvent Firebase failed: $event", it)
        }
        //BI：ad_impression_revenue（不再使用已作废的 ad_impression_value / ad_show 事件名）。
        runCatching {
            TrackSDK.track(event, mapOf(
                "value" to revenue,
                "currency" to currencyCode,
                "ad_type" to adType,
                "pos_id" to posId,
                "sdk_name" to sdkName,
                "ad_id" to adId,
                "ad_source" to adSource,
            ), TrackPolicy.IMMEDIATE)
            Log.d(TAG, "trackRevenueEvent BI: $event revenue=$revenue currency=$currencyCode posId=$posId")
        }.onFailure {
            Log.e(TAG, "trackRevenueEvent BI failed: $event", it)
        }

    }


    private fun trackTotalAdsRevenueIfNeeded(
        context: Context,
        revenue: Double,
    ) {
        synchronized(revenueLock) {
            val prefs = context.applicationContext.trackingPrefs()
            val totalRevenue = prefs.getDouble(KEY_TOTAL_ADS_REVENUE, 0.0) + revenue
            if (totalRevenue >= 0.01) {
                trackValueEvent(
                    context = context,
                    event = "total_ads_revenue_001",
                    revenue = totalRevenue,
                    currencyCode = "USD"
                )
                prefs.putDouble(KEY_TOTAL_ADS_REVENUE, 0.0)
            } else {
                prefs.putDouble(KEY_TOTAL_ADS_REVENUE, totalRevenue)
            }
        }
    }





    private fun Context.trackingPrefs(): SharedPreferences {
        return applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private fun SharedPreferences.putDouble(key: String, value: Double) {
        edit { putLong(key, value.toBits()) }
    }

    private fun SharedPreferences.getDouble(key: String, defaultValue: Double): Double {
        if (!contains(key)) return defaultValue
        return Double.fromBits(getLong(key, defaultValue.toBits()))
    }

    private fun trackValueEvent(
        context: Context,
        event: String,
        revenue: Double,
        currencyCode: String = "USD"
    ) {
        putFirebaseAdRevenue(context, event, revenue, currencyCode)
        runCatching {
            val props = hashMapOf<String, Any>(
                FirebaseAnalytics.Param.VALUE to revenue,
                FirebaseAnalytics.Param.CURRENCY to currencyCode,
            )
            TrackSDK.track(event, props, TrackPolicy.IMMEDIATE)
            Log.d(TAG, "trackValueEvent: $event revenue=$revenue currency=$currencyCode")
        }.onFailure {
            Log.e(TAG, "trackValueEvent failed: $event", it)
        }
        // E11 total_ads_revenue_001 补 Adjust 出口（专用收入接口，不走普通事件）。
        runCatching {
            val adRevenue = AdjustAdRevenue("total_ads_revenue")
            adRevenue.setRevenue(revenue, "USD")
            Adjust.trackAdRevenue(adRevenue)
        }.onFailure {
            Log.e(TAG, "trackValueEvent Adjust failed: $event", it)
        }
    }

    /** Firebase 广告基础事件出口（AD-BASE/AD-SOURCE 参数原样透传）。 */
    private fun firebaseAdBase(event: String, params: Map<String, Any>) {
        runCatching {
            val bundle = Bundle()
            params.forEach { (k, v) ->
                when (v) {
                    is Int -> bundle.putInt(k, v)
                    is Long -> bundle.putLong(k, v)
                    is Double -> bundle.putDouble(k, v)
                    else -> bundle.putString(k, v.toString())
                }
            }
            FirebaseAnalytics.getInstance(MediaApp.get()).logEvent(event, bundle)
        }.onFailure {
            Log.e(TAG, "firebaseAdBase failed: $event", it)
        }
    }

    fun putFirebaseAdRevenue(
        context: Context,
        event: String,
        revenue: Double,
        currencyCode: String = "USD",
    ) {
        runCatching {
            val bundle = Bundle().apply {
                putDouble(FirebaseAnalytics.Param.VALUE, revenue)
                putString(FirebaseAnalytics.Param.CURRENCY, currencyCode)
            }
            FirebaseAnalytics.getInstance(context).logEvent(event, bundle)
        }.onFailure {
            Log.e(TAG, "trackRevenueEvent failed: $event", it)
        }
    }
}
