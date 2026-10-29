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
 * Line UI status. HaloDrift: show API lines immediately — never gate on probe.
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
    /** All API lines with non-blank URLs — never probe-filtered. */
    val lines: List<OutsideLinkDto> = emptyList(),
    val selectedLineIndex: Int = 0,
    val linesStatus: LinesStatus = LinesStatus.Checking,
    /**
     * Optional weak probe marks per line (true if any source got 200/206).
     * Null entry / missing = unknown. Marks must not hide chips or block play.
     */
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
    /** When true, run optional background weak probe for chip marks only. */
    private val enableWeakProbeMarks: Boolean = true,
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

    /** Lines load after detail success; their failure must not hide the detail mask. */
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
        // HaloDrift: show all API lines immediately — do NOT filterAvailable.
        val status = if (raw.isNotEmpty()) LinesStatus.Ready else LinesStatus.None
        _state.update { current ->
            if (current is UiState.Success) {
                current.copy(
                    data = current.data.copy(
                        lines = raw,
                        selectedLineIndex = 0,
                        linesStatus = status,
                        lineProbeOk = List(raw.size) { null },
                    ),
                )
            } else {
                current
            }
        }

        if (enableWeakProbeMarks && raw.isNotEmpty()) {
            launchWeakMarks(raw)
        }
    }

    /** Background marks only — never flips Ready→None or drops lines. */
    private fun launchWeakMarks(raw: List<OutsideLinkDto>) {
        viewModelScope.launch {
            val marks = runCatching { PlaySourceProbe.markAvailability(raw) }.getOrNull()
                ?: return@launch
            val lineOk = marks.map { srcFlags ->
                if (srcFlags.isEmpty()) false else srcFlags.any { it }
            }
            _state.update { current ->
                if (current !is UiState.Success) return@update current
                // Only annotate if still showing the same line count.
                if (current.data.lines.size != lineOk.size) return@update current
                val cur = current.data.selectedLineIndex
                val curOk = lineOk.getOrNull(cur) == true
                val firstProbeOk = lineOk.indexOfFirst { it == true }
                val nextSelected =
                    if (!curOk && firstProbeOk >= 0) firstProbeOk else cur
                current.copy(
                    data = current.data.copy(
                        lineProbeOk = lineOk,
                        selectedLineIndex = nextSelected,
                    ),
                )
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
