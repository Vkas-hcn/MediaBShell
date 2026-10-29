package com.hdvideoplay.media

import android.app.Application
import com.hdvideoplay.media.ads.AdScheduler
import com.hdvideoplay.media.data.local.WatchHistoryRepository

class MediaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WatchHistoryRepository.init(this)
        // AdMob + scene bus (Load≠Show). Employee1: App Open cold/warm.
        AdScheduler.init(this)
    }
}
