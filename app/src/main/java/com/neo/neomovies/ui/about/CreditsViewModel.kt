package com.neo.neomovies.ui.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neo.neomovies.data.SupportRepository
import com.neo.neomovies.data.network.dto.SupportItemDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreditsState(
    val isLoading: Boolean = false,
    val items: List<SupportItemDto> = emptyList(),
    val error: String? = null,
    val fromCache: Boolean = false,
)

class CreditsViewModel(
    private val repository: SupportRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CreditsState())
    val state: StateFlow<CreditsState> = _state

    init {
        refresh()
    }

    fun refresh() {
        val cached = repository.getCached()
        if (!cached.isNullOrEmpty()) {
            _state.update { it.copy(items = cached, fromCache = true) }
        }

        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.fetch() }
                .onSuccess { items ->
                    _state.update { it.copy(isLoading = false, items = items, fromCache = false, error = null) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message ?: "Ошибка") }
                }
        }
    }
}
