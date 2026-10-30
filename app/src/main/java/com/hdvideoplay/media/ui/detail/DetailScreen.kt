package com.hdvideoplay.media.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hdvideoplay.media.R
import com.hdvideoplay.media.data.dto.MediaItemDto
import com.hdvideoplay.media.ui.components.ErrorBox
import com.hdvideoplay.media.ui.components.PosterImage
import com.hdvideoplay.media.ui.components.UiState
import com.hdvideoplay.media.ui.player.PlayerControlsHost
import com.hdvideoplay.media.ui.player.PlayerControlsState

@Composable
fun DetailScreen(
    mediaId: Int,
    initialEpisode: Int = 0,
    autoPlay: Boolean = false,
    onBack: () -> Unit = {},
    onPlay: (playMediaId: Int, episode: Int, title: String, fullscreen: Boolean, lineIndex: Int) -> Unit,
    onOpenRecommend: (MediaItemDto) -> Unit,
    viewModel: DetailViewModel = viewModel(
        key = "detail-$mediaId-$initialEpisode",
        factory = DetailViewModel.factory(mediaId, initialEpisode),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state
    if (current is UiState.Error) {
        DetailErrorState(
            mediaId = mediaId,
            rawMessage = current.message,
            onBack = onBack,
            onRetry = viewModel::refresh,
        )
    } else {
        // Loading and Success share this call site, so the same composition
        // crossfades its skeletons into the real content instead of being
        // replaced by a different full-screen layout.
        DetailContent(
            mediaId = mediaId,
            model = (current as? UiState.Success)?.data,
            loading = current is UiState.Loading,
            autoPlay = autoPlay,
            onBack = onBack,
            onSelectSeason = viewModel::selectSeason,
            onSelectLine = viewModel::selectLine,
            onPlay = onPlay,
            onOpenRecommend = onOpenRecommend,
        )
    }
}

@Composable
private fun DetailErrorState(
    mediaId: Int,
    rawMessage: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    // invalid_media only when Intent mediaId missing / true invalid — never probe.
    val message = when {
        mediaId <= 0 ||
            rawMessage == "invalid_media" ||
            rawMessage == "Invalid media id" ->
            stringResource(R.string.invalid_media)
        rawMessage == "empty_links" ||
            rawMessage == "no_alive_links" ||
            rawMessage == "no_available_sources" ->
            stringResource(R.string.no_available_lines)
        else -> rawMessage
    }
    Box(Modifier.fillMaxSize()) {
        ErrorBox(
            message = message,
            onRetry = onRetry,
            modifier = Modifier.fillMaxSize(),
        )
        // Error is terminal after DetailViewModel retries; keep back accessible
        // instead of hiding this behind the old loading mask.
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
}

@Composable
private fun DetailContent(
    mediaId: Int,
    model: DetailUiModel?,
    loading: Boolean,
    autoPlay: Boolean,
    onBack: () -> Unit,
    onSelectSeason: (Int) -> Unit,
    onSelectLine: (Int) -> Unit,
    onPlay: (playMediaId: Int, episode: Int, title: String, fullscreen: Boolean, lineIndex: Int) -> Unit,
    onOpenRecommend: (MediaItemDto) -> Unit,
) {
    val details = model?.detail?.details
    val title = details?.title.orEmpty().ifBlank { "Untitled" }
    val isMovie = model?.isMovie ?: true
    val lines = model?.lines.orEmpty()
    val selectedLineIndex = model?.selectedLineIndex ?: 0
    val linesStatus = model?.linesStatus ?: LinesStatus.Checking
    val seasons = model?.seasons.orEmpty()
    val recommends = model?.detail?.recommends.orEmpty()
    val playEpisodeNumber = model?.playEpisode ?: 0

    var shellPlaying by remember { mutableStateOf(false) }
    var shellCc by remember { mutableStateOf(false) }
    // UI-only position stub for embedded shell (real Exo is in PlayerActivity).
    var shellPos by remember { mutableStateOf(0L) }
    val shellDur = DETAIL_SHELL_DURATION_MS
    var autoPlayFired by remember(mediaId) { mutableStateOf(false) }
    var selectedEpisode by remember(mediaId, playEpisodeNumber) {
        mutableStateOf(playEpisodeNumber)
    }

    fun playEpisode(ep: Int, fullscreen: Boolean, lineIndex: Int = selectedLineIndex) {
        if (loading || mediaId <= 0) return
        shellPlaying = true
        selectedEpisode = ep
        onPlay(mediaId, ep, title, fullscreen, lineIndex)
    }

    // Play-auto: when EXTRA_AUTO_PLAY, jump to Player as soon as lines Ready — no red-button wait.
    LaunchedEffect(autoPlay, loading, linesStatus, selectedLineIndex, mediaId) {
        if (loading || !autoPlay || autoPlayFired || mediaId <= 0) return@LaunchedEffect
        if (linesStatus != LinesStatus.Ready || lines.isEmpty()) return@LaunchedEffect
        autoPlayFired = true
        playEpisode(playEpisodeNumber, fullscreen = false, lineIndex = selectedLineIndex)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07090E)),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // V8-1: Top player area — fixed height max(screenH/3, 16:9); inner 16:9 letterbox
        item {
            val containerH = rememberPortraitVideoHeight()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(containerH)
                    .background(Color.Black),
            ) {
                // Inner 16:9 content surface centered; black letterbox fills remaining height
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                ) {
                    // Poster stands in for the Exo surface until Player opens.
                    // A 16:9 placeholder is shown while detail data is loading.
                    PosterImage(
                        posterPath = details?.posterPath,
                        contentDescription = title,
                        placeholderRes = R.drawable.ic_video_placeholder,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0x99000000)),
                    )
                }
                // Controls host fills the full container (not only the 16:9 content box).
                // All playback actions are guarded while the skeleton is showing.
                PlayerControlsHost(
                    landscape = false,
                    state = PlayerControlsState(
                        title = title,
                        isPlaying = shellPlaying,
                        positionMs = shellPos,
                        durationMs = shellDur,
                        subtitleOn = shellCc,
                        autoLabel = stringResource(R.string.auto_quality),
                    ),
                    onBack = onBack,
                    onTogglePlay = {
                        if (!loading) {
                            if (!shellPlaying) {
                                val ep = if (isMovie) {
                                    0
                                } else {
                                    model?.selectedSeason?.episodes?.firstOrNull()?.episodeNumber ?: 1
                                }
                                playEpisode(ep, fullscreen = false)
                            } else {
                                shellPlaying = false
                            }
                        }
                    },
                    onSeekBack15 = {
                        if (!loading) shellPos = (shellPos - 15_000L).coerceAtLeast(0L)
                    },
                    onSeekForward15 = {
                        if (!loading) shellPos = (shellPos + 15_000L).coerceAtMost(shellDur)
                    },
                    onSeek = {
                        if (!loading) shellPos = it.coerceIn(0L, shellDur)
                    },
                    onToggleCc = {
                        if (!loading) shellCc = !shellCc
                    },
                    onAuto = { /* stub: quality sheet later */ },
                    onFullscreen = {
                        if (!loading) {
                            val ep = if (isMovie) {
                                0
                            } else {
                                model?.selectedSeason?.episodes?.firstOrNull()?.episodeNumber ?: 1
                            }
                            playEpisode(ep, fullscreen = true)
                        }
                    },
                )
            }
        }

        item {
            val lineLabel = lines.getOrNull(selectedLineIndex)?.name
                ?.ifBlank { null }
                ?: lines.getOrNull(selectedLineIndex)?.let {
                    "Line ${selectedLineIndex + 1}"
                }
            val qualityBadgeRaw = lines
                .getOrNull(selectedLineIndex)
                ?.list
                ?.firstOrNull()
                ?.quality
            WatchMetaSection(
                details = details,
                titleFallback = title,
                currentLineLabel = lineLabel,
                lines = lines,
                selectedLineIndex = selectedLineIndex,
                linesStatus = linesStatus,
                lineProbeOk = model?.lineProbeOk.orEmpty(),
                seasons = seasons,
                selectedSeasonIndex = model?.selectedSeasonIndex ?: 0,
                selectedEpisode = selectedEpisode,
                recommends = recommends,
                onSelectLine = { index ->
                    if (!loading) {
                        onSelectLine(index)
                        playEpisode(selectedEpisode, fullscreen = false, lineIndex = index)
                    }
                },
                onSelectSeason = { index ->
                    if (!loading) onSelectSeason(index)
                },
                onSelectEpisode = { playId, ep, t ->
                    if (!loading) {
                        selectedEpisode = ep
                        onPlay(playId, ep, t, false, selectedLineIndex)
                    }
                },
                onOpenRecommend = { item ->
                    if (!loading) onOpenRecommend(item)
                },
                fallbackMediaId = mediaId,
                qualityBadgeRaw = qualityBadgeRaw,
                loading = loading,
            )
        }
    }
}

private const val DETAIL_SHELL_DURATION_MS = 108L * 60_000L + 37_000L
