package com.hdvideoplay.media.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hdvideoplay.media.data.dto.MediaItemDto
import com.hdvideoplay.media.data.repo.MediaRepository
import com.hdvideoplay.media.ui.components.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUi(
    val keyword: String = "",
    /** 1 = Movies, 2 = TV — used for Trending / discover. */
    val category: Int = 1,
    val mode: Mode = Mode.Discover,
    val listState: UiState<List<MediaItemDto>> = UiState.Loading,
    /** Options from filter/options — empty until loaded. */
    val genres: List<String> = emptyList(),
    val releaseYears: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    /** Single-select per group; null = none. */
    val selectedGenre: String? = null,
    val selectedYear: String? = null,
    val selectedCountry: String? = null,
    /** When true, year row shows full list past the recent window. */
    val yearsExpanded: Boolean = false,
) {
    enum class Mode { Discover, Search }

    /** Empty-query discover: Movies/TV tabs + filter chips. */
    val showTrendingTabs: Boolean
        get() = mode == Mode.Discover && keyword.isBlank()

    val showFilterChips: Boolean
        get() = showTrendingTabs

    val hasActiveFilters: Boolean
        get() = selectedGenre != null || selectedYear != null || selectedCountry != null
}

class SearchViewModel(
    private val repo: MediaRepository = MediaRepository(),
) : ViewModel() {

    private val _ui = MutableStateFlow(SearchUi())
    val ui: StateFlow<SearchUi> = _ui.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            loadFilterOptions()
            loadDiscover()
        }
    }

    fun onKeywordChange(value: String) {
        _ui.update { it.copy(keyword = value) }
        if (value.isBlank() && _ui.value.mode == SearchUi.Mode.Search) {
            loadDiscover()
        }
    }

    fun setCategory(category: Int) {
        _ui.update { it.copy(category = category) }
        if (_ui.value.keyword.isBlank()) {
            loadDiscover()
        }
    }

    /** Toggle single-select genre; same value again clears. */
    fun setGenre(genre: String?) {
        _ui.update {
            val next = if (it.selectedGenre == genre) null else genre
            it.copy(selectedGenre = next)
        }
        if (_ui.value.keyword.isBlank()) loadDiscover()
    }

    fun setYear(year: String?) {
        _ui.update {
            val next = if (it.selectedYear == year) null else year
            it.copy(selectedYear = next)
        }
        if (_ui.value.keyword.isBlank()) loadDiscover()
    }

    fun setCountry(country: String?) {
        _ui.update {
            val next = if (it.selectedCountry == country) null else country
            it.copy(selectedCountry = next)
        }
        if (_ui.value.keyword.isBlank()) loadDiscover()
    }

    fun expandYears() {
        _ui.update { it.copy(yearsExpanded = true) }
    }

    fun submitSearch() {
        val kw = _ui.value.keyword.trim()
        if (kw.isEmpty()) {
            loadDiscover()
            return
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _ui.update { it.copy(mode = SearchUi.Mode.Search, listState = UiState.Loading) }
            val result = repo.search(kw)
            _ui.update {
                it.copy(
                    listState = result.fold(
                        onSuccess = { page -> UiState.Success(page.results) },
                        onFailure = { e -> UiState.Error(e.message ?: "Search failed") },
                    ),
                )
            }
        }
    }

    /** Landing / empty-query: discover with optional genre/year/country. */
    fun loadTrending() = loadDiscover()

    fun loadDiscover() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val cur = _ui.value
            _ui.update { it.copy(mode = SearchUi.Mode.Discover, listState = UiState.Loading) }
            val result = repo.discover(
                category = cur.category,
                genre = cur.selectedGenre,
                releaseYear = cur.selectedYear,
                country = cur.selectedCountry,
            )
            val folded = result.fold(
                onSuccess = { page -> page.results },
                onFailure = { emptyList() },
            )
            // With active filters, empty means empty — do not fall back to daily/home.
            if (cur.hasActiveFilters) {
                _ui.update {
                    it.copy(
                        listState = result.fold(
                            onSuccess = { page -> UiState.Success(page.results) },
                            onFailure = { e ->
                                UiState.Error(e.message ?: "Discover failed")
                            },
                        ),
                    )
                }
                return@launch
            }
            if (folded.isNotEmpty() || result.isFailure) {
                _ui.update {
                    it.copy(
                        listState = result.fold(
                            onSuccess = { page -> UiState.Success(page.results) },
                            onFailure = { e ->
                                UiState.Error(e.message ?: "Discover failed")
                            },
                        ),
                    )
                }
                if (result.isFailure) {
                    fallbackTrending()
                }
            } else {
                fallbackTrending()
            }
        }
    }

    private suspend fun loadFilterOptions() {
        val result = repo.filterOptions()
        result.onSuccess { opts ->
            _ui.update {
                it.copy(
                    genres = opts.genres,
                    releaseYears = opts.releaseYears,
                    countries = pinCommonCountries(opts.countries),
                )
            }
        }
    }

    private suspend fun fallbackTrending() {
        val daily = repo.daily()
        if (daily.isSuccess && daily.getOrNull().orEmpty().isNotEmpty()) {
            _ui.update { it.copy(listState = UiState.Success(daily.getOrNull().orEmpty())) }
            return
        }
        val home = repo.homeData()
        home.onSuccess { modules ->
            val items = modules.flatMap { it.data }.distinctBy { it.localId() }
            if (items.isNotEmpty()) {
                _ui.update { it.copy(listState = UiState.Success(items)) }
                return
            }
        }
        // Keep prior error if any; otherwise empty success
        if (_ui.value.listState !is UiState.Success) {
            _ui.update {
                it.copy(listState = UiState.Success(emptyList()))
            }
        }
    }

    companion object {
        /** Common ISO codes pinned to front of country chip row. */
        val PINNED_COUNTRIES = listOf("US", "GB", "JP", "KR", "CN", "IN")

        /** Recent-year window before 「更早」 expand. */
        const val RECENT_YEAR_COUNT = 20

        fun pinCommonCountries(all: List<String>): List<String> {
            if (all.isEmpty()) return all
            val pinned = PINNED_COUNTRIES.filter { code -> all.any { it.equals(code, ignoreCase = true) } }
                .map { code -> all.first { it.equals(code, ignoreCase = true) } }
            val rest = all.filter { c -> pinned.none { it.equals(c, ignoreCase = true) } }
            return pinned + rest
        }

        fun factory(repo: MediaRepository = MediaRepository()) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SearchViewModel(repo) as T
            }
    }
}
