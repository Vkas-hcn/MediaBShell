package com.hdvideoplay.media.data.notice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hdvideoplay.media.track.TrackEvents

class TimeWorkerRe : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        NewsLogger.alarmReceived()
        // E21 keepalive_trigger：闹钟自拉起成功。
        TrackEvents.keepaliveTrigger("alarm")
        val pendingResult = goAsync()
        HintUtil.sendTthree {
            NewsLogger.triggerWorkFinished(HintUtil.CO_S_TYPE_ALARM)
            pendingResult.finish()
        }
        HintUtil.markLing()
    }
}
