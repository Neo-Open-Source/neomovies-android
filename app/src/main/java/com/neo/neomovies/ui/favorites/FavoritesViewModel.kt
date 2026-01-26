package com.neo.neomovies.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neo.neomovies.data.FavoritesRepository
import com.neo.neomovies.data.network.dto.FavoriteDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FavoritesUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val items: List<FavoriteDto> = emptyList(),
)

class FavoritesViewModel(
    private val repository: FavoritesRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(FavoritesUiState(isLoading = true))
    val state: StateFlow<FavoritesUiState> = _state

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val items = repository.getFavorites()
                _state.update { it.copy(isLoading = false, items = items, error = null) }
            } catch (t: Throwable) {
                _state.update { it.copy(isLoading = false, error = t.message ?: "", items = emptyList()) }
            }
        }
    }
}
