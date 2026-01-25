package com.neo.neomovies.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neo.neomovies.data.MoviesRepository
import com.neo.neomovies.data.network.dto.MediaDto
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val popular: List<MediaDto> = emptyList(),
    val topMovies: List<MediaDto> = emptyList(),
    val topTv: List<MediaDto> = emptyList(),
)

class HomeViewModel(
    private val repository: MoviesRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState(isLoading = true))
    val state: StateFlow<HomeUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val popularDeferred = async { repository.getPopular(1) }
                val topMoviesDeferred = async { repository.getTopMovies(1) }
                val topTvDeferred = async { repository.getTopTv(1) }

                val popular = popularDeferred.await().take(12)
                val topMovies = topMoviesDeferred.await().take(12)
                val topTv = topTvDeferred.await().take(12)

                _state.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        popular = popular,
                        topMovies = topMovies,
                        topTv = topTv,
                    )
                }
            } catch (t: Throwable) {
                _state.update { it.copy(isLoading = false, error = t.message ?: "Ошибка загрузки") }
            }
        }
    }
}
