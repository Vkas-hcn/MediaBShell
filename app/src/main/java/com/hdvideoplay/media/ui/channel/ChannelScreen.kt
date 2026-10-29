package com.hdvideoplay.media.ui.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.hdvideoplay.media.R
import com.hdvideoplay.media.data.dto.MediaItemDto
import com.hdvideoplay.media.ui.components.BrandWordmark
import com.hdvideoplay.media.ui.components.ErrorBox
import com.hdvideoplay.media.ui.components.LoadingBox
import com.hdvideoplay.media.ui.components.MediaPosterCard
import com.hdvideoplay.media.ui.components.UiState
import com.hdvideoplay.media.ui.theme.BrandGold
import com.hdvideoplay.media.ui.theme.ChipBg
import com.hdvideoplay.media.ui.theme.ChipBorder
import com.hdvideoplay.media.ui.theme.MutedText
import com.hdvideoplay.media.ui.theme.PressSurface
import com.hdvideoplay.media.ui.theme.PrimaryText
import com.hdvideoplay.media.ui.theme.VideoPlayDimens
import com.hdvideoplay.media.ui.theme.VideoPlayFontFamily

/**
 * Channel: provider logo grid (gold stroke when selected) + 3-col posters with year.
 * Not EPG.
 */
@Composable
fun ChannelScreen(
    onOpenDetail: (MediaItemDto) -> Unit,
    viewModel: ChannelViewModel = viewModel(factory = ChannelViewModel.factory()),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    when (val platforms = ui.platforms) {
        UiState.Loading -> LoadingBox()
        is UiState.Error -> ErrorBox(platforms.message, onRetry = viewModel::refresh)
        is UiState.Success -> {
            if (platforms.data.isEmpty()) {
                Column(Modifier.fillMaxSize()) {
                    BrandWordmark()
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.empty),
                            color = MutedText,
                            fontSize = 13.sp,
                            fontFamily = VideoPlayFontFamily,
                        )
                    }
                }
            } else {
                ChannelContent(
                    platforms = platforms.data,
                    selectedId = ui.selectedId,
                    moviesState = ui.movies,
                    onSelectPlatform = viewModel::selectPlatform,
                    onOpenDetail = onOpenDetail,
                    onRetryMovies = viewModel::reloadMovies,
                )
            }
        }
    }
}

@Composable
private fun ChannelContent(
    platforms: List<ChannelEntry>,
    selectedId: Int?,
    moviesState: UiState<List<MediaItemDto>>,
    onSelectPlatform: (Int) -> Unit,
    onOpenDetail: (MediaItemDto) -> Unit,
    onRetryMovies: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(12),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = VideoPlayDimens.GridInset.dp,
            vertical = 4.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(VideoPlayDimens.GridGap.dp),
        verticalArrangement = Arrangement.spacedBy(VideoPlayDimens.GridGap.dp),
    ) {
        item(span = { GridItemSpan(12) }, key = "wordmark") {
            BrandWordmark()
        }

        // Top: 4 providers per row (each spans 3 of 12) — design shows 4×2
        items(
            count = platforms.size,
            key = { "p-${platforms[it].id}" },
            span = { GridItemSpan(3) },
        ) { index ->
            val entry = platforms[index]
            ChannelIconEntry(
                entry = entry,
                selected = entry.id == selectedId,
                onClick = { onSelectPlatform(entry.id) },
            )
        }

        item(span = { GridItemSpan(12) }, key = "movies-header") {
            Box(Modifier.heightIn(min = 4.dp))
        }

        when (val movies = moviesState) {
            UiState.Loading -> {
                item(span = { GridItemSpan(12) }, key = "movies-loading") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.dp,
                            color = BrandGold,
                        )
                    }
                }
            }
            is UiState.Error -> {
                item(span = { GridItemSpan(12) }, key = "movies-error") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = movies.message,
                            color = MutedText,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            fontFamily = VideoPlayFontFamily,
                        )
                        Text(
                            text = stringResource(R.string.retry),
                            color = PrimaryText,
                            fontSize = 13.sp,
                            fontFamily = VideoPlayFontFamily,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .clickable(onClick = onRetryMovies),
                        )
                    }
                }
            }
            is UiState.Success -> {
                if (movies.data.isEmpty()) {
                    item(span = { GridItemSpan(12) }, key = "movies-empty") {
                        Text(
                            text = stringResource(R.string.channel_no_content),
                            color = MutedText,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            fontFamily = VideoPlayFontFamily,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                        )
                    }
                } else {
                    items(
                        count = movies.data.size,
                        key = { "m-${movies.data[it].localId()}-${movies.data[it].title}" },
                        span = { GridItemSpan(4) },
                    ) { index ->
                        val item = movies.data[index]
                        MediaPosterCard(
                            item = item,
                            onClick = { onOpenDetail(item) },
                            fillWidth = true,
                            maxTitleLines = 1,
                            showYear = true,
                        )
                    }
                }
            }
        }
    }
}

/** Provider tile: dark card + gold stroke when selected; placeholder letter if no icon. */
@Composable
private fun ChannelIconEntry(
    entry: ChannelEntry,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(10.dp)
    val iconBg = when {
        pressed -> PressSurface
        else -> ChipBg
    }
    val stroke = if (selected) BrandGold else ChipBorder

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.55f)
            .clip(shape)
            .background(iconBg)
            .border(1.5.dp, stroke, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        ChannelIcon(entry)
    }
}

@Composable
private fun ChannelIcon(entry: ChannelEntry) {
    if (entry.iconUrl != null) {
        AsyncImage(
            model = entry.iconUrl,
            contentDescription = entry.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
        )
    } else {
        Text(
            text = entry.name.trim().ifBlank { "#" },
            color = PrimaryText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = VideoPlayFontFamily,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
