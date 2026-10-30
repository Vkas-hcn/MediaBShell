package com.hdvideoplay.media.ui.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdvideoplay.media.R
import com.hdvideoplay.media.ui.theme.BrandGold
import com.hdvideoplay.media.ui.theme.PageBg
import com.hdvideoplay.media.ui.theme.PrimaryText
import com.hdvideoplay.media.ui.theme.SplashProgressTrack
import com.hdvideoplay.media.ui.theme.VideoPlayDimens
import com.hdvideoplay.media.ui.theme.VideoPlayFontFamily
import kotlinx.coroutines.delay

/**
 * Cold-start Loading aligned to Lanhu splash artboard (360x800):
 * - background at 0,0 360x800
 * - logo 140x130 at (110,172), wordmark 24sp/28 at (120,314)
 * - progress track 260x6 at (50,743), pill radius, #FFB800 / #7C8CAE 32%.
 */
@Composable
fun SplashLoadingScreen(
    onFinished: () -> Unit,
    holdMs: Long = 1400L,
) {
    var target by remember { mutableFloatStateOf(0.08f) }
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
            .background(PageBg),
    ) {
        // Lanhu 1440x3200 (4x 360x800) full-bleed film-strip / glow art.
        Image(
            painter = painterResource(R.drawable.img_splash_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 172.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.img_video_play_logo),
                contentDescription = null,
                modifier = Modifier.size(
                    width = VideoPlayDimens.SplashLogoWidth.dp,
                    height = VideoPlayDimens.SplashLogoHeight.dp,
                ),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.brand_wordmark),
                color = PrimaryText,
                fontSize = 24.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = VideoPlayFontFamily,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 50.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = BrandGold,
                trackColor = SplashProgressTrack,
            )
        }
    }
}
