package com.hdvideoplay.media.ui.splash

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.hdvideoplay.media.R
import com.hdvideoplay.media.ui.theme.SplashBg
import com.hdvideoplay.media.ui.theme.SplashProgressRed
import com.hdvideoplay.media.ui.theme.SplashProgressTrack
import com.hdvideoplay.media.ui.theme.VideoPlayDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/**
 * Cold-start splash aligned to HyperVideo GuideActivity splash
 * (activity_guide_splash.xml):
 * - full-screen background #080808
 * - logo ic_login 120x120, centered and raised 50dp
 *   (center gravity + 100dp bottom margin)
 * - horizontal progress bar 6dp pinned to the bottom: 24dp side margins,
 *   100dp above the bottom edge, track #2A2A2A, fill #E21220.
 *
 * 进度条与广告等待窗口同步：总时长 = [holdMs] + 广告等待窗口（COLD_AD_WAIT_MS /
 * HOT_AD_WAIT_MS，由 [targetMs] 体现）。广告就绪或退出时会提前收拢 [targetMs]，
 * 进度条自动重定向，保证广告展示出来 / 超时跳转首页的那一刻进度条恰好走到尽头。
 */
@Composable
fun SplashLoadingScreen(
    startedAtElapsed: Long,
    targetMs: StateFlow<Long>,
    holdMs: Long,
    /** 最短停留结束（进度条越过 hold 阶段），启动广告流程。 */
    onHoldElapsed: () -> Unit,
    /** 组合完成、Loading 即将真正展示时回调一次（loading_show 埋点）。 */
    onShown: () -> Unit = {},
) {
    val progress = remember { Animatable(0f) }
    val target by targetMs.collectAsState()

    // 目标时刻（可重定向）：按当前进度快照校准后，线性走到 100%。
    LaunchedEffect(target) {
        val now = SystemClock.elapsedRealtime()
        val total = (target - startedAtElapsed).coerceAtLeast(1L)
        val elapsed = (now - startedAtElapsed).coerceAtLeast(0L)
        val current = (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        val remaining = (target - now).coerceAtLeast(0L)
        progress.snapTo(current)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = remaining.toInt(),
                easing = LinearEasing,
            ),
        )
    }

    LaunchedEffect(Unit) {
        onShown()
        delay(holdMs)
        onHoldElapsed()
    }

    SplashBackdrop(progress = progress.value)
}

/**
 * Static splash artwork, reused while the cold App Open creative is still loading.
 * Keeping this visible avoids dropping from the design to a blank dark frame.
 */
@Composable
fun SplashBackdrop(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashBg),
    ) {
        // HyperVideo: logo centered with a 100dp bottom margin
        // (i.e. raised 50dp above the true center).
        Image(
            painter = painterResource(R.drawable.ic_login),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-50).dp)
                .size(
                    width = VideoPlayDimens.SplashLogoWidth.dp,
                    height = VideoPlayDimens.SplashLogoHeight.dp,
                ),
            contentScale = ContentScale.Fit,
        )

        // HyperVideo: 6dp horizontal bar, 24dp side margins,
        // 100dp above the bottom edge.
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 100.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = SplashProgressRed,
            trackColor = SplashProgressTrack,
        )
    }
}
