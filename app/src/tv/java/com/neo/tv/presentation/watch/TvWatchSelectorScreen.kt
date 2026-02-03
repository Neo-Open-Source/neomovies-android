package com.neo.tv.presentation.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.neo.neomovies.R
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

    val episodeProgressCallback: (Int, Int, Int, Long, Long) -> Unit = { kpId, season, episode, positionMs, durationMs ->
        viewModel.updateEpisodeWatchProgress(kpId, season, episode, positionMs, durationMs)
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
        if (playlist != null && names != null && startIndex != null) {
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
        } else if (state.selectedPlaybackUrl != null) {
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
                        val poster = resolveDetailsImageUrl(state.details?.backdropUrl)
                            ?: resolveDetailsImageUrl(state.details?.posterUrl)

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
                                    columns = GridCells.Adaptive(minSize = 140.dp),
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                ) {
                                    items(seasons) { season ->
                                        SeasonCard(
                                            title = "${stringResource(R.string.lumex_select_season)} ${season.number}",
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
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                                ) {
                                    items(episodes) { episode ->
                                        val progressPercent = if (episode.watchProgressMs > 0) {
                                            val duration = 45 * 60 * 1000L
                                            ((episode.watchProgressMs.toFloat() / duration) * 100).toInt()
                                        } else 0

                                        ListItem(
                                            headlineContent = {
                                                Text(text = "${stringResource(R.string.lumex_select_episode)} ${episode.number}")
                                            },
                                            // Исправлено: передаем Composable лямбду напрямую
                                            supportingContent = when {
                                                episode.isWatched -> {
                                                    { Text(text = stringResource(R.string.episode_watched)) }
                                                }
                                                progressPercent > 0 -> {
                                                    { Text(text = stringResource(R.string.episode_progress, progressPercent)) }
                                                }
                                                else -> null
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(Color(0xFF1E1E1E))
                                                .clickable { viewModel.selectEpisode(episode.number) }
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                            leadingContent = if (episode.isWatched) {
                                                { Text(text = "✓") }
                                            } else null,
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
                }
            }
        }
    }
}

@Composable
private fun SeasonCard(
    title: String,
    posterUrl: String?,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.68f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = posterUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
            )
            Text(
                text = title,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
            )
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