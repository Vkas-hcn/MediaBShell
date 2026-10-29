package com.hdvideoplay.media.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdvideoplay.media.R
import com.hdvideoplay.media.data.local.WatchProgress
import com.hdvideoplay.media.ui.theme.VideoPlayFontFamily
import com.hdvideoplay.media.ui.theme.VideoPlayTokens
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Non-player quick-resume card (Lanhu 首页 node 1454:13347).
 * Only rendered when the most recent progress is 1%..99% and not dismissed.
 */
@Composable
fun QuickReturnOverlay(
    progress: WatchProgress?,
    dismissedKey: String?,
    onOpen: (WatchProgress) -> Unit,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = progress ?: return
    resumePercentOrNull(item) ?: return
    val key = dismissKey(item)
    if (key == dismissedKey) return

    val shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
    Row(
        modifier = modifier
            .width(116.dp)
            .height(46.dp)
            .clip(shape)
            .background(Color(0xE612151C))
            .border(1.dp, Color(0x1FFFFFFF), shape)
            .clickable { onOpen(item) }
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = VideoPlayTokens.PrimaryText,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = VideoPlayFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.resume_progress,
                    resumeProgressText(item).orEmpty(),
                ),
                color = VideoPlayTokens.Muted,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontFamily = VideoPlayFontFamily,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clickable { onDismiss(key) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.quick_resume_dismiss),
                tint = VideoPlayTokens.Muted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

internal fun dismissKey(progress: WatchProgress): String =
    "${progress.mediaId}:${progress.season ?: -1}:${progress.episode ?: -1}:${progress.progressSec}"

/** 1%..99% inclusive; 0/100 and unknown duration hide. */
internal fun resumePercentOrNull(progress: WatchProgress): Double? {
    if (progress.durationSec <= 0L || progress.progressSec <= 0L) return null
    val percent = progress.progressSec.toDouble() * 100.0 / progress.durationSec.toDouble()
    return percent.takeIf { it in 1.0..99.0 }
}

/** Design uses mm:ss; long videos use a compact percent so the 116dp card never clips. */
internal fun resumeProgressText(progress: WatchProgress): String? {
    val percent = resumePercentOrNull(progress) ?: return null
    val time = formatResumeTime(progress.progressSec)
    return if (time.length <= 5) time else "${percent.roundToInt()}%"
}

internal fun formatResumeTime(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val h = safe / 3600
    val m = (safe % 3600) / 60
    val s = safe % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%02d:%02d", m, s)
    }
}
