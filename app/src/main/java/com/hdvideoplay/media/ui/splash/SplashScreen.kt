package com.hdvideoplay.media.ui.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/**
 * Cold-start splash aligned to HyperVideo GuideActivity splash
 * (activity_guide_splash.xml):
 * - full-screen background #080808
 * - logo ic_login 120x120, centered and raised 50dp
 *   (center gravity + 100dp bottom margin)
 * - horizontal progress bar 6dp pinned to the bottom: 24dp side margins,
 *   100dp above the bottom edge, track #2A2A2A, fill #E21220,
 *   animated 0 -> 100% over the hold time.
 */
@Composable
fun SplashLoadingScreen(
    onFinished: () -> Unit,
    holdMs: Long = 1400L,
) {
    var target by remember { mutableFloatStateOf(0f) }
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(
            durationMillis = holdMs.toInt().coerceAtLeast(400),
            easing = LinearEasing,
        ),
        label = "splashProgress",
    )

    LaunchedEffect(Unit) {
        target = 1f
        delay(holdMs)
        onFinished()
    }

    SplashBackdrop(progress = progress)
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
