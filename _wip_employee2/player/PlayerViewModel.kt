package com.hdvideoplay.media.ui.player

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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

sealed interface PlayerEvent {
    data class Toast(val message: String) : PlayerEvent
    data object AllFailed : PlayerEvent
}

class PlayerViewModel(
    app: Application,
    private val mediaId: Int,
    private val episode: Int,
    private val repo: MediaRepository = MediaRepository(),
) : AndroidViewModel(app) {

    private val _linksState = MutableStateFlow<UiState<PlayerReady>>(UiState.Loading)
    val linksState: StateFlow<UiState<PlayerReady>> = _linksState.asStateFlow()

    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

    private val androidId: String? =
        Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID)

    init {
        loadLinks()
    }

    fun loadLinks() {
        if (mediaId <= 0) {
            _linksState.value = UiState.Error("Invalid media id")
            return
        }
        viewModelScope.launch {
            _linksState.value = UiState.Loading
            val result = repo.outsideLinks(mediaId, episode)
            _linksState.value = result.fold(
                onSuccess = { data ->
                    val links = data.outsideLinks.filter { it.list.isNotEmpty() }
                    if (links.isEmpty()) {
                        UiState.Error("empty_links")
                    } else {
                        UiState.Success(PlayerReady(links = links))
                    }
                },
                onFailure = { UiState.Error(it.message ?: "Unknown error") },
            )
        }
    }

    fun selectLine(lineIndex: Int) {
        val current = (_linksState.value as? UiState.Success)?.data ?: return
        if (lineIndex !in current.links.indices) return
        _linksState.value = UiState.Success(
            current.copy(lineIndex = lineIndex, sourceIndex = 0),
        )
    }

    fun selectSource(sourceIndex: Int) {
        val current = (_linksState.value as? UiState.Success)?.data ?: return
        val line = current.currentLine ?: return
        if (sourceIndex !in line.list.indices) return
        _linksState.value = UiState.Success(current.copy(sourceIndex = sourceIndex))
    }

    /**
     * Current source failed: report failure, advance to next source/line.
     * @return true if a next source was selected; false if all exhausted.
     */
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
        // next line with a non-empty list
        for (i in (current.lineIndex + 1) until current.links.size) {
            if (current.links[i].list.isNotEmpty()) {
                return current.copy(lineIndex = i, sourceIndex = 0)
            }
        }
        return null
    }

    private fun report(ready: PlayerReady, success: Boolean) {
        val source = ready.currentSource ?: return
        val listId = source.id.asIntOrNull() ?: return // skip if not Int-convertible
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
            // ignore result — must not affect playback
        }
    }

    companion object {
        fun factory(
            app: Application,
            mediaId: Int,
            episode: Int,
            repo: MediaRepository = MediaRepository(),
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PlayerViewModel(app, mediaId, episode, repo) as T
        }
    }
}
