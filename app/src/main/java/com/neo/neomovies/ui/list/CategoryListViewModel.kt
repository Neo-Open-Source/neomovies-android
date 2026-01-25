package com.neo.neomovies.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neo.neomovies.data.MoviesRepository
import com.neo.neomovies.data.network.dto.MediaDto
import com.neo.neomovies.ui.navigation.CategoryType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CategoryListUiState(
    val isLoading: Boolean = false,
    val isAppendLoading: Boolean = false,
    val error: String? = null,
    val page: Int = 1,
    val totalPages: Int = 1,
    val items: List<MediaDto> = emptyList(),
)

class CategoryListViewModel(
    private val repository: MoviesRepository,
    private val category: CategoryType,
) : ViewModel() {
    private val _state = MutableStateFlow(CategoryListUiState(isLoading = true))
    val state: StateFlow<CategoryListUiState> = _state

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, isAppendLoading = false, error = null, page = 1) }
        viewModelScope.launch {
            try {
                val data = when (category) {
                    CategoryType.POPULAR -> repository.getPopularPage(1)
                    CategoryType.TOP_MOVIES -> repository.getTopMoviesPage(1)
                    CategoryType.TOP_TV -> repository.getTopTvPage(1)
                }
                _state.update {
                    it.copy(
                        isLoading = false,
                        isAppendLoading = false,
                        error = null,
                        page = 1,
                        totalPages = (data.totalPages ?: 1).coerceAtLeast(1),
                        items = data.results,
                    )
                }
            } catch (t: Throwable) {
                _state.update { it.copy(isLoading = false, error = t.message ?: "Ошибка загрузки") }
            }
        }
    }

    fun loadNextPage() {
        val s = state.value
        if (s.isLoading || s.isAppendLoading || s.page >= s.totalPages) return
        val nextPage = s.page + 1
        _state.update { it.copy(isAppendLoading = true, error = null, page = nextPage) }
        viewModelScope.launch {
            try {
                val data = when (category) {
                    CategoryType.POPULAR -> repository.getPopularPage(nextPage)
                    CategoryType.TOP_MOVIES -> repository.getTopMoviesPage(nextPage)
                    CategoryType.TOP_TV -> repository.getTopTvPage(nextPage)
                }
                _state.update {
                    it.copy(
                        isAppendLoading = false,
                        error = null,
                        totalPages = (data.totalPages ?: it.totalPages).coerceAtLeast(1),
                        items = it.items + data.results,
                    )
                }
            } catch (t: Throwable) {
                _state.update { it.copy(isAppendLoading = false) }
            }
        }
    }
}
