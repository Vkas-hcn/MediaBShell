package com.hdvideoplay.media.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hdvideoplay.media.data.dto.DetailResponseDto
import com.hdvideoplay.media.data.dto.OutsideLinkDto
import com.hdvideoplay.media.data.dto.SeasonDto
import com.hdvideoplay.media.data.probe.PlaySourceProbe
import com.hdvideoplay.media.data.repo.MediaRepository
import com.hdvideoplay.media.ui.components.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Line UI status. The detail page holds this at [Checking] until every line has
 * been probed, so the Play Source row shows skeletons until the check completes.
 * [None] only when outside/links returns no usable URLs.
 */
enum class LinesStatus {
    /** Waiting for outside/links API (not CDN probe). */
    Checking,
    Ready,
    None,
}

data class DetailUiModel(
    val detail: DetailResponseDto,
    val selectedSeasonIndex: Int = 0,
    /** All API lines with non-blank URLs, ordered valid-first after probe. */
    val lines: List<OutsideLinkDto> = emptyList(),
    val selectedLineIndex: Int = 0,
    val linesStatus: LinesStatus = LinesStatus.Checking,
    /** Probe marks aligned with [lines]; true if any source got 200/206. */
    val lineProbeOk: List<Boolean?> = emptyList(),
) {
    val seasons: List<SeasonDto> get() = detail.seasons
    val isMovie: Boolean get() = seasons.isEmpty()
    val selectedSeason: SeasonDto? get() = seasons.getOrNull(selectedSeasonIndex)
    val playEpisode: Int
        get() = if (isMovie) {
            0
        } else {
            selectedSeason?.episodes?.firstOrNull()?.episodeNumber ?: 1
        }
    /** True only when API returned no lines — not when weak probe fails. */
    val noAliveLines: Boolean get() = linesStatus == LinesStatus.None
}

class DetailViewModel(
    private val mediaId: Int,
    private val initialEpisode: Int = 0,
    private val repo: MediaRepository = MediaRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<DetailUiModel>>(UiState.Loading)
    val state: StateFlow<UiState<DetailUiModel>> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        if (mediaId <= 0) {
            _state.value = UiState.Error("invalid_media")
        } else {
            refresh()
        }
    }

    fun refresh() {
        if (mediaId <= 0) {
            _state.value = UiState.Error("invalid_media")
            return
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = UiState.Loading
            var lastError = "Unknown error"
            repeat(MAX_DETAIL_ATTEMPTS) { attempt ->
                val result = try {
                    repo.detail(mediaId)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    Result.failure(t)
                }
                val data = result.getOrNull()
                if (data != null &&
                    (data.details != null || data.seasons.isNotEmpty() || data.recommends.isNotEmpty())
                ) {
                    _state.value = UiState.Success(
                        DetailUiModel(
                            detail = data,
                            selectedSeasonIndex = 0,
                            lines = emptyList(),
                            selectedLineIndex = 0,
                            linesStatus = LinesStatus.Checking,
                        ),
                    )
                    loadLinks()
                    return@launch
                }
                lastError = result.exceptionOrNull()?.message
                    ?: if (data == null) "Unknown error" else "Not found"
                if (attempt < MAX_DETAIL_ATTEMPTS - 1) {
                    delay(DETAIL_RETRY_DELAY_MS)
                }
            }
            _state.value = UiState.Error(lastError)
        }
    }

    /** Load, probe all lines concurrently, then publish valid-first ordering. */
    private suspend fun loadLinks() {
        val linksResult = try {
            repo.outsideLinks(mediaId, initialEpisode)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
        val raw = linksResult
            .getOrNull()
            ?.outsideLinks
            ?.mapNotNull { link ->
                val sources = link.list.filter { it.url.isNotBlank() }
                if (sources.isEmpty()) null else link.copy(list = sources)
            }
            .orEmpty()

        if (raw.isEmpty()) {
            publishLines(emptyList(), emptyList(), LinesStatus.None)
            return
        }

        // Keep LinesStatus.Checking while this runs; PlaySourceRow stays skeleton.
        val outcome = try {
            PlaySourceProbe.probeLinksTimed(raw, PlaySourceProbe.PROBE_TIMEOUT_MS)
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            PlaySourceProbe.ProbeOutcome(
                probed = raw.map {
                    PlaySourceProbe.ProbedLink(it, List(it.list.size) { false })
                },
                timedOut = false,
            )
        }
        val probed = outcome.probed
        if (outcome.timedOut && probed.none { it.isValid }) {
            // Deadline hit without a playable source: show the config order.
            publishLines(raw, List(raw.size) { null }, LinesStatus.Ready, 0)
            return
        }
        val ordered = PlaySourceProbe.validFirst(probed)
        val flags = ordered.map { it.isValid }
        val lines = ordered.map { it.orderedLink() }
        val selected = flags.indexOfFirst { it }.takeIf { it >= 0 } ?: 0
        publishLines(lines, flags, LinesStatus.Ready, selected)
    }

    private fun publishLines(
        lines: List<OutsideLinkDto>,
        flags: List<Boolean?>,
        status: LinesStatus,
        selected: Int = 0,
    ) {
        _state.update { current ->
            if (current is UiState.Success) {
                current.copy(
                    data = current.data.copy(
                        lines = lines,
                        selectedLineIndex = selected,
                        linesStatus = status,
                        lineProbeOk = flags,
                    ),
                )
            } else {
                current
            }
        }
    }

    fun selectSeason(index: Int) {
        _state.update { current ->
            if (current is UiState.Success) {
                val max = current.data.seasons.lastIndex.coerceAtLeast(0)
                current.copy(data = current.data.copy(selectedSeasonIndex = index.coerceIn(0, max)))
            } else {
                current
            }
        }
    }

    fun selectLine(index: Int) {
        _state.update { current ->
            if (current is UiState.Success) {
                val max = current.data.lines.lastIndex
                if (max < 0) current
                else current.copy(
                    data = current.data.copy(selectedLineIndex = index.coerceIn(0, max)),
                )
            } else {
                current
            }
        }
    }

    companion object {
        private const val MAX_DETAIL_ATTEMPTS = 10
        private const val DETAIL_RETRY_DELAY_MS = 2_000L

        fun factory(
            mediaId: Int,
            initialEpisode: Int = 0,
            repo: MediaRepository = MediaRepository(),
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DetailViewModel(mediaId, initialEpisode, repo) as T
        }
    }
}
