package com.hdvideoplay.media.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared portrait player container height for Detail and Player.
 *
 * V8-1: max(screenHeight / 3, 16:9 video height). Keeping one helper means the
 * loading skeleton, Detail shell and Player portrait chrome cannot drift apart.
 */
@Composable
fun rememberPortraitVideoHeight(): Dp {
    val config = LocalConfiguration.current
    return maxOf(
        config.screenHeightDp.dp / 3,
        config.screenWidthDp.dp * 9f / 16f,
    )
}
