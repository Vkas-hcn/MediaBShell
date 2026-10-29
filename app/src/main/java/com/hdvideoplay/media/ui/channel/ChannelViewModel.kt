package com.hdvideoplay.media.ui.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hdvideoplay.media.data.dto.MediaItemDto
import com.hdvideoplay.media.data.repo.MediaRepository
import com.hdvideoplay.media.ui.components.UiState
import com.hdvideoplay.media.util.PosterUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Channel list entry: home_stream_data id/name + optional icon from streaming/
 * matched by name. Empty/invalid stream_url is never treated as an image.
 */
data class ChannelEntry(
    val id: Int,
    val name: String,
    val iconUrl: String?,
)

data class ChannelUi(
    val platforms: UiState<List<ChannelEntry>> = UiState.Loading,
    val selectedId: Int? = null,
    val movies: UiState<List<MediaItemDto>> = UiState.Loading,
)

class ChannelViewModel(
    private val repo: MediaRepository = MediaRepository(),
) : ViewModel() {

    private val _ui = MutableStateFlow(ChannelUi())
    val ui: StateFlow<ChannelUi> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.update { it.copy(platforms = UiState.Loading, movies = UiState.Loading) }
            val streamsDeferred = async { repo.homeStreamData() }
            val platformsDeferred = async { repo.streaming() }
            val streamsResult = streamsDeferred.await()
            val platformsResult = platformsDeferred.await()

            streamsResult.fold(
                onSuccess = { streams ->
                    val iconByName = platformsResult.getOrNull()
                        .orEmpty()
                        .mapNotNull { p ->
                            val key = p.name.trim().lowercase()
                            if (key.isEmpty()) return@mapNotNull null
                            val icon = PosterUtils.resolvePosterUrl(p.icon) ?: return@mapNotNull null
                            key to icon
                        }
                        .toMap()

                    val entries = streams.map { s ->
                        // Cover priority: merged streaming/ icon → valid image URL → null (letter block).
                        // Empty / bare-size stream_url must never become a poster.
                        val fromPlatform = iconByName[s.streamName.trim().lowercase()]
                        val fromStream = PosterUtils.resolvePosterUrl(s.streamUrl)
                        ChannelEntry(
                            id = s.id,
                            name = s.streamName,
                            iconUrl = fromPlatform ?: fromStream,
                        )
                    }
                    // Default: first platform (or "hot" if named so).
                    val defaultId = entries.firstOrNull {
                        it.name.trim().equals("热门", ignoreCase = true) ||
                            it.name.trim().equals("hot", ignoreCase = true) ||
                            it.name.trim().equals("popular", ignoreCase = true)
                    }?.id ?: entries.firstOrNull()?.id

                    _ui.update {
                        it.copy(
                            platforms = UiState.Success(entries),
                            selectedId = defaultId,
                        )
                    }
                    if (defaultId != null) {
                        loadMovies(defaultId)
                    } else {
                        _ui.update { it.copy(movies = UiState.Success(emptyList())) }
                    }
                },
                onFailure = { e ->
                    _ui.update {
                        it.copy(platforms = UiState.Error(e.message ?: "Unknown error"))
                    }
                },
            )
        }
    }

    fun selectPlatform(id: Int) {
        if (_ui.value.selectedId == id) return
        _ui.update { it.copy(selectedId = id) }
        loadMovies(id)
    }

    fun reloadMovies() {
        val id = _ui.value.selectedId ?: return
        loadMovies(id)
    }

    private fun loadMovies(moduleId: Int) {
        viewModelScope.launch {
            _ui.update { it.copy(movies = UiState.Loading) }
            repo.moduleData(id = moduleId, page = 1).fold(
                onSuccess = { list ->
                    _ui.update { it.copy(movies = UiState.Success(list)) }
                },
                onFailure = { e ->
                    _ui.update {
                        it.copy(movies = UiState.Error(e.message ?: "Unknown error"))
                    }
                },
            )
        }
    }

    companion object {
        fun factory(repo: MediaRepository = MediaRepository()) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ChannelViewModel(repo) as T
            }
    }
}
