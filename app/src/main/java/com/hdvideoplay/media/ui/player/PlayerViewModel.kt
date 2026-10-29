package com.hdvideoplay.media.ui.player

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hdvideoplay.media.data.dto.DetailResponseDto
import com.hdvideoplay.media.data.dto.OutsideLinkDto
import com.hdvideoplay.media.data.dto.PlayReportBody
import com.hdvideoplay.media.data.dto.PlaySourceDto
import com.hdvideoplay.media.data.repo.MediaRepository
import com.hdvideoplay.media.ui.components.UiState
import com.hdvideoplay.media.util.asIntOrNull
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayerReady(
    val links: List<OutsideLinkDto>,
    val lineIndex: Int = 0,
    val sourceIndex: Int = 0,
) {
    val currentLine: OutsideLinkDto? get() = links.getOrNull(lineIndex)
    val currentSource: PlaySourceDto? get() = currentLine?.list?.getOrNull(sourceIndex)
}

/** Detail chrome for the portrait meta panel under the player. */
data class PlayerDetailMeta(
    val detail: DetailResponseDto,
    val selectedSeasonIndex: Int = 0,
)

sealed interface PlayerEvent {
    data class Toast(val message: String) : PlayerEvent
    data object AllFailed : PlayerEvent
}

/**
 * HaloDrift alignment: after outside/links, publish all lines and start the first
 * source immediately. No PlaySourceProbe hard filter. Exo 403/etc → [onSourceFailed]
 * advances source then line + play_report.
 *
 * Also loads detail meta for the watch-path info panel (title / overview / seasons /
 * recommends) without gating playback.
 */
class PlayerViewModel(
    app: Application,
    private val mediaId: Int,
    initialEpisode: Int,
    private val initialLineIndex: Int = 0,
    private val repo: MediaRepository = MediaRepository(),
) : AndroidViewModel(app) {

    private var episode: Int = initialEpisode

    private val _linksState = MutableStateFlow<UiState<PlayerReady>>(UiState.Loading)
    val linksState: StateFlow<UiState<PlayerReady>> = _linksState.asStateFlow()

    private val _detailMeta = MutableStateFlow<PlayerDetailMeta?>(null)
    val detailMeta: StateFlow<PlayerDetailMeta?> = _detailMeta.asStateFlow()

    private val _currentEpisode = MutableStateFlow(initialEpisode)
    val currentEpisode: StateFlow<Int> = _currentEpisode.asStateFlow()

    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

    private val androidId: String? =
        Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID)

    init {
        loadDetailMeta()
        loadLinks()
    }

    private fun loadDetailMeta() {
        if (mediaId <= 0) return
        viewModelScope.launch {
            val result = repo.detail(mediaId)
            val data = result.getOrNull() ?: return@launch
            if (data.details == null && data.seasons.isEmpty() && data.recommends.isEmpty()) {
                return@launch
            }
            // Prefer season that contains the current episode when possible.
            val seasonIdx = data.seasons.indexOfFirst { season ->
                season.episodes.any { it.episodeNumber == episode }
            }.let { if (it >= 0) it else 0 }
            _detailMeta.value = PlayerDetailMeta(
                detail = data,
                selectedSeasonIndex = seasonIdx.coerceAtLeast(0),
            )
        }
    }

    fun loadLinks(showLoading: Boolean = true) {
        if (mediaId <= 0) {
            _linksState.value = UiState.Error("invalid_media")
            return
        }
        viewModelScope.launch {
            // Episode switches must not tear down PlayerContent/Exo via Loading flash.
            if (showLoading || _linksState.value !is UiState.Success) {
                _linksState.value = UiState.Loading
            }
            val result = repo.outsideLinks(mediaId, episode)
            val data = result.getOrElse { err ->
                _linksState.value = UiState.Error(err.message ?: "Unknown error")
                return@launch
            }
            val raw = data.outsideLinks.mapNotNull { link ->
                val sources = link.list.filter { it.url.isNotBlank() }
                if (sources.isEmpty()) null else link.copy(list = sources)
            }
            if (raw.isEmpty()) {
                // API truly empty — not probe-empty.
                _linksState.value = UiState.Error("empty_links")
                return@launch
            }
            // Soft prefer HLS inside each line (still keep all sources for failover).
            val preferred = raw.map { link ->
                val sorted = link.list.sortedBy { src ->
                    val u = src.url.lowercase()
                    when {
                        ".m3u8" in u -> 0
                        ".mpd" in u -> 1
                        else -> 2
                    }
                }
                link.copy(list = sorted)
            }
            // Soft prefer lines that have HLS/DASH before pure progressive CDN mp4.
            val ordered = preferred.sortedBy { link ->
                val u = link.list.firstOrNull()?.url?.lowercase().orEmpty()
                when {
                    ".m3u8" in u -> 0
                    ".mpd" in u -> 1
                    else -> 2
                }
            }
            // Direct play: no filterAvailable / no_alive_links gate.
            // Explicit Detail lineIndex is against API (raw) order; map into soft-preferred
            // [ordered] by URL set — not firstUrl, because in-line HLS sort moves first().
            val line = if (initialLineIndex in raw.indices && initialLineIndex != 0) {
                val targetUrls = raw[initialLineIndex].list.map { it.url }.toSet()
                ordered.indexOfFirst { link ->
                    link.list.any { it.url in targetUrls }
                }.let { if (it >= 0) it else 0 }
            } else {
                // Default: start on first soft-preferred (usually HLS) line.
                0
            }
            _linksState.value = UiState.Success(
                PlayerReady(
                    links = ordered,
                    lineIndex = line.coerceIn(0, ordered.lastIndex),
                    sourceIndex = 0,
                ),
            )
        }
    }

    fun selectLine(lineIndex: Int) {
        val current = (_linksState.value as? UiState.Success)?.data ?: return
        if (lineIndex !in current.links.indices) return
        if (lineIndex == current.lineIndex && current.sourceIndex == 0) return
        _linksState.value = UiState.Success(
            current.copy(lineIndex = lineIndex, sourceIndex = 0),
        )
    }

    fun selectSource(sourceIndex: Int) {
        val current = (_linksState.value as? UiState.Success)?.data ?: return
        val line = current.currentLine ?: return
        if (sourceIndex !in line.list.indices) return
        if (sourceIndex == current.sourceIndex) return
        _linksState.value = UiState.Success(current.copy(sourceIndex = sourceIndex))
    }

    /** Season chip only — does not reload links until an episode is picked. */
    fun selectSeason(index: Int) {
        val meta = _detailMeta.value ?: return
        val max = meta.detail.seasons.lastIndex.coerceAtLeast(0)
        _detailMeta.value = meta.copy(selectedSeasonIndex = index.coerceIn(0, max))
    }

    /**
     * Episode change → reload outside/links for the new episode and auto-play
     * via the existing LaunchedEffect(line/source) prepare path.
     */
    fun switchEpisode(newEpisode: Int) {
        if (newEpisode == episode && _linksState.value is UiState.Success) return
        episode = newEpisode
        _currentEpisode.value = newEpisode
        loadLinks(showLoading = false)
    }

    fun onSourceFailed(userMessage: String? = null): Boolean {
        val current = (_linksState.value as? UiState.Success)?.data ?: return false
        report(current, success = false)
        if (userMessage != null) {
            _events.tryEmit(PlayerEvent.Toast(userMessage))
        }
        val next = advance(current)
        return if (next != null) {
            _linksState.value = UiState.Success(next)
            true
        } else {
            _events.tryEmit(PlayerEvent.AllFailed)
            false
        }
    }

    fun onSourceReady() {
        val current = (_linksState.value as? UiState.Success)?.data ?: return
        report(current, success = true)
    }

    private fun advance(current: PlayerReady): PlayerReady? {
        val line = current.currentLine ?: return null
        val nextSource = current.sourceIndex + 1
        if (nextSource < line.list.size) {
            return current.copy(sourceIndex = nextSource)
        }
        for (i in (current.lineIndex + 1) until current.links.size) {
            if (current.links[i].list.isNotEmpty()) {
                return current.copy(lineIndex = i, sourceIndex = 0)
            }
        }
        return null
    }

    private fun report(ready: PlayerReady, success: Boolean) {
        val source = ready.currentSource ?: return
        val listId = source.id.asIntOrNull() ?: return
        val lineName = ready.currentLine?.name.orEmpty().ifBlank { "Line" }
        viewModelScope.launch {
            repo.playReport(
                PlayReportBody(
                    mediaId = mediaId,
                    listId = listId,
                    episode = episode,
                    lineName = lineName,
                    androidId = androidId,
                    isSuccess = success,
                ),
            )
        }
    }

    companion object {
        fun factory(
            app: Application,
            mediaId: Int,
            episode: Int,
            initialLineIndex: Int = 0,
            repo: MediaRepository = MediaRepository(),
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PlayerViewModel(app, mediaId, episode, initialLineIndex, repo) as T
        }
    }
}
