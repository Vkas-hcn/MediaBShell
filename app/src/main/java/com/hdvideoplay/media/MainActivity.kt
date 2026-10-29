package com.hdvideoplay.media

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hdvideoplay.media.ads.AdAnalytics
import com.hdvideoplay.media.ads.AdGate
import com.hdvideoplay.media.ads.AdLocation
import com.hdvideoplay.media.ads.AdScenes
import com.hdvideoplay.media.ads.AdScheduler
import com.hdvideoplay.media.ui.nav.AppNav
import com.hdvideoplay.media.ui.splash.ColdStartPhase
import com.hdvideoplay.media.ui.splash.SplashLoadingScreen
import com.hdvideoplay.media.ui.theme.MediaTheme
import com.hdvideoplay.media.ui.theme.PageBg
import kotlinx.coroutines.delay

/**
 * Cold start: Loading + load(cold_open_a|cold_open) → emit(first_splash_cold|splash_cold) → Main.
 * Warm App Open is handled inside [AdScheduler] (ProcessLifecycleOwner → splash_hot).
 *
 * Employee2 owns: Main onResume preload(hot/normal_*), return_ad on Detail/Search, AdActivity.
 * Acceptance = show / waterfall / dismiss only — NEVER auto-click ad CTAs.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val coldScene = AdGate.coldScene(this, intent)
        AdScheduler.setColdScene(coldScene)
        // LOAD ahead of SHOW (does not block UI). Load ≠ Show.
        AdScheduler.load(coldScene)

        setContent {
            MediaTheme {
                var phase by rememberSaveable { mutableStateOf(ColdStartPhase.Loading) }
                when (phase) {
                    ColdStartPhase.Loading -> SplashLoadingScreen(
                        onFinished = { phase = ColdStartPhase.AdShell },
                    )
                    ColdStartPhase.AdShell -> {
                        val activity = this@MainActivity
                        var attempted by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            // Grace so App Open load can finish after splash chrome.
                            var waited = 0
                            while (!AdScheduler.isReady(coldScene) && waited < 2500) {
                                delay(100)
                                waited += 100
                            }
                            if (attempted) return@LaunchedEffect
                            attempted = true
                            val location = AdScheduler.coldSplashLocation()
                            val started = AdScheduler.emit(
                                location = location,
                                activity = activity,
                            ) { _ ->
                                phase = ColdStartPhase.Ready
                            }
                            if (!started) {
                                phase = ColdStartPhase.Ready
                            }
                        }
                        // Dark hold while AOA shows (SDK owns fullscreen). No fake CTA.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(PageBg),
                        )
                    }
                    ColdStartPhase.Ready -> AppNav()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Employee2: preload hot + content pools (Load ≠ Show).
        // Hot SceneController skips itself when cold pool still has Ready.
        AdScheduler.load(AdScenes.HOT_OPEN)
        AdScheduler.load(AdScenes.NORMAL_1)
        AdScheduler.load(AdScenes.NORMAL_2)
    }

    /**
     * Notification-click entry. [AdAnalytics.NOTIFY_AOA] is an analytics label only —
     * show still uses cold/hot splash Locations (not a fake notify Location / gb).
     */
    fun showNotifyAoa(videoId: String? = null, onDone: () -> Unit = {}) {
        Log.i(TAG, "analytics ${AdAnalytics.NOTIFY_AOA} videoId=$videoId")
        val hasInventory = AdScheduler.hasColdOrHotReady()
        if (!hasInventory) {
            val cold = AdScheduler.coldScene()
            AdScheduler.load(cold)
            val loc = AdScheduler.coldSplashLocation()
            val started = AdScheduler.emit(loc, this) { onDone() }
            if (!started) onDone()
        } else {
            // Already have inventory → splash_hot; do not reload cold (§B.4 notify_aoa).
            val started = AdScheduler.emit(AdLocation.SPLASH_HOT, this) { onDone() }
            if (!started) onDone()
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
