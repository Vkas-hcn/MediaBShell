package com.hdvideoplay.media.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdvideoplay.media.R
import com.hdvideoplay.media.ui.theme.PageBg
import kotlinx.coroutines.delay

/**
 * Fullscreen ad chrome shell (UI-only). Real App Open uses GMA fullscreen via [com.hdvideoplay.media.ads.AdScheduler].
 * Kept for native/fullscreen placeholder styling; cold start no longer auto-routes through fake CTA.
 * Acceptance = show / waterfall / dismiss only — NEVER click ad CTAs in tests.
 */
@Composable
fun SplashAdShell(
    onSkip: () -> Unit,
    onCta: () -> Unit = onSkip,
    autoDismissMs: Long = 3500L,
) {
    LaunchedEffect(Unit) {
        delay(autoDismissMs)
        onSkip()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg),
    ) {
        // Ad creative placeholder area (dark gradient — no SDK).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 96.dp)
                .background(Color(0xFF0D1B2A)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.ad_shell_placeholder),
                color = Color(0xFF8ECAE6),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
        }

        // Top bar: Ad badge + Continue using app
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            // 「广告」badge — reserved hot zone, distinct from player chips
            Text(
                text = stringResource(R.string.ad_badge),
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0x99000000))
                    .clickable { /* reserved: ad info */ }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Text(
                text = stringResource(R.string.ad_continue_app),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x66000000))
                    .clickable(onClick = onSkip)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        // Bottom large CTA — not player-red; reserved install/open zone
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xFF0A0A0A))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFF3DDCFF))
                    .clickable(onClick = onCta),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.ad_cta_install),
                    color = Color.Black,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.ad_skip_hint),
                color = Color(0xFF808080),
                fontSize = 11.sp,
                modifier = Modifier.clickable(onClick = onSkip),
            )
        }
    }
}

/** Cold-start phase for MainActivity. */
enum class ColdStartPhase {
    Loading,
    AdShell,
    Ready,
}
