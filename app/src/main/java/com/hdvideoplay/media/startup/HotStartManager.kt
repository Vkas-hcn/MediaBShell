package com.hdvideoplay.media.startup

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.hdvideoplay.media.MainActivity
import com.hdvideoplay.media.ads.AdScheduler
import com.hdvideoplay.media.ui.splash.SplashActivity
import java.lang.ref.WeakReference

/**
 * 热启动判定（对齐 HyperVideo HyperVideoApplication 的启动广告判定）：
 * - 退后台 300ms 防抖后武装；回前台清栈重拉启动页（CLEAR_TASK），广告结束落首页；
 * - CLEAR_TASK 失败回退普通热启动（SINGLE_TOP，广告结束回原页面）；
 * - 去系统设置页（ignoreNextBackgroundReturn）不算真正离开后台；
 * - 广告加载/展示仍走现有 AdScheduler（SPLASH_HOT），本对象不直接展示广告。
 */
object HotStartManager : DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {

    private const val TAG = "HotStartManager"
    private const val HOT_START_DEBOUNCE_MS = 300L
    private const val HOT_START_RELAUNCH_DELAY_MS = 300L

    private lateinit var app: Application
    private val mainHandler = Handler(Looper.getMainLooper())

    private var initialized = false
    private var hasSeenForeground = false
    private var hotStartArmed = false
    private var pendingArmRunnable: Runnable? = null
    private var pendingHotStartRelaunch: Runnable? = null
    private var ignoreNextBackgroundReturn = false

    private val contentActivities = LinkedHashSet<Activity>()
    private var topActivity: WeakReference<Activity>? = null
    private var adActivity: WeakReference<Activity>? = null

    @Volatile
    private var processInForeground = true

    fun isAppForeground(): Boolean = processInForeground

    fun init(application: Application) {
        if (initialized) return
        initialized = true
        app = application
        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        Log.i(TAG, "hot start watcher registered")
    }

    override fun onStart(owner: LifecycleOwner) {
        cancelPendingArm()
        processInForeground = true
        val shouldLaunch = hotStartArmed && hasSeenForeground
        hotStartArmed = false
        hasSeenForeground = true
        if (!shouldLaunch) return
        mainHandler.post {
            if (!processInForeground) return@post
            launchReturnToGuide()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        processInForeground = false
        dismissCurrentAdActivity()
        // 去系统设置页等系统界面不算真正离开后台，跳过一次热启动判定
        if (ignoreNextBackgroundReturn) {
            ignoreNextBackgroundReturn = false
            Log.d(TAG, "启动广告判定=跳过热启动 原因=主动跳系统页")
            return
        }
        // 冷启动尚未进过前台不武装
        if (!hasSeenForeground) {
            Log.d(TAG, "启动广告判定=进入后台 热启动不武装 原因=冷启动尚未进过前台")
            return
        }
        // Load while we are backgrounded so the next foreground can show directly.
        AdScheduler.forcePreloadHot()
        scheduleArm()
    }

    /** 退后台 300ms 防抖后再武装；期间回前台则取消。 */
    private fun scheduleArm() {
        cancelPendingArm()
        pendingArmRunnable = Runnable {
            pendingArmRunnable = null
            if (processInForeground) return@Runnable
            hotStartArmed = true
            finishNonWhitelisted()
            Log.d(TAG, "启动广告判定=热启动已武装 防抖毫秒=$HOT_START_DEBOUNCE_MS")
        }.also { mainHandler.postDelayed(it, HOT_START_DEBOUNCE_MS) }
    }

    private fun cancelPendingArm() {
        pendingArmRunnable?.let(mainHandler::removeCallbacks)
        pendingArmRunnable = null
    }

    /** 回前台重置：结束启动链以外的页面（白名单：MainActivity、SplashActivity）。 */
    private fun finishNonWhitelisted() {
        contentActivities.toList().forEach { activity ->
            if (activity.isFinishing || activity.isDestroyed || activity.isChangingConfigurations) {
                return@forEach
            }
            if (activity is MainActivity || activity is SplashActivity) {
                return@forEach
            }
            runCatching { activity.finish() }
                .onSuccess { Log.d(TAG, "回前台重置：结束页面=" + activity.javaClass.simpleName) }
                .onFailure { error ->
                    Log.w(TAG, "回前台重置：结束页面失败 " + activity.javaClass.simpleName, error)
                }
        }
    }

    /** 回前台清栈重置：CLEAR_TASK 打开启动页，广告结束落首页。 */
    private fun launchReturnToGuide() {
        // 屏幕未点亮不重开屏
        val power = app.getSystemService(PowerManager::class.java)
        if (power != null && !power.isInteractive) {
            Log.d(TAG, "热启动跳过 原因=屏幕未点亮")
            return
        }
        val current = topActivity?.get()
            ?.takeIf { !it.isFinishing && !it.isDestroyed }
        if (current is SplashActivity) {
            Log.d(TAG, "热启动跳过 原因=当前已是启动页")
            return
        }
        runCatching {
            app.startActivity(SplashActivity.createReturnToMainIntent(app))
            Log.d(TAG, "回前台重置：CLEAR_TASK 打开启动页")
        }.onFailure { error ->
            Log.w(TAG, "回前台重置启动失败，回退普通热启动", error)
            launchPlainHotStart()
        }
    }

    /** 普通热启动（回退路径）：仅 SINGLE_TOP 压栈，广告结束后回原页面。 */
    private fun launchPlainHotStart() {
        val current = topActivity?.get()
            ?.takeIf { !it.isFinishing && !it.isDestroyed }
        if (current is SplashActivity) return
        val intent = SplashActivity.createHotStartIntent(app)
        runCatching {
            if (current != null) current.startActivity(intent)
            else app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Log.w(TAG, "热启动启动页打开失败", it) }
    }

    /**
     * 旧启动页已在离场时，延迟清栈拉起新的启动页。
     *
     * 不能立即 startActivity：旧流程可能刚把 MainActivity 入栈，两个启动顺序竞争时
     * 新 SplashActivity 会被 MainActivity 盖住，导致广告流程没展示就回首页。
     */
    fun relaunchGuideForHotStart(source: Intent) {
        val intent = Intent(source).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        pendingHotStartRelaunch?.let(mainHandler::removeCallbacks)
        val runnable = Runnable {
            pendingHotStartRelaunch = null
            runCatching { app.startActivity(intent) }
                .onFailure { Log.w(TAG, "hot start relaunch failed", it) }
        }
        pendingHotStartRelaunch = runnable
        mainHandler.postDelayed(runnable, HOT_START_RELAUNCH_DELAY_MS)
    }

    /** 关闭当前全屏广告 Activity；热启动需要先收起正在展示的开屏。 */
    fun dismissCurrentAdActivity(): Boolean {
        val activity = adActivity?.get() ?: return false
        if (activity.isFinishing || activity.isDestroyed) return false
        return runCatching {
            activity.finish()
            true
        }.getOrDefault(false)
    }

    /**
     * 告诉「热启动判定」下一次退后台不是用户真的离开（例如去系统设置页开通知权限），
     * 避免用户从设置返回时被当成回前台而重走开屏。
     */
    fun ignoreNextBackgroundReturn(reason: String) {
        ignoreNextBackgroundReturn = true
        Log.d(TAG, "启动广告判定=忽略本次退后台 原因=" + reason)
    }

    // --- Activity 跟踪（adActivity + contentActivities） ---

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = update(activity)

    override fun onActivityStarted(activity: Activity) = update(activity)

    override fun onActivityResumed(activity: Activity) = update(activity)

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        contentActivities.remove(activity)
        if (topActivity?.get() === activity) topActivity = null
        if (adActivity?.get() === activity) adActivity = null
    }

    private fun update(activity: Activity) {
        if (isAdActivity(activity)) {
            adActivity = WeakReference(activity)
        } else {
            topActivity = WeakReference(activity)
            contentActivities.add(activity)
        }
    }

    private fun isAdActivity(activity: Activity): Boolean {
        val name = activity.javaClass.name
        return name == "com.google.android.gms.ads.AdActivity" ||
            name == "com.hdvideoplay.media.ads.AdActivity"
    }
}
