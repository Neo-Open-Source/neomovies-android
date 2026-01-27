package com.neo.neomovies.ui.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neo.neomovies.data.MoviesRepository
import com.neo.neomovies.data.network.dto.MediaDetailsDto
import com.neo.neomovies.data.torrents.JacredTorrent
import com.neo.neomovies.data.torrents.JacredTorrentsRepository
import com.neo.neomovies.torrserver.TorrServerManager
import com.neo.neomovies.torrserver.api.SimpleStreamingApi
import com.neo.neomovies.torrserver.api.model.TorrentFileStat
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WatchSelectorUiState(
    val isLoading: Boolean = true,
    val isSourcesLoading: Boolean = false,
    val isPlaybackResolving: Boolean = false,
    val error: String? = null,
    val details: MediaDetailsDto? = null,

    val kinopoiskId: Int? = null,

    val tvSeasons: List<Season>? = null,
    val movie: Movie? = null,

    val selectedSeasonNumber: Int? = null,
    val selectedEpisodeNumber: Int? = null,
    val selectedVoiceoverId: String? = null,
    val selectedPlaybackUrl: String? = null,
    val selectedPlaylistUrls: List<String>? = null,
    val selectedPlaylistNames: List<String>? = null,
    val selectedPlaylistStartIndex: Int? = null,
    val selectedQuality: Int? = null,
    val resolvedMaxQuality: Int? = null,

    val torrents: List<JacredTorrent> = emptyList(),
    
    val resolvingTorrent: Boolean = false,
    val torrentFiles: List<TorrentFileStat>? = null,
    val torrentHash: String? = null,
)

data class Voiceover(
    val id: String,
    val title: String,
    val playbackUrl: String,
)

data class Episode(
    val number: Int,
    val title: String,
    val voiceovers: List<Voiceover>,
)

data class Season(
    val number: Int,
    val title: String,
    val episodes: List<Episode>,
)

data class Movie(
    val title: String,
    val voiceovers: List<Voiceover>,
)

class WatchSelectorViewModel(
    private val moviesRepository: MoviesRepository,
    private val torrentsRepository: JacredTorrentsRepository,
    private val sourceId: String,
) : ViewModel() {
    private val _state = MutableStateFlow(WatchSelectorUiState())
    val state: StateFlow<WatchSelectorUiState> = _state

    init {
        load()
    }

    fun load() {
        _state.update { WatchSelectorUiState(isLoading = true) }
        viewModelScope.launch {
            try {
                val details = moviesRepository.getDetails(sourceId)
                val title = details.title?.takeIf { it.isNotBlank() }
                    ?: details.name?.takeIf { it.isNotBlank() }
                    ?: error("No title")

                val kpId = details.externalIds?.kp
                    ?: sourceId.removeSuffix(".0").removePrefix("kp_").toIntOrNull()

                val year = details.releaseDate?.take(4)?.toIntOrNull()
                val titleForSearch = details.title?.takeIf { it.isNotBlank() }
                    ?: details.name?.takeIf { it.isNotBlank() }
                    ?: details.originalTitle?.takeIf { it.isNotBlank() }

                _state.update {
                    it.copy(
                        details = details,
                        kinopoiskId = kpId,
                        isLoading = false,
                        isSourcesLoading = true,
                        error = null,
                    )
                }

                if (kpId != null) {
                    loadTorrentsByQuery("kp$kpId", fallbackTitle = titleForSearch, year = year)
                } else if (titleForSearch != null) {
                    loadTorrentsByQuery(titleForSearch, fallbackTitle = null, year = year)
                } else {
                    _state.update { it.copy(isSourcesLoading = false) }
                }
            } catch (t: Throwable) {
                _state.update { it.copy(isLoading = false, isSourcesLoading = false, error = t.message ?: "") }
            }
        }
    }

    private fun loadTorrentsByQuery(query: String, fallbackTitle: String?, year: Int?) {
        viewModelScope.launch {
            runCatching {
                val list = torrentsRepository.search(query)
                if (list.isEmpty() && fallbackTitle != null) {
                    val q = year?.let { "$fallbackTitle $it" } ?: fallbackTitle
                    torrentsRepository.search(q)
                } else {
                    list
                }
            }.onSuccess { list ->
                _state.update { it.copy(torrents = list, isSourcesLoading = false) }
            }.onFailure { t ->
                _state.update { state -> state.copy(isSourcesLoading = false, error = t.message ?: "") }
            }
        }
    }

    fun selectSeason(seasonNumber: Int) {
        _state.update {
            it.copy(
                selectedSeasonNumber = seasonNumber,
                selectedEpisodeNumber = null,
                selectedVoiceoverId = null,
                selectedPlaybackUrl = null,
                selectedQuality = null,
                resolvedMaxQuality = null,
            )
        }
    }

    fun selectEpisode(episodeNumber: Int) {
        _state.update {
            it.copy(
                selectedEpisodeNumber = episodeNumber,
                selectedVoiceoverId = null,
                selectedPlaybackUrl = null,
                selectedQuality = null,
                resolvedMaxQuality = null,
            )
        }
    }

    fun selectVoiceover(voiceoverId: String, playbackUrl: String) {
        _state.update {
            it.copy(
                selectedVoiceoverId = voiceoverId,
                selectedPlaybackUrl = playbackUrl,
                selectedQuality = null,
                resolvedMaxQuality = null,
            )
        }
    }

    fun selectQuality(quality: Int) {
        _state.update { it.copy(selectedQuality = quality) }
    }

    fun resolveAndWatch(
        onWatch: (String) -> Unit,
    ) {
        val directUrl = _state.value.selectedPlaybackUrl ?: return
        if (_state.value.isPlaybackResolving) return

        _state.update { it.copy(isPlaybackResolving = true, error = null) }
        viewModelScope.launch {
            try {
                _state.update {
                    it.copy(
                        isPlaybackResolving = false,
                        resolvedMaxQuality = null,
                    )
                }

                onWatch(directUrl)
            } catch (t: Throwable) {
                _state.update { it.copy(isPlaybackResolving = false, error = t.message ?: "") }
            }
        }
    }

    fun resolveTorrent(magnet: String, title: String) {
        _state.update { it.copy(resolvingTorrent = true, error = null) }
        viewModelScope.launch {
            try {
                val api = SimpleStreamingApi(TorrServerManager.baseUrl())
                val status = api.startStreaming(magnet, title, null)
                val hash = status.hash ?: throw RuntimeException("No hash returned")

                val readyStatus = api.waitForReady(hash)
                val allFiles = readyStatus.fileStats ?: emptyList()
                val videoFiles = allFiles.filter { file ->
                    val path = file.path?.lowercase() ?: ""
                    path.endsWith(".mkv") || path.endsWith(".mp4") || path.endsWith(".avi") ||
                            path.endsWith(".mov") || path.endsWith(".wmv") || path.endsWith(".flv") ||
                            path.endsWith(".webm")
                }

                val orderedVideoFiles = videoFiles.sortedBy { it.path ?: "" }

                if (videoFiles.isEmpty()) {
                    throw RuntimeException("No video files found")
                }

                if (videoFiles.size == 1) {
                    val url = api.getFileStreamUrl(hash, orderedVideoFiles.first().id)
                    val name = orderedVideoFiles.first().path ?: ""
                    _state.update {
                        it.copy(
                            resolvingTorrent = false,
                            selectedPlaybackUrl = url,
                            selectedPlaylistUrls = listOf(url),
                            selectedPlaylistNames = listOf(name),
                            selectedPlaylistStartIndex = 0,
                            torrentHash = hash
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            resolvingTorrent = false,
                            torrentFiles = orderedVideoFiles,
                            torrentHash = hash
                        )
                    }
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        resolvingTorrent = false,
                        error = t.message ?: "Failed to resolve torrent"
                    )
                }
            }
        }
    }

    fun selectTorrentFile(fileIndex: Int) {
        val hash = _state.value.torrentHash ?: return
        val files = _state.value.torrentFiles ?: return
        val api = SimpleStreamingApi(TorrServerManager.baseUrl())

        val playlistUrls = files.map { f -> api.getFileStreamUrl(hash, f.id) }
        val playlistNames = files.map { f -> f.path ?: "" }
        val startIndex = fileIndex.coerceIn(0, (files.size - 1).coerceAtLeast(0))
        Log.d("WatchSelectorVM", "selectTorrentFile index=$fileIndex -> startIndex=$startIndex name=${playlistNames.getOrNull(startIndex)}")
        val url = playlistUrls.getOrNull(startIndex) ?: return
        _state.update {
            it.copy(
                selectedPlaybackUrl = url,
                selectedPlaylistUrls = playlistUrls,
                selectedPlaylistNames = playlistNames,
                selectedPlaylistStartIndex = startIndex,
                torrentFiles = null,
                torrentHash = null
            )
        }
    }

    fun clearTorrentSelection() {
        _state.update {
            it.copy(
                torrentFiles = null,
                torrentHash = null,
                resolvingTorrent = false
            )
        }
    }

    fun clearSelectedPlaybackUrl() {
        _state.update {
            it.copy(
                selectedPlaybackUrl = null,
                selectedPlaylistUrls = null,
                selectedPlaylistNames = null,
                selectedPlaylistStartIndex = null,
            )
        }
    }
}
