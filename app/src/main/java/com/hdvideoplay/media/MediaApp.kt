package com.hdvideoplay.media

import android.app.Application
import com.hdvideoplay.media.ads.AdScheduler
import com.hdvideoplay.media.data.local.WatchHistoryRepository
import com.hdvideoplay.media.startup.HotStartManager
import com.hdvideoplay.media.ui.recall.RecallPopupController

class MediaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WatchHistoryRepository.init(this)
        // Cold-start recall config pull; Home decides from cache afterwards.
        RecallPopupController.init(this)
        // AdMob + scene bus (Load≠Show). Employee1: App Open cold/warm.
        AdScheduler.init(this)
        HotStartManager.init(this)
    }
}
