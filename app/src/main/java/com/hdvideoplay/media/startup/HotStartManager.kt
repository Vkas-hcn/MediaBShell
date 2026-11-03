package com.hdvideoplay.media.startup

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.hdvideoplay.media.ads.AdLocation
import com.hdvideoplay.media.ads.AdScheduler
import com.hdvideoplay.media.ui.splash.SplashActivity

/**
 * Process foreground watcher, aligned with EfileScan's AppStartupManager:
 * - arm 300ms after a real background
 * - first direct-show if a hot App Open is ready
 * - otherwise open SplashActivity, which shows the design and retries loading
 */
object HotStartManager : DefaultLifecycleObserver {

    private const val TAG = "HotStartManager"
    private const val ARM_DELAY_MS = 300L
    private const val LAUNCH_GUARD_MS = 1_500L

    private lateinit var app: Application
    private val handler = Handler(Looper.getMainLooper())

    private var initialized = false
    private var hasSeenForeground = false
    private var armed = false
    private var launching = false
    private var pendingArm: Runnable? = null
    private var ignoreNextBackgroundReturn = false

    /** 进程前台状态（通知门禁等使用；阶段4 重写时保留该 API）。 */
    @Volatile
    private var processInForeground = true

    fun isAppForeground(): Boolean = processInForeground

    fun init(application: Application) {
        if (initialized) return
        initialized = true
        app = application
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        Log.i(TAG, "registered process foreground watcher")
    }

    override fun onStart(owner: LifecycleOwner) {
        processInForeground = true
        cancelPendingArm()
        if (!hasSeenForeground) {
            // Cold launch: SplashActivity is already the launcher.
            hasSeenForeground = true
            Log.d(TAG, "first foreground — hot splash not armed")
            return
        }
        if (!armed) return
        armed = false
        if (launching) return

        val activity = AdScheduler.currentActivityOr()
        if (activity is SplashActivity || SplashActivity.isShowing) {
            Log.d(TAG, "foreground skip — splash already showing")
            return
        }
        if (AdScheduler.isShowingAd()) {
            Log.d(TAG, "foreground skip — fullscreen ad showing")
            return
        }
        if (activity != null && isAdActivity(activity)) {
            Log.d(TAG, "foreground skip — ad activity on top")
            return
        }
        if (!isScreenInteractive()) {
            Log.d(TAG, "foreground skip — screen not interactive")
            return
        }

        // Requirement: if inventory is ready and frequency allows, show directly.
        if (AdScheduler.canShowHotSplash() &&
            activity != null && !activity.isFinishing && !activity.isDestroyed
        ) {
            val started = AdScheduler.show(
                location = AdLocation.SPLASH_HOT,
                activity = activity,
            ) { shown ->
                Log.i(TAG, "direct hot App Open dismiss shown=$shown")
            }
            if (started) {
                Log.i(TAG, "foreground — direct hot App Open started")
                return
            }
        }

        // No ready/displayable ad: open the design splash and keep loading there.
        if (AdScheduler.isShowingAd()) return
        launchSplash(activity)
    }

    override fun onStop(owner: LifecycleOwner) {
        processInForeground = false
        if (!hasSeenForeground) return
        // 去系统设置页等系统界面不算真正离开后台，跳过一次热启动判定
        if (ignoreNextBackgroundReturn) {
            ignoreNextBackgroundReturn = false
            Log.d(TAG, "启动广告判定=跳过热启动 原因=主动跳系统页")
            return
        }
        cancelPendingArm()
        // Load while we are backgrounded so the next foreground can show directly.
        AdScheduler.forcePreloadHot()

        val task = Runnable {
            pendingArm = null
            armed = true
            Log.d(TAG, "background armed (${ARM_DELAY_MS}ms debounce elapsed)")
        }
        pendingArm = task
        handler.postDelayed(task, ARM_DELAY_MS)
    }

    private fun cancelPendingArm() {
        pendingArm?.let(handler::removeCallbacks)
        pendingArm = null
    }

    /**
     * 告诉「热启动判定」下一次退后台不是用户真的离开（例如去系统设置页开通知权限），
     * 避免用户从设置返回时被当成回前台而重走开屏。
     */
    fun ignoreNextBackgroundReturn(reason: String) {
        ignoreNextBackgroundReturn = true
        Log.d(TAG, "启动广告判定=忽略本次退后台 原因=" + reason)
    }

    private fun launchSplash(activity: Activity?) {
        if (launching) return
        launching = true
        val target: Activity? = activity?.takeUnless { it.isFinishing || it.isDestroyed }
        try {
            val intent = SplashActivity.hotIntent(target ?: app)
            if (target == null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            (target ?: app).startActivity(intent)
            Log.i(TAG, "foreground — splash fallback started target=${target?.javaClass?.simpleName ?: "app"}")
        } catch (t: Throwable) {
            Log.e(TAG, "failed to open hot splash", t)
        } finally {
            handler.postDelayed({ launching = false }, LAUNCH_GUARD_MS)
        }
    }

    private fun isScreenInteractive(): Boolean {
        val power = app.getSystemService(PowerManager::class.java) ?: return true
        return power.isInteractive
    }

    private fun isAdActivity(activity: Activity): Boolean {
        val name = activity.javaClass.name
        return name == "com.google.android.gms.ads.AdActivity" ||
            name == "com.hdvideoplay.media.ads.AdActivity"
    }
}
