package com.hdvideoplay.media.data.recall

import android.util.Log
import com.hdvideoplay.media.data.api.MediaApi
import com.hdvideoplay.media.data.api.NetworkModule
import com.hdvideoplay.media.track.TrackEvents
import kotlinx.coroutines.CancellationException

internal interface RecallConfigRemote {
    suspend fun fetch(key: String): RecallConfigFetch
}

internal class MediaRecallConfigRemote(
    private val api: MediaApi = NetworkModule.api,
) : RecallConfigRemote {

    override suspend fun fetch(key: String): RecallConfigFetch {
        val start = System.currentTimeMillis()
        val raw = try {
            api.mediaConfig(key)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            // HTTP/网络异常由 NetworkModule 拦截器上报 api_error。
            return RecallConfigFetch.Failed(t)
        }
        // 业务失败（code!=1 且非「配置关闭」20001）→ api_error（HTTP 200）。
        if (!raw.isSuccess && raw.code != "20001") {
            TrackEvents.apiError(
                api = "config",
                httpCode = 200,
                costMs = System.currentTimeMillis() - start,
            )
        }
        val result = parseRecallConfig(raw)
        when (result) {
            is RecallConfigFetch.Loaded ->
                Log.i(TAG, "fetch ok key=$key enabled=${result.value.isNotBlank()}")
            RecallConfigFetch.Disabled ->
                Log.i(TAG, "fetch off (config not found) key=$key")
            is RecallConfigFetch.Failed ->
                Log.w(TAG, "fetch failed key=$key", result.error)
        }
        return result
    }

    private companion object {
        const val TAG = "RecallConfig"
    }
}
