package com.neo.neomovies.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neo.neomovies.data.MoviesRepository
import com.neo.neomovies.data.network.dto.MediaDetailsDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val details: MediaDetailsDto? = null,
)

class DetailsViewModel(
    private val repository: MoviesRepository,
    private val sourceId: String,
) : ViewModel() {
    private val _state = MutableStateFlow(DetailsUiState(isLoading = true))
    val state: StateFlow<DetailsUiState> = _state

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val details = repository.getDetails(sourceId)
                _state.update { it.copy(isLoading = false, error = null, details = details) }
            } catch (t: Throwable) {
                _state.update { it.copy(isLoading = false, error = t.message ?: "Ошибка загрузки") }
            }
        }
    }
}
