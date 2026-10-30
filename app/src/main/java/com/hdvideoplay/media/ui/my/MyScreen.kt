package com.hdvideoplay.media.ui.my

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hdvideoplay.media.R
import com.hdvideoplay.media.data.local.WatchHistoryRepository
import com.hdvideoplay.media.data.local.WatchProgress
import com.hdvideoplay.media.ui.components.PosterImage
import com.hdvideoplay.media.ui.nav.NavIcons
import com.hdvideoplay.media.ui.player.PlayerActivity
import com.hdvideoplay.media.ui.theme.VideoPlayDimens
import com.hdvideoplay.media.ui.theme.VideoPlayTokens

/**
 * Lanhu My — Continue Watching (gold progress), Favorites / My List,
 * Other card: History only.
 */
@Composable
fun MyScreen(
    onOpenHistory: () -> Unit = {},
    onOpenFavorites: () -> Unit = {},
) {
    val context = LocalContext.current
    val repo = WatchHistoryRepository.get(context)
    val continueWatching by repo.observeContinueWatching(20)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    fun openPlayer(item: WatchProgress) {
        if (item.mediaId <= 0) return
        context.startActivity(
            PlayerActivity.createIntent(
                context = context,
                mediaId = item.mediaId,
                episode = item.episode ?: 0,
                title = item.title,
                startFullscreen = false,
                lineIndex = 0,
            ),
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(VideoPlayTokens.PageBg),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "topbar") {
            MyTopBar()
        }
        item(key = "continue") {
            PosterRowSection(
                title = stringResource(R.string.my_continue_watching),
                emptyHint = stringResource(R.string.my_row_empty),
                items = continueWatching,
                showProgress = true,
                onOpen = ::openPlayer,
                onTitleClick = null,
                topPadding = 24,
            )
        }
        item(key = "favorites") {
            PosterRowSection(
                title = stringResource(R.string.my_favorites_list),
                emptyHint = stringResource(R.string.my_row_empty),
                items = emptyList(),
                showProgress = false,
                onOpen = {},
                onTitleClick = onOpenFavorites,
            )
        }
        item(key = "other") {
            OtherMenuCard(onHistory = onOpenHistory)
        }
    }
}

@Composable
private fun MyTopBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.nav_my),
            color = VideoPlayTokens.PrimaryText,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SectionTitle(title: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(VideoPlayDimens.SectionBarWidth.dp)
                .height(VideoPlayDimens.SectionBarHeight.dp)
                .background(VideoPlayTokens.AccentGold, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            color = VideoPlayTokens.PrimaryText,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PosterRowSection(
    title: String,
    emptyHint: String,
    items: List<WatchProgress>,
    showProgress: Boolean,
    onOpen: (WatchProgress) -> Unit,
    onTitleClick: (() -> Unit)?,
    topPadding: Int = 12,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = topPadding.dp, bottom = 4.dp),
    ) {
        SectionTitle(title = title, onClick = onTitleClick)
        LazyRow(
            contentPadding = PaddingValues(
                start = VideoPlayDimens.GridInset.dp,
                top = 12.dp,
                end = VideoPlayDimens.GridInset.dp,
                bottom = 0.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(VideoPlayDimens.GridGap.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (items.isEmpty()) {
                item {
                    Text(
                        text = emptyHint,
                        color = VideoPlayTokens.Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                items(items, key = { it.mediaId }) { item ->
                    ContinuePoster(
                        item = item,
                        showProgress = showProgress,
                        onClick = { onOpen(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinuePoster(
    item: WatchProgress,
    showProgress: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(VideoPlayDimens.PosterRadius.dp)
    val progress = if (item.durationSec > 0) {
        (item.progressSec.toFloat() / item.durationSec.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(
        modifier = Modifier
            .width(VideoPlayDimens.PosterWidth.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(VideoPlayDimens.PosterHeight.dp)
                .clip(shape),
        ) {
            PosterImage(
                posterPath = item.poster.takeIf { it.isNotBlank() },
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
            )
            if (showProgress && progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp),
                    color = VideoPlayTokens.AccentGold,
                    trackColor = VideoPlayTokens.ProgressTrack,
                )
            }
        }
        Text(
            text = item.title,
            color = VideoPlayTokens.PosterTitle,
            fontSize = 14.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
}

@Composable
private fun OtherMenuCard(
    onHistory: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
    ) {
        SectionTitle(title = stringResource(R.string.my_other))
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x99121826))
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            MyMenuRow(
                icon = NavIcons.History,
                iconTint = Color(0xFF3B82F6),
                label = stringResource(R.string.my_history),
                onClick = onHistory,
            )
        }
    }
}

@Composable
private fun MyMenuRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = VideoPlayTokens.PosterTitle,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = VideoPlayTokens.Muted,
            modifier = Modifier.size(16.dp),
        )
    }
}
