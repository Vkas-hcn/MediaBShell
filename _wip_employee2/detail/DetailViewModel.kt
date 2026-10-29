package com.hdvideoplay.media.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hdvideoplay.media.data.dto.DetailResponseDto
import com.hdvideoplay.media.data.dto.SeasonDto
import com.hdvideoplay.media.data.repo.MediaRepository
import com.hdvideoplay.media.ui.components.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailUiModel(
    val detail: DetailResponseDto,
    val selectedSeasonIndex: Int = 0,
) {
    val seasons: List<SeasonDto> get() = detail.seasons
    val isMovie: Boolean get() = seasons.isEmpty()
    val selectedSeason: SeasonDto? get() = seasons.getOrNull(selectedSeasonIndex)
}

class DetailViewModel(
    private val mediaId: Int,
    private val repo: MediaRepository = MediaRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<DetailUiModel>>(UiState.Loading)
    val state: StateFlow<UiState<DetailUiModel>> = _state.asStateFlow()

    init {
        if (mediaId <= 0) {
            _state.value = UiState.Error("Invalid media id")
        } else {
            refresh()
        }
    }

    fun refresh() {
        if (mediaId <= 0) {
            _state.value = UiState.Error("Invalid media id")
            return
        }
        viewModelScope.launch {
            _state.value = UiState.Loading
            val result = repo.detail(mediaId)
            _state.value = result.fold(
                onSuccess = { data ->
                    if (data.details == null && data.seasons.isEmpty() && data.recommends.isEmpty()) {
                        UiState.Error("Not found")
                    } else {
                        UiState.Success(DetailUiModel(detail = data, selectedSeasonIndex = 0))
                    }
                },
                onFailure = { UiState.Error(it.message ?: "Unknown error") },
            )
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

    companion object {
        fun factory(mediaId: Int, repo: MediaRepository = MediaRepository()) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DetailViewModel(mediaId, repo) as T
            }
    }
}
