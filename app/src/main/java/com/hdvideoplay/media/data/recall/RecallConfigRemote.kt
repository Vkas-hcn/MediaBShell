package com.hdvideoplay.media.data.recall

import android.util.Log
import com.hdvideoplay.media.data.api.MediaApi
import com.hdvideoplay.media.data.api.NetworkModule
import kotlinx.coroutines.CancellationException

internal interface RecallConfigRemote {
    suspend fun fetch(key: String): RecallConfigFetch
}

internal class MediaRecallConfigRemote(
    private val api: MediaApi = NetworkModule.api,
) : RecallConfigRemote {

    override suspend fun fetch(key: String): RecallConfigFetch {
        val result = try {
            parseRecallConfig(api.mediaConfig(key))
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            RecallConfigFetch.Failed(t)
        }
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
