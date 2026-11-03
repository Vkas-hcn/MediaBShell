package com.hdvideoplay.media.ui.splash

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.flux.tracksdk.core.TrackSDK
import com.flux.tracksdk.model.TrackPolicy
import com.hdvideoplay.media.MainActivity
import com.hdvideoplay.media.ads.AdGate
import com.hdvideoplay.media.ads.AdLocation
import com.hdvideoplay.media.ads.AdScheduler
import com.hdvideoplay.media.data.notice.HintUtil
import com.hdvideoplay.media.permission.NotificationPermissionHelper
import com.hdvideoplay.media.startup.HotStartManager
import com.hdvideoplay.media.ui.theme.MediaTheme
import com.hdvideoplay.media.ui.uninstall.UninstallEntry
import com.hdvideoplay.media.ui.uninstall.UninstallRetentionActivity
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

    private var notifRequested = false
    private var notificationClickReported = false
    private var hotStartPoll: Runnable? = null

    /** 通知权限状态机；必须在 onCreate 里取一次，保证 launcher 注册早于 STARTED。 */
    private val permissionHost by lazy { NotificationPermissionHelper.bind(this) }

    private val isHotStart: Boolean
        get() = intent.getBooleanExtra(EXTRA_HOT_START, false)

    private val isReturnToMain: Boolean
        get() = intent.getBooleanExtra(EXTRA_RETURN_TO_MAIN, false)

    /** 桌面 Uninstall 快捷方式入口：广告结束后进挽留页，不落首页。 */
    private val isUninstallEntry: Boolean
        get() = UninstallEntry.isLeaveLaunch(intent)

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
        reportNotificationClickIfNeeded(intent)
        processAlreadyEntered = true

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

        if (isHotStart && AdScheduler.isShowingAd()) {
            // 通知热启动落在旧启动页/旧广告之上：先收起旧广告，再重走启动流程。
            startupEpoch++
            restartStartupAfterDismissingAd()
            return
        }
        // 启动页通知权限：只弹系统框（systemOnly），同意/拒绝/返回键都不阻塞广告流程
        requestNotificationThenProceed()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        reportNotificationClickIfNeeded(intent)
        if (intent.getBooleanExtra(EXTRA_HOT_START, false)) {
            if (isFinishing || isDestroyed) {
                // 旧启动页已经在离场（例如广告刚被 CLEAR_TOP 关闭）。
                // 延迟清栈拉起新实例，避免旧流程刚启动的 MainActivity 把新 Splash 盖住。
                startupEpoch++
                HotStartManager.relaunchGuideForHotStart(intent)
                return
            }
            // 先作废旧展示流程的回调，再收起当前全屏广告，最后重走启动流程。
            startupEpoch++
            restartStartupAfterDismissingAd()
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

    /**
     * 通知点击处理：先清掉被点的那条（新闻通知是常驻置顶的，系统不会自动清除），
     * 再做点击埋点（本地新闻通知 CO_SHOW_TYPE 或 FCM EXTRA_FROM_FCM，实例内只上报一次）。
     */
    private fun reportNotificationClickIfNeeded(intent: Intent) {
        val showType = intent.getIntExtra(HintUtil.CO_SHOW_TYPE, -1)
        if (showType >= 0) {
            val notificationId = intent.getIntExtra(HintUtil.CO_NOTIFICATION_ID, -1)
            HintUtil.cancelNewsNotification(showType, notificationId.takeIf { it >= 0 })
        }
        if (notificationClickReported) return
        val fromNotification = intent.hasExtra(HintUtil.CO_SHOW_TYPE) ||
            intent.getBooleanExtra(EXTRA_FROM_FCM, false)
        if (!fromNotification) return
        notificationClickReported = true
        runCatching { TrackSDK.track("news_notification_click", null, TrackPolicy.IMMEDIATE) }
            .onFailure { Log.w(TAG, "news_notification_click track failed", it) }
    }

    /** 通知权限 → 继续启动流程（systemOnly：只弹系统框，弹不出来也不降级，不阻塞广告）。 */
    private fun requestNotificationThenProceed() {
        // 卸载挽留入口不打断用户去要通知权限；广告流程保持原样。
        if (isUninstallEntry) return
        if (notifRequested) return
        if (NotificationPermissionHelper.isNotificationGranted(this)) return
        notifRequested = true
        permissionHost.requestNotification(systemOnly = true) { }
    }

    private fun restartStartupAfterDismissingAd() {
        if (!AdScheduler.isShowingAd()) {
            restartStartupFlow()
            return
        }
        hotStartPoll?.let(handler::removeCallbacks)
        hotStartPoll = null
        Log.d(TAG, "热启动收起正在展示的广告")
        HotStartManager.dismissCurrentAdActivity()
        val deadline = SystemClock.elapsedRealtime() + HOT_START_DISMISS_TIMEOUT_MS
        lateinit var poll: Runnable
        poll = Runnable {
            if (isFinishing || isDestroyed) return@Runnable
            if (!AdScheduler.isShowingAd()) {
                hotStartPoll = null
                restartStartupFlow()
                return@Runnable
            }
            if (SystemClock.elapsedRealtime() >= deadline) {
                hotStartPoll = null
                Log.w(TAG, "热启动收起广告超时")
                restartStartupFlow()
                return@Runnable
            }
            handler.postDelayed(poll, HOT_START_DISMISS_POLL_MS)
        }
        hotStartPoll = poll
        handler.postDelayed(poll, HOT_START_DISMISS_POLL_MS)
    }

    private fun restartStartupFlow() {
        hotStartPoll?.let(handler::removeCallbacks)
        hotStartPoll = null
        handler.removeCallbacksAndMessages(null)
        flowStarted = false
        pendingFlow = false
        finishing = false
        pendingExit = false
        // 重新渲染启动页（新的 LaunchedEffect → 重走 splash 动画 → startFlow）
        setContent {
            MediaTheme {
                SplashLoadingScreen(
                    holdMs = if (isHotStart) HOT_HOLD_MS else COLD_HOLD_MS,
                    onFinished = ::onSplashFinished,
                )
            }
        }
        requestNotificationThenProceed()
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
        val epoch = startupEpoch
        lifecycleScope.launch {
            val scene = coldScene ?: return@launch
            var waited = 0L
            while (!AdScheduler.isReady(scene) && waited < COLD_AD_WAIT_MS) {
                if (epoch != startupEpoch) return@launch
                delay(POLL_MS)
                waited += POLL_MS
            }
            if (epoch != startupEpoch) return@launch
            if (!isActive || isFinishing || isDestroyed) return@launch
            awaitResumed()
            if (epoch != startupEpoch || !isActive || isFinishing || isDestroyed) return@launch

            val started = AdScheduler.emit(
                location = AdScheduler.coldSplashLocation(),
                activity = this@SplashActivity,
            ) { _ ->
                if (epoch == startupEpoch) requestExit()
            }
            if (epoch == startupEpoch && !started) requestExit()
        }
    }

    private fun startHotAdFlow() {
        val epoch = startupEpoch
        lifecycleScope.launch {
            if (!AdScheduler.canShowHotSplash()) {
                // Frequency-blocked: keep the designed splash at least its
                // minimum time, then return to the page underneath.
                if (epoch == startupEpoch) requestExit()
                return@launch
            }
            var waited = 0L
            while (waited < HOT_AD_WAIT_MS) {
                if (epoch != startupEpoch) return@launch
                if (!isActive || isFinishing || isDestroyed) return@launch
                if (AdScheduler.isReadyFor(AdLocation.SPLASH_HOT)) {
                    awaitResumed()
                    if (epoch != startupEpoch || !isActive || isFinishing || isDestroyed) return@launch
                    var adStarted = false
                    val started = AdScheduler.emit(
                        location = AdLocation.SPLASH_HOT,
                        activity = this@SplashActivity,
                    ) { shown ->
                        // Empty/frequency-blocked emits synchronously call false;
                        // keep the splash visible until the bounded wait ends.
                        if (epoch == startupEpoch && (shown || adStarted)) requestExit()
                    }
                    adStarted = started
                    if (started) return@launch
                }
                delay(POLL_MS)
                waited += POLL_MS
            }
            if (epoch == startupEpoch) requestExit()
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
        // 桌面 Uninstall 快捷方式：开屏广告结束/超时后直接进挽留页。
        if (isUninstallEntry) {
            startActivity(Intent(this, UninstallRetentionActivity::class.java))
            finish()
            return
        }
        // 通知点击带 URL：落地系统浏览器打开该页面；无 URL 默认首页
        val targetUrl = intent.data?.toString().orEmpty()
        if (targetUrl.isNotBlank()) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
            finish()
            return
        }
        // 清栈重置与冷启动都落首页；普通热启动（仅 CLEAR_TASK 失败回退可达）回原页面
        if (isHotStart && !isReturnToMain) {
            finish()
            return
        }
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        const val TAG = "SplashActivity"
        const val EXTRA_HOT_START = "extra_hot_start"
        const val EXTRA_RETURN_TO_MAIN = "extra_return_to_main"
        const val EXTRA_FROM_FCM = "extra_from_fcm"

        private const val HOT_START_DISMISS_TIMEOUT_MS = 1_500L
        private const val HOT_START_DISMISS_POLL_MS = 50L
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

        /**
         * 通知点击入口：清栈重走启动页广告流程（NEW_TASK|CLEAR_TOP|SINGLE_TOP 语义），
         * 带 EXTRA_RETURN_TO_MAIN 保证广告结束后落主页。
         */
        fun createNotificationClickIntent(context: Context): Intent =
            Intent(context, SplashActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_HOT_START, true)
                putExtra(EXTRA_RETURN_TO_MAIN, true)
            }

        /** 普通热启动（仅 SINGLE_TOP，不清栈；当前仅作为 CLEAR_TASK 失败的回退路径）。 */
        fun createHotStartIntent(context: Context): Intent =
            Intent(context, SplashActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_HOT_START, true)
            }

        /** 回前台清栈重置（NEW_TASK|CLEAR_TASK），广告结束后落首页 MainActivity。 */
        fun createReturnToMainIntent(context: Context): Intent =
            Intent(context, SplashActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(EXTRA_HOT_START, true)
                putExtra(EXTRA_RETURN_TO_MAIN, true)
            }

        /** 进程级启动流程代数：新实例/热启动递增，旧广告回调按代数丢弃。 */
        @Volatile
        private var startupEpoch = 0

        /** 进程被系统新建时为 false；第一次进入启动页后置 true（对齐 HyperVideo）。 */
        private var processAlreadyEntered = false
    }
}
