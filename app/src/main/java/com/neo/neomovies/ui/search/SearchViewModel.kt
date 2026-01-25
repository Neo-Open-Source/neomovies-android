package com.neo.neomovies.ui.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neo.neomovies.data.MoviesRepository
import com.neo.neomovies.data.network.dto.MediaDto
import com.neo.neomovies.ui.util.filterValidMovies
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val page: Int = 1,
    val totalPages: Int = 1,
    val isLoading: Boolean = false,
    val isAppendLoading: Boolean = false,
    val error: String? = null,
    val items: List<MediaDto> = emptyList(),
)

class SearchViewModel(
    private val repository: MoviesRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun setQuery(value: String) {
        _state.update { it.copy(query = value, page = 1) }
        scheduleSearch(page = 1)
    }

    fun loadNextPage() {
        val s = state.value
        if (s.isLoading || s.isAppendLoading || s.page >= s.totalPages) return
        val nextPage = s.page + 1
        _state.update { it.copy(page = nextPage) }
        scheduleSearch(page = nextPage, append = true)
    }

    fun setPage(page: Int) {
        val total = state.value.totalPages
        val clamped = page.coerceIn(1, total.coerceAtLeast(1))
        _state.update { it.copy(page = clamped) }
        scheduleSearch(page = clamped)
    }

    fun retry() {
        scheduleSearch(page = state.value.page, append = state.value.page > 1)
    }

    private fun scheduleSearch(page: Int, append: Boolean = false) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val q = state.value.query.trim()
            if (q.isBlank()) {
                _state.update { it.copy(isLoading = false, error = null, items = emptyList(), totalPages = 1) }
                return@launch
            }

            if (!append) delay(250)

            _state.update { 
                if (append) it.copy(isAppendLoading = true, error = null)
                else it.copy(isLoading = true, error = null) 
            }
            try {
                val data = repository.searchMovies(query = q, page = page)
                val rawItems = data.results
                val filteredItems = filterValidMovies(rawItems)
                Log.d(
                    "Search",
                    "search query='$q' page=$page raw=${rawItems.size} filtered=${filteredItems.size}",
                )
                _state.update {
                    val newItems = if (append) it.items + filteredItems else filteredItems
                    it.copy(
                        isLoading = false,
                        isAppendLoading = false,
                        error = null,
                        items = newItems,
                        totalPages = (data.totalPages ?: 1).coerceAtLeast(1),
                    )
                }
            } catch (t: Throwable) {
                _state.update { 
                    if (append) it.copy(isAppendLoading = false) // ошибка при пагинации не стирает экран
                    else it.copy(isLoading = false, error = t.message ?: "Ошибка поиска")
                }
            }
        }
    }
}
