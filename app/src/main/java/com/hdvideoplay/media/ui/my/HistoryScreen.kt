package com.hdvideoplay.media.ui.my

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hdvideoplay.media.R
import com.hdvideoplay.media.data.local.WatchHistoryRepository
import com.hdvideoplay.media.data.local.WatchProgress
import com.hdvideoplay.media.track.TrackEvents
import com.hdvideoplay.media.ui.components.PosterImage
import com.hdvideoplay.media.ui.player.PlayerActivity
import com.hdvideoplay.media.ui.theme.VideoPlayDimens
import com.hdvideoplay.media.ui.theme.VideoPlayTokens
import kotlinx.coroutines.launch

/** Lanhu History — 3-col poster grid with thin gold progress. */
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repo = WatchHistoryRepository.get(context)
    val history by repo.observeHistory()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    fun openPlayer(item: WatchProgress) {
        if (item.mediaId <= 0) return
        context.startActivity(
            PlayerActivity.createIntent(
                context = context,
                mediaId = item.mediaId,
                episode = item.episode ?: 0,
                title = item.title,
                fromPosition = TrackEvents.FROM_HISTORY,
            ),
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(VideoPlayTokens.PageBg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "‹",
                color = VideoPlayTokens.PrimaryText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Text(
                text = stringResource(R.string.my_history_title),
                color = VideoPlayTokens.PrimaryText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { scope.launch { repo.clear() } }) {
                Text(
                    text = stringResource(R.string.my_history_clear),
                    color = VideoPlayTokens.Muted,
                    fontSize = 13.sp,
                )
            }
        }

        if (history.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.my_history_empty),
                    color = VideoPlayTokens.Muted,
                    fontSize = 13.sp,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(
                    horizontal = VideoPlayDimens.GridInset.dp,
                    vertical = 8.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(VideoPlayDimens.GridGap.dp),
                verticalArrangement = Arrangement.spacedBy(VideoPlayDimens.GridGap.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(history, key = { it.mediaId }) { item ->
                    HistoryPoster(item = item, onClick = { openPlayer(item) })
                }
            }
        }
    }
}

@Composable
private fun HistoryPoster(
    item: WatchProgress,
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
            .fillMaxWidth()
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
                trackMediaId = item.mediaId,
            )
            if (progress > 0f) {
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
                .padding(top = 6.dp),
        )
    }
}
