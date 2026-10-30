package com.hdvideoplay.media.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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

/** Design tokens for the Lanhu quick-resume card. */
private val CardShape = RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp)
private val CardBg = Color(0xF20E121E)
private val CardBorder = Color(0x66FFB800)
private val CardWidthShort = 150.dp
private val CardWidthLong = 170.dp
private val CardHeight = 60.dp
private val CloseTouchSize = 32.dp
private val CloseIconColor = Color(0xFFC7D0DF)

/**
 * Non-player quick-resume card (Lanhu 首页 node 1454:13347).
 *
 * Design: dark navy card with a subtle gold border, title + gold dot +
 * "Resume · mm:ss", and a plain X dismiss control. Always shows the
 * actual playback time (never a percentage); the card widens for h:mm:ss.
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
    val resumeText = resumeProgressText(item) ?: return
    val cardWidth = if (resumeText.length > 5) CardWidthLong else CardWidthShort

    Row(
        modifier = modifier
            .width(cardWidth)
            .height(CardHeight)
            .clip(CardShape)
            .background(CardBg)
            .border(1.dp, CardBorder, CardShape)
            .clickable { onOpen(item) }
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = item.title,
                color = VideoPlayTokens.PrimaryText,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = VideoPlayFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(5.dp)
                        .background(VideoPlayTokens.AccentGold, CircleShape),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = stringResource(R.string.resume_progress, resumeText),
                    color = VideoPlayTokens.AccentGold,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = VideoPlayFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(CloseTouchSize)
                .clickable { onDismiss(key) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.quick_resume_dismiss),
                tint = CloseIconColor,
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

/** Always show actual playback time, including h:mm:ss for long videos. */
internal fun resumeProgressText(progress: WatchProgress): String? {
    resumePercentOrNull(progress) ?: return null
    return formatResumeTime(progress.progressSec)
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
