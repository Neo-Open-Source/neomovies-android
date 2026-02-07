package com.neo.neomovies.ui.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.content.SharedPreferences
import com.neo.neomovies.data.MoviesRepository
import com.neo.neomovies.data.collaps.CollapsRepository
import com.neo.neomovies.data.network.dto.MediaDetailsDto
import com.neo.neomovies.data.torrents.JacredTorrent
import com.neo.neomovies.data.torrents.JacredTorrentsRepository
import com.neo.neomovies.torrserver.TorrServerManager
import com.neo.neomovies.torrserver.api.SimpleStreamingApi
import com.neo.neomovies.torrserver.api.model.TorrentFileStat
import android.util.Log
import com.neo.neomovies.ui.settings.SourceManager
import com.neo.neomovies.ui.settings.SourceMode
import com.neo.neomovies.ui.settings.PlayerEngineManager
import com.neo.neomovies.ui.settings.PlayerEngineMode
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
    val isWatched: Boolean = false,
    val watchProgressMs: Long = 0L,
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
    private val collapsRepository: CollapsRepository,
    private val context: Context,
    private val sourceId: String,
) : ViewModel() {
    private val _state = MutableStateFlow(WatchSelectorUiState())
    val state: StateFlow<WatchSelectorUiState> = _state
    
    private val watchedPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("collaps_watched", Context.MODE_PRIVATE)
    }

    init {
        load()
    }

    private fun loadCollaps(kpId: Int) {
        viewModelScope.launch {
            runCatching {
                collapsRepository.getSeasonsByKpId(kpId)
            }.onSuccess { seasons ->
                if (seasons.isEmpty()) {
                    runCatching { collapsRepository.getMovieByKpId(kpId) }
                        .onSuccess { movie ->
                            val mpd = movie?.mpdUrl
                            if (movie != null && mpd != null) {
                                _state.update { it.copy(isPlaybackResolving = true, error = null) }
                                viewModelScope.launch {
                                    try {
                                        val preferHlsForMpv =
                                            PlayerEngineManager.getMode(context) == PlayerEngineMode.MPV

                                        val shouldUseHls =
                                            preferHlsForMpv ||
                                                runCatching { collapsRepository.dashContainsAv1(mpd) }.getOrDefault(false)

                                        val rewritten =
                                            if (shouldUseHls && !movie.hlsUrl.isNullOrBlank()) {
                                                if (preferHlsForMpv) {
                                                    movie.hlsUrl
                                                } else {
                                                    collapsRepository.buildRewrittenHlsUri(kpId, 0, 0, movie.hlsUrl, movie.voices, movie.subtitles)
                                                }
                                            } else {
                                                collapsRepository.buildRewrittenMpdUri(kpId, 0, 0, mpd, movie.voices, movie.subtitles)
                                            }
                                        _state.update {
                                            it.copy(
                                                isSourcesLoading = false,
                                                isPlaybackResolving = false,
                                                movie = Movie(title = "", voiceovers = emptyList()),
                                                selectedPlaybackUrl = rewritten,
                                            )
                                        }
                                    } catch (t: Throwable) {
                                        _state.update { it.copy(isSourcesLoading = false, isPlaybackResolving = false, error = t.message ?: "") }
                                    }
                                }
                            } else {
                                _state.update { it.copy(isSourcesLoading = false) }
                            }
                        }
                        .onFailure { t ->
                            _state.update { it.copy(isSourcesLoading = false, error = t.message ?: "") }
                        }
                    return@onSuccess
                }

                val mapped = seasons.map { s ->
                    Season(
                        number = s.season,
                        title = "${s.season}",
                        episodes = s.episodes.map { e ->
                            val voiceovers =
                                if (e.mpdUrl != null && e.voices.isNotEmpty()) {
                                    e.voices.mapIndexed { idx, name ->
                                        Voiceover(
                                            id = "collaps:${s.season}:${e.episode}:$idx",
                                            title = name,
                                            playbackUrl = e.mpdUrl,
                                        )
                                    }
                                } else if (e.mpdUrl != null) {
                                    listOf(
                                        Voiceover(
                                            id = "collaps:${s.season}:${e.episode}:0",
                                            title = "Collaps",
                                            playbackUrl = e.mpdUrl,
                                        )
                                    )
                                } else if (e.hlsUrl != null) {
                                    listOf(
                                        Voiceover(
                                            id = "collaps:${s.season}:${e.episode}:0",
                                            title = "Collaps",
                                            playbackUrl = e.hlsUrl,
                                        )
                                    )
                                } else {
                                    emptyList()
                                }

                            val watchedKey = "kp_${kpId}_s${s.season}_e${e.episode}"
                            val watchProgressMs = watchedPrefs.getLong(watchedKey, 0L)
                            val isWatched = watchedPrefs.getBoolean("${watchedKey}_watched", false)
                            Episode(
                                number = e.episode,
                                title = "${e.episode}",
                                voiceovers = voiceovers,
                                isWatched = isWatched,
                                watchProgressMs = watchProgressMs,
                            )
                        },
                    )
                }

                _state.update {
                    it.copy(
                        tvSeasons = mapped,
                        isSourcesLoading = false,
                    )
                }
            }.onFailure { t ->
                _state.update { state -> state.copy(isSourcesLoading = false, error = t.message ?: "") }
            }
        }
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

                when (SourceManager.getMode(context)) {
                    SourceMode.COLLAPS -> {
                        if (kpId != null) {
                            loadCollaps(kpId)
                        } else {
                            _state.update { it.copy(isSourcesLoading = false) }
                        }
                    }
                    SourceMode.TORRENTS -> {
                        if (kpId != null) {
                            loadTorrentsByQuery("kp$kpId", fallbackTitle = titleForSearch, year = year)
                        } else if (titleForSearch != null) {
                            loadTorrentsByQuery(titleForSearch, fallbackTitle = null, year = year)
                        } else {
                            _state.update { it.copy(isSourcesLoading = false) }
                        }
                    }
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

        val seasonNumber = _state.value.selectedSeasonNumber ?: return
        val season = _state.value.tvSeasons?.firstOrNull { it.number == seasonNumber } ?: return
        val episode = season.episodes.firstOrNull { it.number == episodeNumber } ?: return
        val voiceover = episode.voiceovers.firstOrNull() ?: return
        selectVoiceover(voiceover.id, voiceover.playbackUrl)
    }

    fun selectVoiceover(voiceoverId: String, playbackUrl: String) {
        val kpId = _state.value.kinopoiskId
        val s = _state.value.selectedSeasonNumber
        val e = _state.value.selectedEpisodeNumber

        // If this is a Collaps DASH voice, rewrite mpd and play via content:// so external players can also open it.
        if (kpId != null && s != null && e != null && voiceoverId.startsWith("collaps:")) {
            _state.update { it.copy(isPlaybackResolving = true, error = null) }
            viewModelScope.launch {
                try {
                    val voiceIndex = voiceoverId.substringAfterLast(':').toIntOrNull() ?: 0
                    val seasons = collapsRepository.getSeasonsByKpId(kpId)
                    val season = seasons.firstOrNull { it.season == s }
                    val episodes = season?.episodes.orEmpty()
                    val ep = episodes.firstOrNull { it.episode == e }
                    if (ep == null || episodes.isEmpty()) {
                        _state.update { it.copy(isPlaybackResolving = false, error = "No episode") }
                        return@launch
                    }

                    val preferHlsForMpv =
                        PlayerEngineManager.getMode(context) == PlayerEngineMode.MPV

                    val playlistUrls = episodes.mapNotNull { episode ->
                        val mpd = episode.mpdUrl
                        val shouldUseHls =
                            preferHlsForMpv ||
                                (mpd != null && runCatching { collapsRepository.dashContainsAv1(mpd) }.getOrDefault(false))

                        when {
                            shouldUseHls && !episode.hlsUrl.isNullOrBlank() -> {
                                if (preferHlsForMpv) {
                                    episode.hlsUrl
                                } else {
                                    collapsRepository.buildRewrittenHlsUri(
                                        kpId,
                                        episode.season,
                                        episode.episode,
                                        episode.hlsUrl,
                                        episode.voices,
                                        episode.subtitles,
                                    )
                                }
                            }
                            mpd != null ->
                                collapsRepository.buildRewrittenMpdUri(
                                    kpId,
                                    episode.season,
                                    episode.episode,
                                    mpd,
                                    episode.voices,
                                    episode.subtitles,
                                )
                            else -> null
                        }
                    }

                    if (playlistUrls.isEmpty()) {
                        _state.update { it.copy(isPlaybackResolving = false, error = "No mpd/hls url") }
                        return@launch
                    }

                    val playlistNames = episodes.map { episode ->
                        val voiceTitle = episode.voices.getOrNull(voiceIndex) ?: "Collaps"
                        "S%02dE%02d • %s".format(episode.season, episode.episode, voiceTitle)
                    }

                    val startIndex = episodes.indexOfFirst { it.episode == e }.coerceAtLeast(0)

                    _state.update {
                        it.copy(
                            isPlaybackResolving = false,
                            selectedVoiceoverId = voiceoverId,
                            selectedPlaybackUrl = playlistUrls.getOrNull(startIndex),
                            selectedPlaylistUrls = playlistUrls,
                            selectedPlaylistNames = playlistNames,
                            selectedPlaylistStartIndex = startIndex,
                            selectedQuality = null,
                            resolvedMaxQuality = null,
                        )
                    }
                } catch (t: Throwable) {
                    _state.update { it.copy(isPlaybackResolving = false, error = t.message ?: "") }
                }
            }
            return
        }

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

    fun updateEpisodeWatchProgress(kpId: Int?, season: Int?, episode: Int?, positionMs: Long, durationMs: Long) {
        if (kpId == null || season == null || episode == null) return
        val watchedKey = "kp_${kpId}_s${season}_e${episode}"
        val watchedThresholdMs = if (durationMs > 0) {
            val percentThreshold = (durationMs * 0.85f).toLong()
            val creditsThreshold = durationMs - 180_000L
            maxOf(percentThreshold, creditsThreshold)
        } else {
            Long.MAX_VALUE
        }
        watchedPrefs.edit()
            .putLong(watchedKey, positionMs)
            .putBoolean("${watchedKey}_watched", durationMs > 0 && positionMs >= watchedThresholdMs)
            .putInt("kp_${kpId}_last_season", season)
            .putInt("kp_${kpId}_last_episode", episode)
            .putLong("kp_${kpId}_last_position", positionMs)
            .putLong("kp_${kpId}_last_duration", durationMs)
            .apply()
        
        // Refresh state to update UI
        loadCollaps(kpId)
    }

    fun refreshCollapsProgress() {
        val kpId = _state.value.kinopoiskId ?: return
        if (SourceManager.getMode(context) != SourceMode.COLLAPS) return
        loadCollaps(kpId)
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