package com.neo.neomovies.ui.search

import android.content.Context
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

private const val MAX_HISTORY = 5
private const val PREFS_NAME = "search_history"
private const val KEY_HISTORY = "history"

data class SearchUiState(
    val query: String = "",
    val page: Int = 1,
    val totalPages: Int = 1,
    val isLoading: Boolean = false,
    val isAppendLoading: Boolean = false,
    val error: String? = null,
    val items: List<MediaDto> = emptyList(),
    val history: List<String> = emptyList(),
)

class SearchViewModel(
    private val repository: MoviesRepository,
    private val context: Context,
) : ViewModel() {
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    private val _state = MutableStateFlow(SearchUiState(history = loadHistory()))
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    private fun loadHistory(): List<String> {
        val raw = prefs.getString(KEY_HISTORY, "") ?: ""
        return raw.split("\n").filter { it.isNotBlank() }
    }

    private fun saveHistory(history: List<String>) {
        prefs.edit().putString(KEY_HISTORY, history.joinToString("\n")).apply()
    }

    fun setQuery(value: String) {
        _state.update { it.copy(query = value, page = 1) }
        scheduleSearch(page = 1)
    }

    fun selectHistory(query: String) {
        _state.update { it.copy(query = query, page = 1) }
        scheduleSearch(page = 1, saveToHistory = false)
    }

    fun removeHistory(query: String) {
        val updated = _state.value.history - query
        _state.update { it.copy(history = updated) }
        saveHistory(updated)
    }

    fun clearHistory() {
        _state.update { it.copy(history = emptyList()) }
        saveHistory(emptyList())
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

    private fun scheduleSearch(page: Int, append: Boolean = false, saveToHistory: Boolean = true) {
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
                    val newHistory = if (saveToHistory && !append && filteredItems.isNotEmpty()) {
                        (listOf(q) + it.history.filter { h -> h != q }).take(MAX_HISTORY)
                    } else {
                        it.history
                    }
                    if (saveToHistory && !append && filteredItems.isNotEmpty()) {
                        saveHistory(newHistory)
                    }
                    val newItems = if (append) it.items + filteredItems else filteredItems
                    it.copy(
                        isLoading = false,
                        isAppendLoading = false,
                        error = null,
                        items = newItems,
                        totalPages = (data.effectiveTotalPages).coerceAtLeast(1),
                        history = newHistory,
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    if (append) it.copy(isAppendLoading = false)
                    else it.copy(isLoading = false, error = t.message ?: "Ошибка поиска")
                }
            }
        }
    }
}
