package com.hdvideoplay.media.data.notice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hdvideoplay.media.track.TrackEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PhoneLockRe : BroadcastReceiver() {
    override fun onReceive(p0: Context?, p1: Intent?) {
        p0 ?: return
        val pendingResult = goAsync()
        // E21 keepalive_trigger：解锁自拉起成功。
        TrackEvents.keepaliveTrigger("user_present")
        NewsLogger.triggerReceived(HintUtil.CO_S_TYPE_UNLOCK, extra = ",延迟1500ms")
        CoroutineScope(Dispatchers.IO).launch {
            delay(1_500)
            HintUtil.sendTtwo {
                NewsLogger.triggerWorkFinished(HintUtil.CO_S_TYPE_UNLOCK)
                pendingResult.finish()
            }
        }
    }
}
