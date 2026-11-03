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
import com.hdvideoplay.media.data.notice.NoticeConfigStore
import com.hdvideoplay.media.data.notice.NewsNotificationManager
import com.hdvideoplay.media.startup.HotStartManager
import com.hdvideoplay.media.ui.recall.RecallPopupController

class MediaApp : Application() {

    private val beForTool = BeForTool()

    override fun onCreate() {
        super.onCreate()
        initTrackSdk()
        DataTool.init(this)
        NoticeConfigStore.init(this)
        WatchHistoryRepository.init(this)
        // Cold-start recall config pull; Home decides from cache afterwards.
        RecallPopupController.init(this)
        // AdMob + scene bus (Load≠Show). Employee1: App Open cold/warm.
        AdScheduler.init(this)
        HotStartManager.init(this)
        NewsNotificationManager.init(this)
        DispatchManager.init(this)
        beForTool.launchRefData()
        initAdJust(this)
    }

    private fun initTrackSdk() {
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
        val appToken = ""
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
}
