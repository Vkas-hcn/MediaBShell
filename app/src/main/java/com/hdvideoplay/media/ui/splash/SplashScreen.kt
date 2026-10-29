package com.hdvideoplay.media.ui.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * Cold-start Loading: film-strip atmosphere + centered logo + gold progress.
 * Theme windowBackground is already #07090E so no white flash.
 */
@Composable
fun SplashLoadingScreen(
    onFinished: () -> Unit,
    holdMs: Long = 1400L,
) {
    var target by remember { mutableFloatStateOf(0.08f) }
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = holdMs.toInt().coerceAtLeast(400), easing = LinearEasing),
        label = "splashProgress",
    )

    LaunchedEffect(Unit) {
        target = 1f
        delay(holdMs)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg),
    ) {
        FilmStripBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.img_video_play_logo),
                contentDescription = stringResource(R.string.brand_wordmark),
                modifier = Modifier.size(
                    width = VideoPlayDimens.SplashLogoWidth.dp,
                    height = VideoPlayDimens.SplashLogoHeight.dp,
                ),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.brand_wordmark),
                color = PrimaryText,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = VideoPlayFontFamily,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = BrandGold,
                trackColor = SplashProgressTrack,
            )
        }
    }
}

/** Abstract glowing film-strip curves — blue/cyan with warm edge accents. */
@Composable
private fun FilmStripBackground(modifier: Modifier = Modifier) {
    val blue = Color(0xFF1E6BFF)
    val cyan = Color(0xFF3AA0FF)
    val amber = Color(0xFFFFB84A)
    Canvas(modifier = modifier.background(PageBg)) {
        val w = size.width
        val h = size.height
        // Soft radial glow behind logo area
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x332A6BFF), Color.Transparent),
                center = Offset(w * 0.5f, h * 0.38f),
                radius = w * 0.55f,
            ),
            radius = w * 0.55f,
            center = Offset(w * 0.5f, h * 0.38f),
        )
        // Winding film-strip path (bottom-left → center-right)
        val strip = Path().apply {
            moveTo(-w * 0.05f, h * 0.92f)
            cubicTo(
                w * 0.15f, h * 0.78f,
                w * 0.35f, h * 0.88f,
                w * 0.55f, h * 0.62f,
            )
            cubicTo(
                w * 0.72f, h * 0.42f,
                w * 0.88f, h * 0.55f,
                w * 1.05f, h * 0.38f,
            )
        }
        drawPath(
            path = strip,
            brush = Brush.horizontalGradient(
                colors = listOf(cyan.copy(alpha = 0.55f), blue.copy(alpha = 0.35f), amber.copy(alpha = 0.55f)),
            ),
            style = Stroke(width = 28.dp.toPx(), cap = StrokeCap.Round),
        )
        drawPath(
            path = strip,
            brush = Brush.horizontalGradient(
                colors = listOf(cyan.copy(alpha = 0.9f), amber.copy(alpha = 0.85f)),
            ),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
        // Secondary thinner curve
        val strip2 = Path().apply {
            moveTo(w * 0.05f, h * 1.02f)
            cubicTo(
                w * 0.28f, h * 0.85f,
                w * 0.48f, h * 0.95f,
                w * 0.7f, h * 0.7f,
            )
            cubicTo(
                w * 0.88f, h * 0.5f,
                w * 0.95f, h * 0.62f,
                w * 1.1f, h * 0.48f,
            )
        }
        drawPath(
            path = strip2,
            color = blue.copy(alpha = 0.25f),
            style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}
