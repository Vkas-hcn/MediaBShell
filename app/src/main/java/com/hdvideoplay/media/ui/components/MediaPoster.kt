package com.hdvideoplay.media.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hdvideoplay.media.R
import com.hdvideoplay.media.data.dto.MediaItemDto
import com.hdvideoplay.media.track.TrackEvents
import com.hdvideoplay.media.ui.theme.ChipBg
import com.hdvideoplay.media.ui.theme.MutedText
import com.hdvideoplay.media.ui.theme.PosterTitle
import com.hdvideoplay.media.ui.theme.PrimaryText
import com.hdvideoplay.media.ui.theme.VideoPlayDimens
import com.hdvideoplay.media.ui.theme.VideoPlayFontFamily
import com.hdvideoplay.media.util.PosterUtils

/** Lanhu: ~101dp so 3 posters fit (inset 16, gap 13). */
val DefaultPosterWidthDp: Int = VideoPlayDimens.PosterWidth

@Composable
fun MediaPosterCard(
    item: MediaItemDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    widthDp: Int = DefaultPosterWidthDp,
    /** When true, fill parent width (for grids) instead of fixed [widthDp]. */
    fillWidth: Boolean = false,
    /** Show title under poster (home rows) or overlay on poster bottom (grids). */
    titleOverlay: Boolean = false,
    maxTitleLines: Int = 1,
    /** Optional gray year under title (Channel / Search grid). */
    showYear: Boolean = false,
    /** Optional rank badge (1-based); shown only when <= maxRankBadge. */
    rank: Int? = null,
    maxRankBadge: Int = 1,
) {
    val shape = RoundedCornerShape(VideoPlayDimens.PosterRadius.dp)
    val clickMod = modifier.clickable(onClick = onClick)
    val widthMod = if (fillWidth) clickMod.fillMaxWidth() else clickMod.width(widthDp.dp)

    Column(modifier = widthMod) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(VideoPlayDimens.PosterWidth.toFloat() / VideoPlayDimens.PosterHeight.toFloat())
                .clip(shape),
        ) {
            PosterImage(
                posterPath = item.posterPath,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                trackMediaId = item.localId(),
            )
            val showRank = rank != null && rank >= 1 && rank <= maxRankBadge
            if (showRank) {
                Text(
                    text = "%02d".format(rank),
                    color = PrimaryText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic,
                    fontFamily = VideoPlayFontFamily,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                )
            }
            if (titleOverlay) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xCC000000)),
                            ),
                        )
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = item.title,
                        color = PosterTitle,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = VideoPlayFontFamily,
                        maxLines = maxTitleLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (!titleOverlay) {
            val titleLines = if (showYear) 1 else maxTitleLines.coerceAtLeast(1)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text(
                    text = item.title,
                    color = PosterTitle,
                    fontSize = 14.sp,
                    lineHeight = 16.sp,
                    fontFamily = VideoPlayFontFamily,
                    maxLines = titleLines,
                    minLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showYear) {
                    val year = item.releaseDate?.take(4).orEmpty()
                    if (year.isNotBlank()) {
                        Text(
                            text = year,
                            color = MutedText,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            fontFamily = VideoPlayFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PosterImage(
    posterPath: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholderRes: Int = R.drawable.ic_poster_placeholder,
    /** 海报所属媒体 ID：传值即开启 image_load_fail 上报（仅海报，不覆盖所有图片）。 */
    trackMediaId: Int? = null,
) {
    val url = PosterUtils.resolvePosterUrl(posterPath)
    val context = LocalContext.current
    // image_load_fail 去重：同一组合内同一 URL 只报一次，避免组件重复回调重复上报。
    var loadFailReported by remember(url) { mutableStateOf(false) }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(SkeletonMotion.ImageFadeMs)
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = painterResource(placeholderRes),
                placeholder = painterResource(placeholderRes),
                onError = {
                    val mediaId = trackMediaId
                    if (mediaId != null && mediaId > 0 && !loadFailReported) {
                        loadFailReported = true
                        val host = runCatching { android.net.Uri.parse(url).host }
                            .getOrNull()
                            .orEmpty()
                        TrackEvents.imageLoadFail(host, mediaId)
                    }
                },
            )
        } else {
            Image(
                painter = painterResource(placeholderRes),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Circular avatar with a stable letter placeholder underneath the image.
 * The image fades in over the letter, so a slow/failed load never flashes or
 * changes the avatar's measured size.
 */
@Composable
fun AvatarImage(
    url: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    initialFontSize: TextUnit = if (size >= 48.dp) 18.sp else 14.sp,
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(ChipBg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().take(1).uppercase(),
            color = Color.White,
            fontSize = initialFontSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        val resolved = url?.takeIf { it.isNotBlank() }
        if (resolved != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(resolved)
                    .crossfade(SkeletonMotion.ImageFadeMs)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
