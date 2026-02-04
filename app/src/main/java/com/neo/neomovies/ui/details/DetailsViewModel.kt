package com.neo.neomovies.ui.details

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neo.neomovies.NeoMoviesApplication
import com.neo.neomovies.data.FavoritesRepository
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
    val isFavorite: Boolean? = null,
    val favoriteMediaId: String? = null,
    val favoriteMediaType: String? = null,
    val isFavoriteLoading: Boolean = false,
    val isFavoriteUpdating: Boolean = false,
    val watchedSummary: WatchedSummary? = null,
)

data class WatchedSummary(
    val watchedCount: Int,
    val lastSeason: Int,
    val lastEpisode: Int,
    val lastPosition: Long,
    val lastDuration: Long,
)

class DetailsViewModel(
    private val repository: MoviesRepository,
    private val favoritesRepository: FavoritesRepository,
    private val sourceId: String,
) : ViewModel() {
    private val _state = MutableStateFlow(DetailsUiState(isLoading = true))
    val state: StateFlow<DetailsUiState> = _state

    init {
        load()
    }

    fun load() {
        _state.update {
            it.copy(
                isLoading = true,
                error = null,
                details = null,
                isFavorite = null,
                favoriteMediaId = null,
                favoriteMediaType = null,
                isFavoriteLoading = false,
                isFavoriteUpdating = false,
                watchedSummary = null,
            )
        }
        viewModelScope.launch {
            try {
                val details = repository.getDetails(sourceId)
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        details = details,
                        watchedSummary = loadWatchedSummary(details),
                    )
                }

                if (hasToken()) {
                    refreshIsFavorite(details)
                }
            } catch (t: Throwable) {
                _state.update { it.copy(isLoading = false, error = t.message ?: "") }
            }
        }
    }

    fun toggleFavorite() {
        val details = _state.value.details ?: return
        if (!hasToken()) return
        if (_state.value.isFavoriteUpdating) return

        val mediaId = _state.value.favoriteMediaId ?: favoriteKey(details)?.first ?: return
        val mediaType = _state.value.favoriteMediaType ?: favoriteKey(details)?.second ?: return

        _state.update { it.copy(isFavoriteUpdating = true, error = null) }
        viewModelScope.launch {
            try {
                val currentlyFavorite = _state.value.isFavorite == true
                if (currentlyFavorite) {
                    favoritesRepository.removeFromFavorites(mediaId = mediaId, mediaType = mediaType)
                } else {
                    favoritesRepository.addToFavorites(mediaId = mediaId, mediaType = mediaType)
                }
                _state.update { it.copy(isFavorite = !currentlyFavorite, isFavoriteUpdating = false) }
            } catch (t: Throwable) {
                _state.update { it.copy(isFavoriteUpdating = false, error = t.message ?: "") }
            }
        }
    }

    fun refreshWatchedSummary() {
        val details = _state.value.details ?: return
        _state.update { it.copy(watchedSummary = loadWatchedSummary(details)) }
    }

    private fun refreshIsFavorite(details: MediaDetailsDto) {
        val (mediaId, suggestedType) = favoriteKey(details) ?: return
        _state.update { it.copy(isFavoriteLoading = true) }
        viewModelScope.launch {
            try {
                val typeCandidates = when (suggestedType) {
                    "tv" -> listOf("tv", "movie")
                    else -> listOf("movie", "tv")
                }

                var resolvedType: String? = null
                var isFav = false
                for (candidate in typeCandidates) {
                    if (favoritesRepository.isFavorite(mediaId = mediaId, mediaType = candidate)) {
                        resolvedType = candidate
                        isFav = true
                        break
                    }
                }

                _state.update {
                    it.copy(
                        isFavorite = isFav,
                        favoriteMediaId = mediaId,
                        favoriteMediaType = resolvedType ?: typeCandidates.firstOrNull(),
                        isFavoriteLoading = false,
                    )
                }
            } catch (_: Throwable) {
                _state.update {
                    it.copy(
                        isFavorite = null,
                        favoriteMediaId = mediaId,
                        favoriteMediaType = suggestedType,
                        isFavoriteLoading = false,
                    )
                }
            }
        }
    }

    private fun favoriteKey(details: MediaDetailsDto): Pair<String, String>? {
        val cleaned = sourceId.removeSuffix(".0")
        val id = details.externalIds?.kp?.toString()
            ?: cleaned.removePrefix("kp_").takeIf { it.isNotBlank() }
        val type = when (details.type?.lowercase()) {
            "tv", "series" -> "tv"
            else -> "movie"
        }
        if (id.isNullOrBlank()) return null
        return id to type
    }

    private fun hasToken(): Boolean {
        val ctx = NeoMoviesApplication.instance.applicationContext
        val prefs = ctx.getSharedPreferences("auth", Context.MODE_PRIVATE)
        return !prefs.getString("token", null).isNullOrBlank()
    }

    private fun loadWatchedSummary(details: MediaDetailsDto): WatchedSummary? {
        val kpId = details.externalIds?.kp?.toString()
            ?: sourceId.removeSuffix(".0").removePrefix("kp_")
        if (kpId.isBlank()) return null
        val ctx = NeoMoviesApplication.instance.applicationContext
        val watchedPrefs = ctx.getSharedPreferences("collaps_watched", Context.MODE_PRIVATE)
        val watchedCount = watchedPrefs.all
            .filterKeys { it.startsWith("kp_${kpId}_s") && it.endsWith("_watched") }
            .count { it.value == true }
        val lastSeason = watchedPrefs.getInt("kp_${kpId}_last_season", -1)
        val lastEpisode = watchedPrefs.getInt("kp_${kpId}_last_episode", -1)
        val lastPosition = watchedPrefs.getLong("kp_${kpId}_last_position", 0L)
        val lastDuration = watchedPrefs.getLong("kp_${kpId}_last_duration", 0L)
        return WatchedSummary(
            watchedCount = watchedCount,
            lastSeason = lastSeason,
            lastEpisode = lastEpisode,
            lastPosition = lastPosition,
            lastDuration = lastDuration,
        )
    }
}
