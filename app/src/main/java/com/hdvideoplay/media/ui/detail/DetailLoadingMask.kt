package com.hdvideoplay.media.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.hdvideoplay.media.ui.theme.VideoPlayTokens

/**
 * Full-screen detail placeholder. Fixed layout mirrors [DetailScreen]:
 * player area on top + meta skeleton below. Only detail API success removes it.
 */
@Composable
fun DetailLoadingMask(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val config = LocalConfiguration.current
    val videoHeight = maxOf(
        config.screenHeightDp.dp / 3,
        config.screenWidthDp.dp * 9f / 16f,
    )
    val block = Color(0xFF141824)
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VideoPlayTokens.PageBg)
            // Consume taps so the error/retry UI behind the mask cannot be clicked.
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) { },
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(videoHeight)
                    .background(Color.Black),
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                SkeletonBlock(block, widthFraction = 0.55f, heightDp = 24)
                Spacer(Modifier.height(10.dp))
                SkeletonBlock(block, widthFraction = 0.9f, heightDp = 14)
                Spacer(Modifier.height(18.dp))
                SkeletonBlock(block, widthFraction = 0.55f, heightDp = 16)
                Spacer(Modifier.height(12.dp))
                SkeletonBlock(block, widthFraction = 1f, heightDp = 14)
                Spacer(Modifier.height(8.dp))
                SkeletonBlock(block, widthFraction = 1f, heightDp = 14)
                Spacer(Modifier.height(8.dp))
                SkeletonBlock(block, widthFraction = 0.8f, heightDp = 14)
                Spacer(Modifier.height(20.dp))
                SkeletonBlock(block, widthFraction = 0.35f, heightDp = 16)
                Spacer(Modifier.height(12.dp))
                SkeletonBlock(block, widthFraction = 0.2f, heightDp = 32)
            }
        }
    }
}

@Composable
private fun SkeletonBlock(
    color: Color,
    widthFraction: Float,
    heightDp: Int,
) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(heightDp.dp)
            .background(color, androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
    )
}
