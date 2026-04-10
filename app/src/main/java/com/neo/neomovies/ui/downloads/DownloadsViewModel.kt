package com.neo.neomovies.ui.downloads

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neo.neomovies.downloads.DownloadEntry
import com.neo.neomovies.downloads.DownloadType
import com.neo.neomovies.downloads.DownloadsStore
import com.neo.neomovies.downloads.DownloadUtil
import com.neo.neomovies.downloads.MediaNameParser
import androidx.media3.exoplayer.offline.Download
import com.neo.neomovies.downloads.CollapsDownloadQueue
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

sealed class DownloadGroupItem {
    data class MovieItem(val entry: DownloadEntry) : DownloadGroupItem()
    data class ShowItem(
        val showId: String,
        val title: String,
        val posterUrl: String?,
        val seasons: List<DownloadedSeason>,
    ) : DownloadGroupItem()
}

data class DownloadedSeason(
    val seasonNumber: Int,
    val episodes: List<DownloadEntry>,
)

data class DownloadsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val movies: List<DownloadEntry> = emptyList(),
    val shows: List<DownloadGroupItem.ShowItem> = emptyList(),
    val active: List<Download> = emptyList(),
    /** downloadId -> 0..100 for our OkHttp downloads */
    val collapsProgress: Map<String, Int> = emptyMap(),
)

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {
    private val store = DownloadsStore(application)
    private val downloadManager = DownloadUtil.getDownloadManager(application)

    private val _state = MutableStateFlow(DownloadsUiState(isLoading = true))
    val state: StateFlow<DownloadsUiState> = _state

    init {
        refresh()
        pollDownloads()
        viewModelScope.launch {
            CollapsDownloadQueue.state.collect { queueState ->
                _state.update { it.copy(collapsProgress = queueState.progress) }
                refresh()
            }
        }
    }

    fun deleteEntry(entry: DownloadEntry) {
        store.removeById(entry.id)
        refresh()
    }

    fun deleteEntries(entries: List<DownloadEntry>) {
        entries.forEach { store.removeById(it.id) }
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                store.loadAll()
            }.onSuccess { list ->
                val normalized = list.map { entry ->
                    if (entry.seasonNumber == null || entry.episodeNumber == null) {
                        val se = MediaNameParser.parseSeasonEpisode(entry.title)
                        if (se != null) {
                            entry.copy(
                                type = DownloadType.EPISODE,
                                seasonNumber = se.first,
                                episodeNumber = se.second,
                            )
                        } else {
                            entry
                        }
                    } else {
                        entry
                    }
                }

                val movies = normalized.filter { it.type == DownloadType.MOVIE }
                val episodes = normalized.filter { it.type == DownloadType.EPISODE && !it.showId.isNullOrBlank() }

                val shows = episodes.groupBy { it.showId!! }
                    .map { (showId, eps) ->
                        val showTitle = eps.firstOrNull()?.showTitle?.takeIf { it.isNotBlank() } ?: showId
                        val poster = eps.firstOrNull()?.posterUrl
                        val seasons = eps.groupBy { it.seasonNumber ?: 0 }
                            .filterKeys { it > 0 }
                            .map { (season, seasonEpisodes) ->
                                DownloadedSeason(
                                    seasonNumber = season,
                                    episodes = seasonEpisodes.sortedBy { it.episodeNumber ?: 0 },
                                )
                            }
                            .sortedBy { it.seasonNumber }
                        DownloadGroupItem.ShowItem(
                            showId = showId,
                            title = showTitle,
                            posterUrl = poster,
                            seasons = seasons,
                        )
                    }
                    .sortedBy { it.title }

                _state.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        movies = movies,
                        shows = shows,
                    )
                }
            }.onFailure { t ->
                _state.update { it.copy(isLoading = false, error = t.message ?: "Ошибка загрузки") }
            }
        }
    }

    private fun pollDownloads() {
        viewModelScope.launch {
            while (true) {
                val list = downloadManager.currentDownloads
                _state.update { it.copy(active = list) }
                delay(1000L)
            }
        }
    }
}
