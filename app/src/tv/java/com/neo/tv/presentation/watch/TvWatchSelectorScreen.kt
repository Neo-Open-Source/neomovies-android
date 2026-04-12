package com.neo.tv.presentation.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.neo.neomovies.R
import com.neo.neomovies.data.alloha.AllohaSessionHolder
import com.neo.neomovies.data.alloha.AllohaSessionManager
import com.neo.neomovies.data.torrents.JacredTorrent
import com.neo.neomovies.ui.home.collectAsStateWithLifecycleCompat
import com.neo.neomovies.ui.settings.SourceManager
import com.neo.neomovies.ui.settings.SourceMode
import com.neo.neomovies.ui.util.normalizeImageUrl
import com.neo.neomovies.ui.watch.WatchSelectorViewModel
import com.neo.tv.presentation.common.TvActionButton
import com.neo.tv.presentation.common.TvScreenScaffold
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvWatchSelectorScreen(
    sourceId: String,
    onBack: () -> Unit,
    onWatch: (ArrayList<String>, ArrayList<String>, Int, String?, Int?, (Int, Int, Int, Long, Long) -> Unit) -> Unit,
) {
    val viewModel: WatchSelectorViewModel = koinViewModel(parameters = { parametersOf(sourceId) })
    val state by viewModel.state.collectAsStateWithLifecycleCompat()
    val context = LocalContext.current
    val sourceMode = remember { SourceManager.getMode(context) }

    val effectiveTitle = state.details?.title?.takeIf { it.isNotBlank() }
        ?: state.details?.name?.takeIf { it.isNotBlank() }

    val episodeProgressCallback: (Int, Int, Int, Long, Long) -> Unit = { kpId, season, episode, positionMs, durationMs ->
        viewModel.updateEpisodeWatchProgress(kpId, season, episode, positionMs, durationMs)
    }

    // Alloha session manager (only for ALLOHA source)
    val allohaSession = remember {
        if (sourceMode == SourceMode.ALLOHA) AllohaSessionManager(context) else null
    }
    var allohaParsingIframe by remember { mutableStateOf<String?>(null) }
    var allohaParsingStatus by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            // Only release if we're not going to the player (session is still needed there)
            if (AllohaSessionHolder.session !== allohaSession) {
                allohaSession?.release()
            }
        }
    }

    LaunchedEffect(
        state.selectedPlaybackUrl,
        state.selectedPlaylistUrls,
        state.selectedPlaylistNames,
        state.selectedPlaylistStartIndex,
    ) {
        val playlist = state.selectedPlaylistUrls
        val names = state.selectedPlaylistNames
        val startIndex = state.selectedPlaylistStartIndex
        val isAllohaVoiceover = state.selectedVoiceoverId?.startsWith("alloha:") == true

        when {
            isAllohaVoiceover && state.selectedPlaybackUrl != null && allohaSession != null -> {
                val iframeUrl = state.selectedPlaybackUrl!!
                allohaParsingIframe = iframeUrl
                allohaParsingStatus = context.getString(R.string.alloha_parsing_stream)
                viewModel.clearSelectedPlaybackUrl()

                val seasonNum = state.selectedSeasonNumber
                val episodeNum = state.selectedEpisodeNumber
                val translationName = state.allohaTranslationName ?: ""
                val episodeName = if (seasonNum != null && episodeNum != null) {
                    val se = "S%02dE%02d".format(seasonNum, episodeNum)
                    if (translationName.isNotBlank()) "$se - $translationName" else se
                } else {
                    translationName.ifBlank { effectiveTitle ?: "" }
                }

                // Gather episode translations for in-player switching
                val currentEpisodeVoiceovers = run {
                    val s = state.selectedSeasonNumber
                    val e = state.selectedEpisodeNumber
                    if (s != null && e != null) {
                        state.tvSeasons?.firstOrNull { it.number == s }
                            ?.episodes?.firstOrNull { it.number == e }
                            ?.voiceovers.orEmpty()
                    } else {
                        state.movie?.voiceovers.orEmpty()
                    }
                }

                allohaSession.ensureInitialized()
                AllohaSessionHolder.session = allohaSession

                allohaSession.onStreamReady = { _, m3u8Url ->
                    allohaSession.hlsProxy?.updateMasterUrl(m3u8Url)
                    val proxyUrl = allohaSession.proxyMasterUrl
                    allohaParsingIframe = null
                    allohaParsingStatus = null

                    AllohaSessionHolder.setTranslations(
                        names = currentEpisodeVoiceovers.map { it.title },
                        urls = currentEpisodeVoiceovers.map { it.playbackUrl },
                        current = translationName,
                    )

                    onWatch(
                        arrayListOf(proxyUrl),
                        arrayListOf(episodeName),
                        0,
                        effectiveTitle,
                        state.kinopoiskId,
                        episodeProgressCallback,
                    )
                }
                allohaSession.onError = { error ->
                    allohaParsingIframe = null
                    allohaParsingStatus = "Error: $error"
                }
                allohaSession.startSession(iframeUrl)
            }
            playlist != null && names != null && startIndex != null -> {
                val safePlaylist = ArrayList(playlist.filterNotNull())
                val safeNames = ArrayList(names.map { it ?: "" })
                if (safePlaylist.isNotEmpty()) {
                    onWatch(
                        safePlaylist,
                        safeNames,
                        startIndex.coerceIn(0, safePlaylist.size - 1),
                        state.details?.title,
                        state.kinopoiskId,
                        episodeProgressCallback,
                    )
                }
                viewModel.clearSelectedPlaybackUrl()
            }
            state.selectedPlaybackUrl != null && !isAllohaVoiceover -> {
                onWatch(
                    arrayListOf(state.selectedPlaybackUrl ?: ""),
                    arrayListOf(""),
                    0,
                    state.details?.title,
                    state.kinopoiskId,
                    episodeProgressCallback,
                )
                viewModel.clearSelectedPlaybackUrl()
            }
        }
    }

    TvScreenScaffold(
        title = state.details?.title ?: stringResource(R.string.action_watch),
        onBack = onBack,
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
    ) { padding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
            state.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) { Text(text = state.error ?: stringResource(R.string.common_error)) }
            }
            else -> {
                when (sourceMode) {
                    SourceMode.COLLAPS -> {
                        val seasons = state.tvSeasons.orEmpty()
                        val posterId = state.details?.externalIds?.kp?.toString()
                            ?: state.details?.id
                            ?: state.details?.sourceId
                        val poster = resolveDetailsImageUrl(state.details?.backdropUrl)
                            ?: resolveDetailsImageUrl(state.details?.posterUrl)
                            ?: resolveDetailsImageUrl(posterId)

                        if (seasons.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(text = stringResource(R.string.lumex_no_data))
                            }
                        } else {
                            val selectedSeason = state.selectedSeasonNumber
                            if (selectedSeason == null) {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 180.dp),
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(20.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                                ) {
                                    items(seasons) { season ->
                                        SeasonCard(
                                            seasonNumber = season.number,
                                            posterUrl = poster,
                                            onClick = { viewModel.selectSeason(season.number) },
                                        )
                                    }
                                }
                            } else {
                                val season = seasons.firstOrNull { it.number == selectedSeason }
                                val episodes = season?.episodes.orEmpty()

                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                                ) {
                                    items(episodes) { episode ->
                                        val progressPercent = if (episode.watchProgressMs > 0) {
                                            val duration = 45 * 60 * 1000L
                                            ((episode.watchProgressMs.toFloat() / duration) * 100).toInt()
                                        } else 0

                                        val supportingText = when {
                                            episode.isWatched -> stringResource(R.string.episode_watched)
                                            progressPercent > 0 -> stringResource(R.string.episode_progress, progressPercent)
                                            else -> null
                                        }

                                        EpisodeItem(
                                            episodeNumber = episode.number,
                                            isWatched = episode.isWatched,
                                            supportingText = supportingText,
                                            onClick = { viewModel.selectEpisode(episode.number) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    SourceMode.TORRENTS -> {
                        TorrentsList(
                            torrents = state.torrents,
                            onSelect = { torrent ->
                                val title = torrent.title.ifBlank { torrent.name }
                                viewModel.resolveTorrent(torrent.magnet, title)
                            },
                        )
                    }
                    SourceMode.ALLOHA -> {
                        val seasons = state.tvSeasons.orEmpty()
                        val movie = state.movie
                        val posterId = state.details?.externalIds?.kp?.toString()
                            ?: state.details?.id
                            ?: state.details?.sourceId
                        val poster = resolveDetailsImageUrl(state.details?.backdropUrl)
                            ?: resolveDetailsImageUrl(state.details?.posterUrl)
                            ?: resolveDetailsImageUrl(posterId)

                        Box(modifier = Modifier.fillMaxSize()) {
                            when {
                                seasons.isNotEmpty() -> {
                                    val selectedSeason = state.selectedSeasonNumber

                                    // Translation picker overlay
                                    if (state.showAllohaTranslationPicker && state.allohaEpisodeVoiceovers.isNotEmpty()) {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(12.dp),
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                                        ) {
                                            item {
                                                Text(
                                                    text = stringResource(R.string.lumex_select_voiceover),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = Color.White,
                                                )
                                            }
                                            items(state.allohaEpisodeVoiceovers) { voice ->
                                                val isSaved = voice.title == state.allohaTranslationName
                                                EpisodeItem(
                                                    episodeNumber = 0,
                                                    isWatched = false,
                                                    supportingText = voice.title,
                                                    onClick = { viewModel.selectAllohaVoiceover(voice) },
                                                )
                                            }
                                        }
                                    } else if (selectedSeason == null) {
                                        LazyVerticalGrid(
                                            columns = GridCells.Adaptive(minSize = 180.dp),
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(20.dp),
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                                        ) {
                                            items(seasons) { season ->
                                                SeasonCard(
                                                    seasonNumber = season.number,
                                                    posterUrl = poster,
                                                    onClick = { viewModel.selectSeason(season.number) },
                                                )
                                            }
                                        }
                                    } else {
                                        val season = seasons.firstOrNull { it.number == selectedSeason }
                                        val episodes = season?.episodes.orEmpty()

                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(12.dp),
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                                        ) {
                                            items(episodes) { episode ->
                                                val progressPercent = if (episode.watchProgressMs > 0) {
                                                    val duration = 45 * 60 * 1000L
                                                    ((episode.watchProgressMs.toFloat() / duration) * 100).toInt()
                                                } else 0

                                                val supportingText = when {
                                                    episode.isWatched -> stringResource(R.string.episode_watched)
                                                    progressPercent > 0 -> stringResource(R.string.episode_progress, progressPercent)
                                                    episode.voiceovers.size > 1 -> episode.voiceovers.joinToString(", ") { it.title }
                                                    else -> null
                                                }

                                                EpisodeItem(
                                                    episodeNumber = episode.number,
                                                    isWatched = episode.isWatched,
                                                    supportingText = supportingText,
                                                    onClick = { viewModel.selectEpisode(episode.number) },
                                                )
                                            }
                                        }
                                    }
                                }
                                movie != null -> {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                                    ) {
                                        items(movie.voiceovers) { voice ->
                                            EpisodeItem(
                                                episodeNumber = 0,
                                                isWatched = false,
                                                supportingText = voice.title,
                                                onClick = { viewModel.selectAllohaVoiceover(voice) },
                                            )
                                        }
                                    }
                                }
                                else -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(text = stringResource(R.string.lumex_no_data))
                                    }
                                }
                            }

                            // Alloha parsing overlay
                            if (allohaParsingIframe != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.7f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator()
                                        Text(
                                            text = allohaParsingStatus ?: stringResource(R.string.alloha_parsing_stream),
                                            color = Color.White,
                                            modifier = Modifier.padding(top = 16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonCard(
    seasonNumber: Int,
    posterUrl: String?,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 4.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = posterUrl,
                contentDescription = "Сезон $seasonNumber",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (isFocused) 0.3f else 0.5f)),
            )
            Text(
                text = "${stringResource(R.string.lumex_select_season)} $seasonNumber",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodeItem(
    episodeNumber: Int,
    isWatched: Boolean,
    supportingText: String?,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isFocused) Color(0xFF3A7BD5) else Color(0xFF1E1E1E)
            )
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${stringResource(R.string.lumex_select_episode)} $episodeNumber",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                
                supportingText?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
            
            if (isWatched) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
    }
}

private fun resolveDetailsImageUrl(value: String?): String? {
    if (value.isNullOrBlank()) return null
    val v = value.trim()
    if (v.startsWith("http://") || v.startsWith("https://")) return v
    return normalizeImageUrl(v)
}

@Composable
private fun TorrentsList(
    torrents: List<JacredTorrent>,
    onSelect: (JacredTorrent) -> Unit,
) {
    if (torrents.isEmpty()) {
        Text(text = stringResource(R.string.torrents_not_found))
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(torrents) { torrent ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(text = torrent.title.ifBlank { torrent.name })
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = torrent.sizeName)
                    TvActionButton(text = stringResource(R.string.action_watch), onClick = { onSelect(torrent) })
                }
            }
        }
    }
}