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
    val error: String? = null,
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
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val items = when (category) {
                    CategoryType.POPULAR -> repository.getPopular(1)
                    CategoryType.TOP_MOVIES -> repository.getTopMovies(1)
                    CategoryType.TOP_TV -> repository.getTopTv(1)
                }
                _state.update { it.copy(isLoading = false, error = null, items = items) }
            } catch (t: Throwable) {
                _state.update { it.copy(isLoading = false, error = t.message ?: "Ошибка загрузки") }
            }
        }
    }
}
