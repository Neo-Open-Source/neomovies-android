package com.neo.tv.presentation.watch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.neo.neomovies.R
import com.neo.neomovies.data.torrents.JacredTorrent
import com.neo.neomovies.ui.home.collectAsStateWithLifecycleCompat
import com.neo.neomovies.ui.settings.SourceManager
import com.neo.neomovies.ui.settings.SourceMode
import com.neo.neomovies.ui.watch.WatchSelectorViewModel
import com.neo.tv.presentation.common.TvActionButton
import com.neo.tv.presentation.common.TvScreenScaffold
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun TvWatchSelectorScreen(
    sourceId: String,
    onBack: () -> Unit,
    onWatch: (ArrayList<String>, ArrayList<String>, Int, String?) -> Unit,
) {
    val viewModel: WatchSelectorViewModel = koinViewModel(parameters = { parametersOf(sourceId) })
    val state by viewModel.state.collectAsStateWithLifecycleCompat()
    val context = androidx.compose.ui.platform.LocalContext.current
    val sourceMode = remember { SourceManager.getMode(context) }

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
                onWatch(safePlaylist, safeNames, startIndex.coerceIn(0, safePlaylist.size - 1), state.details?.title)
            }
            viewModel.clearSelectedPlaybackUrl()
        } else if (state.selectedPlaybackUrl != null) {
            onWatch(arrayListOf(state.selectedPlaybackUrl ?: ""), arrayListOf(""), 0, state.details?.title)
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
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) { Text(text = stringResource(R.string.credits_loading)) }
            }
            state.error != null -> {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) { Text(text = state.error ?: stringResource(R.string.common_error)) }
            }
            else -> {
                when (sourceMode) {
                    SourceMode.COLLAPS -> {
                        val seasons = state.tvSeasons.orEmpty()
                        if (seasons.isEmpty()) {
                            Text(text = stringResource(R.string.lumex_no_data))
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                val selectedSeason = state.selectedSeasonNumber
                                if (selectedSeason == null) {
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        items(seasons) { season ->
                                            TvActionButton(
                                                text = stringResource(R.string.lumex_select_season) + " ${season.number}",
                                                onClick = { viewModel.selectSeason(season.number) },
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                } else {
                                    val season = seasons.firstOrNull { it.number == selectedSeason }
                                    val episodes = season?.episodes.orEmpty()
                                    val selectedEpisode = state.selectedEpisodeNumber
                                    if (selectedEpisode == null) {
                                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            items(episodes) { episode ->
                                                TvActionButton(
                                                    text = stringResource(R.string.lumex_select_episode) + " ${episode.number}",
                                                    onClick = { viewModel.selectEpisode(episode.number) },
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                            }
                                        }
                                    } else {
                                        val episode = episodes.firstOrNull { it.number == selectedEpisode }
                                        val voiceovers = episode?.voiceovers.orEmpty()
                                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            items(voiceovers) { voice ->
                                                TvActionButton(
                                                    text = voice.title,
                                                    onClick = { viewModel.selectVoiceover(voice.id, voice.playbackUrl) },
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                            }
                                        }
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
