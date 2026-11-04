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
import com.hdvideoplay.media.data.probe.PlaySourceProbe
import com.hdvideoplay.media.data.repo.ApiException
import com.hdvideoplay.media.data.repo.MediaRepository
import com.hdvideoplay.media.track.TrackEvents
import com.hdvideoplay.media.ui.components.UiState
import com.hdvideoplay.media.util.asIntOrNull
import retrofit2.HttpException
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
    /** 仅入口会话（intent 带 from_position）才上报 detail_request/view/error。 */
    private val trackDetailEvents: Boolean = false,
    private val fromPosition: String? = null,
    private val moduleId: Int? = null,
) : AndroidViewModel(app) {

    private var episode: Int = initialEpisode

    /** play_fail 去重：同一次播放会话内同一线路同一错误只报一次；集数切换重置。 */
    private val reportedFailKeys = mutableSetOf<String>()

    /** detail_view 按数据实例身份去重（同一轮数据只报一次）。 */
    private var detailViewReportedForData: PlayerReady? = null

    private val _linksState = MutableStateFlow<UiState<PlayerReady>>(UiState.Loading)
    val linksState: StateFlow<UiState<PlayerReady>> = _linksState.asStateFlow()

    /** True while the all-line probe runs for the selected episode. */
    private val _linksProbing = MutableStateFlow(false)
    val linksProbing: StateFlow<Boolean> = _linksProbing.asStateFlow()

    private val _detailMeta = MutableStateFlow<PlayerDetailMeta?>(null)
    val detailMeta: StateFlow<PlayerDetailMeta?> = _detailMeta.asStateFlow()

    /** True until the detail-meta request completes (success or failure). */
    private val _detailMetaLoading = MutableStateFlow(mediaId > 0)
    val detailMetaLoading: StateFlow<Boolean> = _detailMetaLoading.asStateFlow()

    private val _currentEpisode = MutableStateFlow(initialEpisode)
    val currentEpisode: StateFlow<Int> = _currentEpisode.asStateFlow()

    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

    private val androidId: String? =
        Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID)

    init {
        viewModelScope.launch {
            // 先取详情：带集数的影片在 episode<=0 时默认选第一集，
            // 否则 outside/links(episode=0) 返回空 → 误报“无资源”。
            val detail = fetchDetailMeta()
            if (episode <= 0) {
                val first = detail?.seasons?.firstOrNull()?.episodes?.firstOrNull()?.episodeNumber
                if (first != null && first > 0) {
                    episode = first
                    _currentEpisode.value = first
                    _detailMeta.value = _detailMeta.value?.copy(
                        selectedSeasonIndex = detail.seasons.indexOfFirst { season ->
                            season.episodes.any { it.episodeNumber == first }
                        }.coerceAtLeast(0),
                    )
                }
            }
            loadLinks()
        }
    }

    /** 拉详情并写入 [PlayerDetailMeta]；失败返回 null（不阻断播放）。 */
    private suspend fun fetchDetailMeta(): DetailResponseDto? {
        if (mediaId <= 0) {
            _detailMetaLoading.value = false
            return null
        }
        return try {
            val result = repo.detail(mediaId)
            val data = result.getOrNull() ?: return null
            if (data.details == null && data.seasons.isEmpty() && data.recommends.isEmpty()) {
                return null
            }
            // Prefer season that contains the current episode when possible.
            val seasonIdx = data.seasons.indexOfFirst { season ->
                season.episodes.any { it.episodeNumber == episode }
            }.let { if (it >= 0) it else 0 }
            _detailMeta.value = PlayerDetailMeta(
                detail = data,
                selectedSeasonIndex = seasonIdx.coerceAtLeast(0),
            )
            data
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (_: Throwable) {
            null
        } finally {
            // Never leave the skeleton shimmering forever if detail fails.
            _detailMetaLoading.value = false
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
            // detail_request：详情页（本应用内容入口直接落播放页）资源请求发起时上报。
            if (trackDetailEvents) TrackEvents.detailRequest()
            val result = repo.outsideLinks(mediaId, episode)
            val data = result.getOrElse { err ->
                reportLinksLoadError(err.message ?: "Unknown error", err)
                _linksState.value = UiState.Error(err.message ?: "Unknown error")
                return@launch
            }
            val raw = data.outsideLinks.mapNotNull { link ->
                val sources = link.list.filter { it.url.isNotBlank() }
                if (sources.isEmpty()) null else link.copy(list = sources)
            }
            if (raw.isEmpty()) {
                // API truly empty — not probe-empty.
                reportLinksLoadError("empty_links", null)
                _linksState.value = UiState.Error("empty_links")
                return@launch
            }

            // Probe every line/source concurrently before publishing playback.
            // The player surface shows its circular indicator while this runs.
            _linksProbing.value = true
            try {
                val outcome = try {
                    PlaySourceProbe.probeLinksTimed(raw, PlaySourceProbe.PROBE_TIMEOUT_MS)
                } catch (ce: kotlinx.coroutines.CancellationException) {
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
                    // Deadline hit without a playable source: play the config order.
                    val requested = initialLineIndex.coerceIn(0, raw.lastIndex)
                    _linksState.value = UiState.Success(
                        PlayerReady(
                            links = raw,
                            lineIndex = requested,
                            sourceIndex = 0,
                        ),
                    )
                    return@launch
                }

                val orderedProbed = PlaySourceProbe.validFirst(probed)
                val firstValid = orderedProbed.indexOfFirst { it.isValid }
                if (firstValid < 0) {
                    reportLinksLoadError("no_alive_links", null)
                    _linksState.value = UiState.Error("no_alive_links")
                    return@launch
                }

                val ordered = orderedProbed.map { it.orderedLink() }
                // Detail passes its post-probe line index; direct content opens at 0.
                // Keep an explicit valid selection, otherwise fall back to first verified.
                val requestedValid = initialLineIndex.takeIf { it in orderedProbed.indices }
                val line = if (requestedValid != null && orderedProbed[requestedValid].isValid) {
                    requestedValid
                } else {
                    firstValid
                }
                _linksState.value = UiState.Success(
                    PlayerReady(
                        links = ordered,
                        lineIndex = line.coerceIn(0, ordered.lastIndex),
                        sourceIndex = 0,
                    ),
                )
            } finally {
                _linksProbing.value = false
            }
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
        reportedFailKeys.clear()
        loadLinks(showLoading = false)
    }

    /**
     * detail_error：links 资源请求/加载失败（每次请求失败报一次，重试失败可再报）。
     */
    private fun reportLinksLoadError(message: String, cause: Throwable?) {
        if (!trackDetailEvents) return
        val errorCode = when {
            cause is ApiException -> cause.code
            cause is HttpException -> cause.code().toString()
            cause != null -> cause.javaClass.simpleName
            else -> message
        }
        TrackEvents.detailError(mediaId, errorCode, message)
    }

    /** detail_view：links 资源成功加载并展示时上报（同一轮数据只报一次）。 */
    fun reportDetailShownOnce(data: PlayerReady) {
        if (!trackDetailEvents) return
        if (detailViewReportedForData === data) return
        detailViewReportedForData = data
        val from = fromPosition ?: return
        // 内容入口现状恒为自动播放（方案 A：进播放页即自动播）。
        TrackEvents.detailView(mediaId, autoPlay = true, fromPosition = from, moduleId = moduleId)
    }

    /**
     * play_fail：当前线路播放失败时上报；全部线路耗尽时额外记录 line_name="all"
     * 的最终失败情况（区分单线路失败与全部线路失败）。
     */
    fun onSourceFailed(userMessage: String? = null, errorMsg: String? = null): Boolean {
        val current = (_linksState.value as? UiState.Success)?.data ?: return false
        val lineName = current.currentLine?.name.orEmpty().ifBlank { "Line ${current.lineIndex + 1}" }
        val msg = errorMsg.orEmpty().ifBlank { "unknown_error" }
        if (reportedFailKeys.add("${current.lineIndex}|$msg")) {
            TrackEvents.playFail(mediaId, episode, lineName, msg)
        }
        report(current, success = false)
        if (userMessage != null) {
            _events.tryEmit(PlayerEvent.Toast(userMessage))
        }
        val next = advance(current)
        return if (next != null) {
            _linksState.value = UiState.Success(next)
            true
        } else {
            if (reportedFailKeys.add("all|$msg")) {
                TrackEvents.playFail(mediaId, episode, "all", msg)
            }
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
            trackDetailEvents: Boolean = false,
            fromPosition: String? = null,
            moduleId: Int? = null,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PlayerViewModel(
                    app,
                    mediaId,
                    episode,
                    initialLineIndex,
                    repo,
                    trackDetailEvents,
                    fromPosition,
                    moduleId,
                ) as T
        }
    }
}
