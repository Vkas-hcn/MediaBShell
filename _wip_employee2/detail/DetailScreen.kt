package com.hdvideoplay.media.ui.detail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hdvideoplay.media.R
import com.hdvideoplay.media.data.dto.EpisodeDto
import com.hdvideoplay.media.data.dto.MediaItemDto
import com.hdvideoplay.media.data.dto.RecommendGroupDto
import com.hdvideoplay.media.data.dto.SeasonDto
import com.hdvideoplay.media.ui.components.ErrorBox
import com.hdvideoplay.media.ui.components.LoadingBox
import com.hdvideoplay.media.ui.components.MediaPosterCard
import com.hdvideoplay.media.ui.components.PosterImage
import com.hdvideoplay.media.ui.components.UiState

/**
 * @param mediaId local Media.id from Intent (used for movie play & detail fetch)
 * @param onPlay (playMediaId, episode, title) — TV seasons may use season.mediaId
 */
@Composable
fun DetailScreen(
    mediaId: Int,
    onPlay: (playMediaId: Int, episode: Int, title: String) -> Unit,
    onOpenRecommend: (MediaItemDto) -> Unit,
    viewModel: DetailViewModel = viewModel(factory = DetailViewModel.factory(mediaId)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val s = state) {
        UiState.Loading -> LoadingBox()
        is UiState.Error -> {
            ErrorBox(
                message = if (mediaId <= 0) {
                    stringResource(R.string.invalid_media)
                } else {
                    s.message
                },
                onRetry = if (mediaId <= 0) {
                    {}
                } else {
                    viewModel::refresh
                },
            )
        }
        is UiState.Success -> DetailContent(
            mediaId = mediaId,
            model = s.data,
            onSelectSeason = viewModel::selectSeason,
            onPlay = onPlay,
            onOpenRecommend = onOpenRecommend,
        )
    }
}

@Composable
private fun DetailContent(
    mediaId: Int,
    model: DetailUiModel,
    onSelectSeason: (Int) -> Unit,
    onPlay: (playMediaId: Int, episode: Int, title: String) -> Unit,
    onOpenRecommend: (MediaItemDto) -> Unit,
) {
    val details = model.detail.details
    val title = details?.title.orEmpty().ifBlank { "Untitled" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PosterImage(
                    posterPath = details?.posterPath,
                    contentDescription = title,
                    modifier = Modifier
                        .width(120.dp)
                        .height(180.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Column(Modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    val date = details?.releaseDate.orEmpty()
                    if (date.isNotBlank()) {
                        Text(
                            date,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val genres = details?.genres?.joinToString(", ") { it.name }.orEmpty()
                    if (genres.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            genres,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (model.isMovie) {
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { onPlay(mediaId, 0, title) }) {
                            Text(stringResource(R.string.play))
                        }
                    }
                }
            }
        }

        item {
            val overview = details?.overview.orEmpty()
            if (overview.isNotBlank()) {
                Text(
                    overview,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        if (!model.isMovie) {
            item {
                Text(
                    stringResource(R.string.seasons),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(8.dp))
                SeasonChips(
                    seasons = model.seasons,
                    selectedIndex = model.selectedSeasonIndex,
                    onSelect = onSelectSeason,
                )
            }
            item {
                val season = model.selectedSeason
                if (season != null) {
                    Text(
                        stringResource(R.string.episodes),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    EpisodeList(
                        season = season,
                        fallbackMediaId = mediaId,
                        title = title,
                        onPlay = onPlay,
                    )
                }
            }
        }

        items(model.detail.recommends, key = { it.k }) { group ->
            RecommendSection(group = group, onOpen = onOpenRecommend)
        }
    }
}

@Composable
private fun SeasonChips(
    seasons: List<SeasonDto>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        seasons.forEachIndexed { index, season ->
            FilterChip(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                label = {
                    Text(
                        season.name.ifBlank { "S${season.seasonNumber}" },
                    )
                },
            )
        }
    }
}

@Composable
private fun EpisodeList(
    season: SeasonDto,
    fallbackMediaId: Int,
    title: String,
    onPlay: (playMediaId: Int, episode: Int, title: String) -> Unit,
) {
    val playMediaId = season.mediaId.takeIf { it > 0 } ?: fallbackMediaId
    Column(
        Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        season.episodes.forEach { ep: EpisodeDto ->
            OutlinedButton(
                onClick = {
                    onPlay(
                        playMediaId,
                        ep.episodeNumber,
                        ep.name.ifBlank { title },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = ep.name.ifBlank {
                        stringResource(R.string.episode_n, ep.episodeNumber)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text("▶")
            }
        }
    }
}

@Composable
private fun RecommendSection(
    group: RecommendGroupDto,
    onOpen: (MediaItemDto) -> Unit,
) {
    if (group.v.isEmpty()) return
    Column {
        Text(
            text = group.k.ifBlank { "Recommended" },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(group.v, key = { it.localId() }) { item ->
                MediaPosterCard(item = item, onClick = { onOpen(item) })
            }
        }
    }
}
