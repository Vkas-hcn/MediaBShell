package com.hdvideoplay.media.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hdvideoplay.media.data.dto.HomeModuleDto
import com.hdvideoplay.media.data.repo.MediaRepository
import com.hdvideoplay.media.track.TrackEvents
import com.hdvideoplay.media.ui.components.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repo: MediaRepository = MediaRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<HomeModuleDto>>>(UiState.Loading)
    val state: StateFlow<UiState<List<HomeModuleDto>>> = _state.asStateFlow()

    // ---- home_view / home_module_show 去重（按数据列表实例身份） ----
    private var homeViewReportedForData: List<HomeModuleDto>? = null
    private var modulesReportedForData: List<HomeModuleDto>? = null
    private val reportedModuleKeys = mutableSetOf<String>()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            // home_module_request：首页模块资源请求发起时上报。
            TrackEvents.homeModuleRequest()
            val result = repo.homeData()
            _state.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "Unknown error") },
            )
        }
    }

    /** home_view：首页内容真正展示成功后，同一轮数据只报一次。 */
    fun reportHomeViewOnce(data: List<HomeModuleDto>) {
        if (homeViewReportedForData === data) return
        homeViewReportedForData = data
        TrackEvents.homeView()
    }

    /**
     * home_module_show：模块行真正进入组合（即将展示）时上报。
     * 同一轮数据内每模块只报一次；刷新出新数据后重置；滚动回看/tab 切回不重报。
     */
    fun reportModuleShownOnce(module: HomeModuleDto, index: Int, data: List<HomeModuleDto>) {
        if (modulesReportedForData !== data) {
            modulesReportedForData = data
            reportedModuleKeys.clear()
        }
        if (!reportedModuleKeys.add("${module.id}|$index")) return
        TrackEvents.homeModuleShow(module.id, module.moduleName, index)
    }

    companion object {
        fun factory(repo: MediaRepository = MediaRepository()) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(repo) as T
            }
    }
}
