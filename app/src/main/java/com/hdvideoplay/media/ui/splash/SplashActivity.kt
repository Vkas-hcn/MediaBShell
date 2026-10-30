package com.hdvideoplay.media.ui.splash

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.hdvideoplay.media.MainActivity
import com.hdvideoplay.media.ads.AdGate
import com.hdvideoplay.media.ads.AdLocation
import com.hdvideoplay.media.ads.AdScheduler
import com.hdvideoplay.media.ui.theme.MediaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Cold/hot startup splash.
 *
 * Cold launch: SplashActivity is the launcher; after the cold App Open (or empty
 * inventory) it starts MainActivity.
 *
 * Hot return: only used as the no-cache fallback. If HotStartManager already has
 * a ready App Open, it shows it directly and this Activity is not launched.
 */
class SplashActivity : ComponentActivity() {

    private val isHotStart: Boolean
        get() = intent.getBooleanExtra(EXTRA_HOT_START, false)

    private val handler = Handler(Looper.getMainLooper())
    private var coldScene: String? = null
    private var flowStarted = false
    private var pendingFlow = false
    private var finishing = false
    private var pendingExit = false

    private val watchdog: Runnable = object : Runnable {
        override fun run() {
            if (finishing || isFinishing || isDestroyed) return
            if (AdScheduler.isShowingAd()) {
                handler.postDelayed(this, WATCHDOG_POLL_MS)
            } else {
                requestExit()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        isShowing = true

        // Splash must not be skipped; its watchdog exits after a bounded wait.
        onBackPressedDispatcher.addCallback(this) { }

        if (isHotStart) {
            AdScheduler.forcePreloadHot()
        } else {
            val scene = AdGate.coldScene(this, intent)
            coldScene = scene
            AdScheduler.setColdScene(scene)
            // LOAD ahead of SHOW (Load != Show).
            AdScheduler.load(scene)
        }

        setContent {
            MediaTheme {
                SplashLoadingScreen(
                    holdMs = if (isHotStart) HOT_HOLD_MS else COLD_HOLD_MS,
                    onFinished = ::onSplashFinished,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (pendingFlow) {
            pendingFlow = false
            startFlow()
        }
        if (pendingExit) {
            pendingExit = false
            doExit()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        isShowing = false
        super.onDestroy()
    }

    private fun onSplashFinished() {
        if (flowStarted || pendingFlow || finishing || isFinishing || isDestroyed) return
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            pendingFlow = true
            return
        }
        startFlow()
    }

    private fun startFlow() {
        if (flowStarted || finishing || isFinishing || isDestroyed) return
        flowStarted = true
        handler.postDelayed(watchdog, WATCHDOG_MS)
        if (isHotStart) startHotAdFlow() else startColdAdFlow()
    }

    private suspend fun awaitResumed() {
        while (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            if (isFinishing || isDestroyed) return
            delay(POLL_MS)
        }
    }

    private fun startColdAdFlow() {
        lifecycleScope.launch {
            val scene = coldScene ?: return@launch
            var waited = 0L
            while (!AdScheduler.isReady(scene) && waited < COLD_AD_WAIT_MS) {
                delay(POLL_MS)
                waited += POLL_MS
            }
            if (!isActive || isFinishing || isDestroyed) return@launch
            awaitResumed()
            if (!isActive || isFinishing || isDestroyed) return@launch

            val started = AdScheduler.emit(
                location = AdScheduler.coldSplashLocation(),
                activity = this@SplashActivity,
            ) { _ ->
                requestExit()
            }
            if (!started) requestExit()
        }
    }

    private fun startHotAdFlow() {
        lifecycleScope.launch {
            if (!AdScheduler.canShowHotSplash()) {
                // Frequency-blocked: keep the designed splash at least its
                // minimum time, then return to the page underneath.
                requestExit()
                return@launch
            }
            var waited = 0L
            while (waited < HOT_AD_WAIT_MS) {
                if (!isActive || isFinishing || isDestroyed) return@launch
                if (AdScheduler.isReadyFor(AdLocation.SPLASH_HOT)) {
                    awaitResumed()
                    if (!isActive || isFinishing || isDestroyed) return@launch
                    var adStarted = false
                    val started = AdScheduler.emit(
                        location = AdLocation.SPLASH_HOT,
                        activity = this@SplashActivity,
                    ) { shown ->
                        // Empty/frequency-blocked emits synchronously call false;
                        // keep the splash visible until the bounded wait ends.
                        if (shown || adStarted) requestExit()
                    }
                    adStarted = started
                    if (started) return@launch
                }
                delay(POLL_MS)
                waited += POLL_MS
            }
            requestExit()
        }
    }

    /** Cold exits to Main; hot finishes back to the page underneath. */
    private fun requestExit() {
        if (finishing || isFinishing || isDestroyed) return
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            pendingExit = true
            return
        }
        doExit()
    }

    private fun doExit() {
        if (finishing || isFinishing || isDestroyed) return
        finishing = true
        handler.removeCallbacksAndMessages(null)
        if (isHotStart) {
            finish()
        } else {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    companion object {
        const val EXTRA_HOT_START = "extra_hot_start"

        private const val COLD_HOLD_MS = 1400L
        private const val HOT_HOLD_MS = 1_400L
        private const val COLD_AD_WAIT_MS = 2_500L
        private const val HOT_AD_WAIT_MS = 1_200L
        private const val POLL_MS = 100L
        private const val WATCHDOG_MS = 8_000L
        private const val WATCHDOG_POLL_MS = 500L

        /** Set for the lifetime of the visible splash; HotStartManager reads it. */
        @Volatile
        var isShowing: Boolean = false
            private set

        fun hotIntent(context: Context): Intent =
            Intent(context, SplashActivity::class.java)
                .putExtra(EXTRA_HOT_START, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
