package com.hdvideoplay.media

import android.annotation.SuppressLint
import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustConfig
import com.adjust.sdk.LogLevel
import com.flux.tracksdk.core.TrackConfig
import com.flux.tracksdk.core.TrackSDK
import com.flux.tracksdk.model.TrackPolicy
import com.hdvideoplay.media.ads.AdScheduler
import com.hdvideoplay.media.data.local.WatchHistoryRepository
import com.hdvideoplay.media.data.ref.BeForTool
import com.hdvideoplay.media.data.ref.DataTool
import com.hdvideoplay.media.data.ref.DispatchManager
import com.hdvideoplay.media.data.notice.HintUtil
import com.hdvideoplay.media.data.notice.NoticeConfigStore
import com.hdvideoplay.media.data.notice.fcm.FcmNotificationDecision
import com.hdvideoplay.media.startup.HotStartManager
import com.hdvideoplay.media.track.BootTracking
import com.hdvideoplay.media.ui.recall.RecallPopupController

class MediaApp : Application() {

    private val beForTool = BeForTool()

    override fun onCreate() {
        super.onCreate()
        instance = this
        initTrackSdk()
        DataTool.init(this)
        NoticeConfigStore.init(this)
        WatchHistoryRepository.init(this)
        // Cold-start recall config pull; Home decides from cache afterwards.
        RecallPopupController.init(this)
        // AdMob + scene bus (Load≠Show). Employee1: App Open cold/warm.
        AdScheduler.init(this)
        HotStartManager.init(this)
        // 通知运行时：动态广播注册 + 轮询协程 + FCM 订阅 + 闹钟补排（各自兜底，失败不阻断启动）。
        runCatching { HintUtil.init(this) }
            .onFailure { Log.w(TAG, "notification runtime init failed", it) }
        runCatching { HintUtil.startJob(this) }
            .onFailure { Log.w(TAG, "notification job enqueue failed", it) }
        runCatching { FcmNotificationDecision.onRuntimeReady() }
            .onFailure { Log.w(TAG, "FCM runtime ready failed", it) }
        DispatchManager.init(this)
        beForTool.launchRefData()
        // Adjust 先初始化：activated 等事件上报需要 SDK 就绪。
        initAdJust(this)
        // 启动期埋点任务：E23 activated + E01 补出口（adjust/firebase）+ E24 留存。
        BootTracking.run()
    }

    private fun initTrackSdk() {
        //BI 埋点平台初始化
        runCatching {
            val trackConfig = TrackConfig.Builder(this)
                .baseUrl(
                    if (BuildConfig.DEBUG) {
                        "https://testbi.googletogoogle.com"
                    } else {
                        "https://bi.googletogoogle.com"
                    },
                )
                .debug(BuildConfig.DEBUG)
                .appId(packageName)
                .channel("google_play")
                .batchSize(20)
                .defaultUploadPolicy(TrackPolicy.BATCH)
                .uploadThreadCount(2)
                .maxRetryCount(5)
                .connectTimeout(10_000)
                .readTimeout(10_000)
                .enablePiggyback(true)
                .enableAppList(false)
                .build()
            TrackSDK.init(trackConfig)
        }.onFailure { Log.w("MediaApp", "TrackSDK init failed: " + it.message) }
    }

    fun flushTrackEvents() {
        val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return
        val capabilities = connectivityManager.activeNetwork
            ?.let(connectivityManager::getNetworkCapabilities) ?: return
        if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        ) {
            TrackSDK.flush()
        }
    }

    @SuppressLint("HardwareIds")
    private fun initAdJust(application: Application) {
        val deviceId = TrackSDK.getInstance().effectiveDistinctId
        //TODO 正式环境填入对应appToken
        val appToken = "xhxnvxbpetj4"
        val environment: String = if (BuildConfig.DEBUG) {
            AdjustConfig.ENVIRONMENT_SANDBOX
        } else {
            AdjustConfig.ENVIRONMENT_PRODUCTION
        }
        val config = AdjustConfig(application, appToken, environment)
        config.setLogLevel(LogLevel.WARN)
        config.setExternalDeviceId(deviceId)
        config.enableFirstSessionDelay()
        config.enableSendingInBackground()
        Adjust.initSdk(config)
        Adjust.endFirstSessionDelay()
    }

    companion object {
        private const val TAG = "MediaApp"
        private lateinit var instance: MediaApp
        fun get(): MediaApp = instance
    }
}
