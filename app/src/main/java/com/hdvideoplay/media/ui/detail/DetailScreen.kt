package com.hdvideoplay.media.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hdvideoplay.media.R
import com.hdvideoplay.media.data.dto.MediaItemDto
import com.hdvideoplay.media.ui.components.ErrorBox
import com.hdvideoplay.media.ui.components.LoadingBox
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
    var loadingMaskVisible by remember(mediaId) { mutableStateOf(true) }
    LaunchedEffect(state) {
        if (state is UiState.Success) loadingMaskVisible = false
    }
    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            UiState.Loading -> LoadingBox()
            is UiState.Error -> {
                // invalid_media only when Intent mediaId missing / true invalid — never probe.
                val msg = when {
                    mediaId <= 0 ||
                        s.message == "invalid_media" ||
                        s.message == "Invalid media id" ->
                        stringResource(R.string.invalid_media)
                    s.message == "empty_links" ||
                        s.message == "no_alive_links" ||
                        s.message == "no_available_sources" ->
                        stringResource(R.string.no_available_lines)
                    else -> s.message
                }
                ErrorBox(
                    message = msg,
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
                autoPlay = autoPlay,
                onBack = onBack,
                onSelectSeason = viewModel::selectSeason,
                onSelectLine = viewModel::selectLine,
                onPlay = onPlay,
                onOpenRecommend = onOpenRecommend,
            )
        }
        if (loadingMaskVisible) {
            DetailLoadingMask(onBack = onBack)
        }
    }
}

@Composable
private fun DetailContent(
    mediaId: Int,
    model: DetailUiModel,
    autoPlay: Boolean,
    onBack: () -> Unit,
    onSelectSeason: (Int) -> Unit,
    onSelectLine: (Int) -> Unit,
    onPlay: (playMediaId: Int, episode: Int, title: String, fullscreen: Boolean, lineIndex: Int) -> Unit,
    onOpenRecommend: (MediaItemDto) -> Unit,
) {
    val details = model.detail.details
    val title = details?.title.orEmpty().ifBlank { "Untitled" }
    var shellPlaying by remember { mutableStateOf(false) }
    var shellCc by remember { mutableStateOf(false) }
    // UI-only position stub for embedded shell (real Exo is in PlayerActivity).
    var shellPos by remember { mutableStateOf(0L) }
    val shellDur = DETAIL_SHELL_DURATION_MS
    var autoPlayFired by remember(mediaId) { mutableStateOf(false) }
    var selectedEpisode by remember(mediaId, model.playEpisode) {
        mutableStateOf(model.playEpisode)
    }

    fun playEpisode(ep: Int, fullscreen: Boolean, lineIndex: Int = model.selectedLineIndex) {
        if (mediaId <= 0) return
        shellPlaying = true
        selectedEpisode = ep
        onPlay(mediaId, ep, title, fullscreen, lineIndex)
    }

    // Play-auto: when EXTRA_AUTO_PLAY, jump to Player as soon as lines Ready — no red-button wait.
    LaunchedEffect(autoPlay, model.linesStatus, model.selectedLineIndex, mediaId) {
        if (!autoPlay || autoPlayFired || mediaId <= 0) return@LaunchedEffect
        if (model.linesStatus != LinesStatus.Ready || model.lines.isEmpty()) return@LaunchedEffect
        autoPlayFired = true
        playEpisode(model.playEpisode, fullscreen = false, lineIndex = model.selectedLineIndex)
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
            val configuration = LocalConfiguration.current
            val screenHeightDp = configuration.screenHeightDp
            val screenWidthDp = configuration.screenWidthDp
            val minH = screenHeightDp.dp / 3
            val videoH = screenWidthDp.dp * 9f / 16f
            val containerH = maxOf(minH, videoH)
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
                    // Placeholder surface: poster dimmed as stand-in until Exo embeds here
                    PosterImage(
                        posterPath = details?.posterPath,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0x99000000)),
                    )
                }
                // Controls host fills the full container (not only the 16:9 content box)
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
                        if (!shellPlaying) {
                            val ep = if (model.isMovie) {
                                0
                            } else {
                                model.selectedSeason?.episodes?.firstOrNull()?.episodeNumber ?: 1
                            }
                            playEpisode(ep, fullscreen = false)
                        } else {
                            shellPlaying = false
                        }
                    },
                    onSeekBack15 = { shellPos = (shellPos - 15_000L).coerceAtLeast(0L) },
                    onSeekForward15 = { shellPos = (shellPos + 15_000L).coerceAtMost(shellDur) },
                    onSeek = { shellPos = it.coerceIn(0L, shellDur) },
                    onToggleCc = { shellCc = !shellCc },
                    onAuto = { /* stub: quality sheet later */ },
                    onFullscreen = {
                        val ep = if (model.isMovie) {
                            0
                        } else {
                            model.selectedSeason?.episodes?.firstOrNull()?.episodeNumber ?: 1
                        }
                        playEpisode(ep, fullscreen = true)
                    },
                )
            }
        }

        item {
            val lineLabel = model.lines.getOrNull(model.selectedLineIndex)?.name
                ?.ifBlank { null }
                ?: model.lines.getOrNull(model.selectedLineIndex)?.let {
                    "Line ${model.selectedLineIndex + 1}"
                }
            val qualityBadgeRaw = model.lines
                .getOrNull(model.selectedLineIndex)
                ?.list
                ?.firstOrNull()
                ?.quality
            WatchMetaSection(
                details = details,
                titleFallback = title,
                currentLineLabel = lineLabel,
                lines = model.lines,
                selectedLineIndex = model.selectedLineIndex,
                linesStatus = model.linesStatus,
                lineProbeOk = model.lineProbeOk,
                seasons = model.seasons,
                selectedSeasonIndex = model.selectedSeasonIndex,
                selectedEpisode = selectedEpisode,
                recommends = model.detail.recommends,
                onSelectLine = { index ->
                    onSelectLine(index)
                    playEpisode(selectedEpisode, fullscreen = false, lineIndex = index)
                },
                onSelectSeason = onSelectSeason,
                onSelectEpisode = { playId, ep, t ->
                    selectedEpisode = ep
                    onPlay(playId, ep, t, false, model.selectedLineIndex)
                },
                onOpenRecommend = onOpenRecommend,
                fallbackMediaId = mediaId,
                qualityBadgeRaw = qualityBadgeRaw,
            )
        }
    }
}

private const val DETAIL_SHELL_DURATION_MS = 108L * 60_000L + 37_000L
