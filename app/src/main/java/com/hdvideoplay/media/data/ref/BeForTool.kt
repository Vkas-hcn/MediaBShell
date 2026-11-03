package com.hdvideoplay.media.data.ref

import android.content.Context
import android.util.Log
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BeForTool {
    var refGetJob: Job? = null

    fun launchRefData() {
        when {
            DataTool.ref_can.isNotEmpty() -> {
                goToAdmin()
            }

            else -> startRefMonitoring(DataTool.appAll)
        }
    }

    private fun startRefMonitoring(context: Context) {
        refGetJob?.cancel()
        refGetJob = CoroutineScope(Dispatchers.IO).launch {
            while (DataTool.ref_can.isEmpty()) {
                getRefData(context)
                delay(11111)
            }
        }
    }

    fun getRefData(context: Context) {
        try {
            val client = InstallReferrerClient.newBuilder(context).build()
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    when (responseCode) {
                        InstallReferrerClient.InstallReferrerResponse.OK -> {
                            val referrer = client.installReferrer.installReferrer
                            if(referrer.isNotEmpty()){
                                DataTool.ref_can = referrer
                                DataTool.ref_can_ts =
                                    client.installReferrer.referrerClickTimestampSeconds.toString()
                                DataTool.ref_can_tss =
                                    client.installReferrer.referrerClickTimestampServerSeconds.toString()
                                goToAdmin()
                            }
                        }
                    }
                    runCatching { client?.endConnection() }
                        .onFailure { Log.w("Referrer", "End connection failed: ${it.message}") }
                }

                override fun onInstallReferrerServiceDisconnected() {
                    runCatching { client?.endConnection() }
                        .onFailure { Log.w("Referrer", "End connection failed: ${it.message}") }
                }
            })
        } catch (e: Exception) {
        }
    }
    //请求/api/dispatch接口数据
    fun goToAdmin(){
        Log.e("TAG", "goto")
    }
}