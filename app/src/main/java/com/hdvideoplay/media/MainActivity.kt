package com.hdvideoplay.media

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import com.hdvideoplay.media.ads.AdScenes
import com.hdvideoplay.media.ads.AdScheduler
import com.hdvideoplay.media.data.notice.HintUtil
import com.hdvideoplay.media.permission.NotificationPermissionHelper
import com.hdvideoplay.media.ui.nav.AppNav
import com.hdvideoplay.media.ui.recall.RecallPopupController
import com.hdvideoplay.media.ui.theme.MediaTheme

/**
 * Business shell only. Cold/hot splash and App Open are owned by SplashActivity
 * and HotStartManager; MainActivity is always entered after the splash finishes.
 */
class MainActivity : ComponentActivity() {
    private val permissionHandler = Handler(Looper.getMainLooper())
    private val homePromptRunnable = Runnable { showHomeNotificationPrompt() }

    /** 通知权限引导（对齐 HyperVideo MainActivity.maybeShowHomeNotificationDialog）。 */
    private val permissionHost by lazy { NotificationPermissionHelper.bind(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 提前初始化，确保权限 launcher 在 Activity 进入 STARTED 前完成注册
        permissionHost
        enableEdgeToEdge()
        setContent {
            MediaTheme {
                AppNav()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 每次首页显示都重新判定一次通知权限（系统框能弹就弹系统框）
        scheduleHomeNotificationPrompt()
        // Cold/hot app-start events only; route changes do not trigger the popup.
        RecallPopupController.onAppForeground(this)
        // Preload pools for the next return; load is not show.
        AdScheduler.load(AdScenes.HOT_OPEN)
        AdScheduler.load(AdScenes.NORMAL_1)
        AdScheduler.load(AdScenes.NORMAL_2)
    }

    override fun onPause() {
        permissionHandler.removeCallbacks(homePromptRunnable)
        super.onPause()
    }

    /** 切回首页 Tab 时由 AppNav 调用（对齐「每次回到首页都重新判定」）。 */
    fun onHomeTabSelected() {
        scheduleHomeNotificationPrompt()
    }

    /**
     * 每次首页显示（进入首页 / 切回首页 Tab）都重新判定：没授权就优先弹系统框，
     * 系统框弹不出来才弹自定义框。延迟 300ms，避免和启动页收尾、广告关闭的回调抢时机。
     */
    private fun scheduleHomeNotificationPrompt() {
        permissionHandler.removeCallbacks(homePromptRunnable)
        permissionHandler.postDelayed(homePromptRunnable, HOME_NOTIFICATION_PROMPT_DELAY_MS)
    }

    private fun showHomeNotificationPrompt() {
        if (isFinishing || isDestroyed) return
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        permissionHost.showNotificationInAppPrompt { granted ->
            // 授权成功后补排通知链路
            if (granted) HintUtil.markLing()
        }
    }

    private companion object {
        const val HOME_NOTIFICATION_PROMPT_DELAY_MS = 300L
    }
}
