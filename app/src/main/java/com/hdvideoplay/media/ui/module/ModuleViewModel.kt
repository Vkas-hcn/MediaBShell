package com.hdvideoplay.media.ui.module

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hdvideoplay.media.data.dto.MediaItemDto
import com.hdvideoplay.media.data.repo.MediaRepository
import com.hdvideoplay.media.ui.components.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModuleUi(
    val title: String,
    val items: List<MediaItemDto> = emptyList(),
    val page: Int = 0,
    val loading: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
    val initial: UiState<Unit> = UiState.Loading,
)

class ModuleViewModel(
    private val moduleId: Int,
    title: String,
    private val repo: MediaRepository = MediaRepository(),
) : ViewModel() {

    private val _ui = MutableStateFlow(ModuleUi(title = title))
    val ui: StateFlow<ModuleUi> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    items = emptyList(),
                    page = 0,
                    endReached = false,
                    error = null,
                    initial = UiState.Loading,
                )
            }
            loadNext(reset = true)
        }
    }

    fun loadMore() {
        val cur = _ui.value
        if (cur.loading || cur.endReached) return
        viewModelScope.launch { loadNext(reset = false) }
    }

    private suspend fun loadNext(reset: Boolean) {
        _ui.update { it.copy(loading = true, error = null) }
        val nextPage = if (reset) 1 else _ui.value.page + 1
        val result = repo.moduleData(id = moduleId, page = nextPage)
        result.fold(
            onSuccess = { list ->
                _ui.update {
                    val merged = if (reset) list else it.items + list
                    it.copy(
                        items = merged,
                        page = nextPage,
                        loading = false,
                        endReached = list.isEmpty(),
                        initial = UiState.Success(Unit),
                    )
                }
            },
            onFailure = { e ->
                _ui.update {
                    it.copy(
                        loading = false,
                        error = e.message,
                        initial = if (reset) UiState.Error(e.message ?: "Failed") else it.initial,
                    )
                }
            },
        )
    }

    companion object {
        fun factory(moduleId: Int, title: String, repo: MediaRepository = MediaRepository()) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ModuleViewModel(moduleId, title, repo) as T
            }
    }
}
