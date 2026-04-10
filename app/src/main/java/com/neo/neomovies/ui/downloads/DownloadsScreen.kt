package com.neo.neomovies.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import coil.compose.AsyncImage
import com.neo.neomovies.R
import com.neo.neomovies.downloads.CollapsDownloadQueue
import com.neo.neomovies.downloads.DownloadEntry
import com.neo.neomovies.downloads.NeoDownloadService
import com.neo.neomovies.ui.home.collectAsStateWithLifecycleCompat
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: (() -> Unit)? = null,
    onOpenDetails: ((String) -> Unit)? = null,
    onDeleteEntry: ((DownloadEntry) -> Unit)? = null,
    onPlayEntry: ((DownloadEntry) -> Unit)? = null,
) {
    val viewModel: DownloadsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycleCompat()

    // Track which show is expanded
    var expandedShowId by remember { mutableStateOf<String?>(null) }
    var expandedSeasonNumber by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.nav_downloads)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = state.error ?: "")
                }
            }
            state.movies.isEmpty() && state.shows.isEmpty() && state.active.isEmpty() && state.collapsProgress.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = stringResource(R.string.downloads_empty))
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    // Active downloads section
                    val hasActive = state.active.isNotEmpty() || state.collapsProgress.isNotEmpty()
                    if (hasActive) {
                        item {
                            Text(
                                text = stringResource(R.string.downloads_active),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                        items(state.active, key = { it.request.id }) { d ->
                            ActiveDownloadItem(download = d)
                        }
                        items(state.collapsProgress.entries.toList(), key = { it.key }) { (id, pct) ->
                            val ctx = LocalContext.current
                            CollapsActiveItem(
                                downloadId = id,
                                progress = pct,
                                onCancel = { CollapsDownloadQueue.cancel(id, ctx) },
                            )
                        }
                    }

                    // Movies grid
                    if (state.movies.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.downloads_movies),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                        item {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(140.dp),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(state.movies) { entry ->
                                    MoviePosterCard(
                                        entry = entry,
                                        onClick = {
                                            if (onPlayEntry != null) onPlayEntry(entry)
                                            else onOpenDetails?.invoke(entry.showId ?: "")
                                        },
                                        onDelete = {
                                            viewModel.deleteEntry(entry)
                                            onDeleteEntry?.invoke(entry)
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // Shows — series/season/episode selector style
                    if (state.shows.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.downloads_series),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }

                        items(state.shows, key = { it.showId }) { show ->
                            val isShowExpanded = expandedShowId == show.showId
                            ShowItem(
                                show = show,
                                isExpanded = isShowExpanded,
                                expandedSeason = if (isShowExpanded) expandedSeasonNumber else null,
                                onShowClick = {
                                    expandedShowId = if (isShowExpanded) null else show.showId
                                    expandedSeasonNumber = null
                                },
                                onSeasonClick = { seasonNum ->
                                    expandedSeasonNumber = if (expandedSeasonNumber == seasonNum) null else seasonNum
                                },
                                onDeleteShow = {
                                    val eps = show.seasons.flatMap { it.episodes }
                                    viewModel.deleteEntries(eps)
                                    eps.forEach { onDeleteEntry?.invoke(it) }
                                    expandedShowId = null
                                },
                                onDeleteSeason = { season ->
                                    viewModel.deleteEntries(season.episodes)
                                    season.episodes.forEach { onDeleteEntry?.invoke(it) }
                                },
                                onPlayEpisode = { ep -> onPlayEntry?.invoke(ep) },
                                onDeleteEpisode = { ep ->
                                    viewModel.deleteEntry(ep)
                                    onDeleteEntry?.invoke(ep)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShowItem(
    show: DownloadGroupItem.ShowItem,
    isExpanded: Boolean,
    expandedSeason: Int?,
    onShowClick: () -> Unit,
    onSeasonClick: (Int) -> Unit,
    onDeleteShow: () -> Unit,
    onDeleteSeason: (DownloadedSeason) -> Unit,
    onPlayEpisode: (DownloadEntry) -> Unit,
    onDeleteEpisode: (DownloadEntry) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Show row
        ListItem(
            headlineContent = {
                Text(
                    text = show.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Text(
                    text = "${show.seasons.size} ${if (show.seasons.size == 1) "season" else "seasons"} • ${show.seasons.sumOf { it.episodes.size }} episodes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            leadingContent = {
                if (show.posterUrl != null) {
                    AsyncImage(
                        model = show.posterUrl,
                        contentDescription = show.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                }
            },
            trailingContent = {
                IconButton(onClick = onDeleteShow) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                }
            },
            modifier = Modifier.clickable { onShowClick() },
        )

        if (isExpanded) {
            show.seasons.forEach { season ->
                val isSeasonExpanded = expandedSeason == season.seasonNumber
                // Season row
                ListItem(
                    headlineContent = {
                        Text(text = "Season ${season.seasonNumber}")
                    },
                    supportingContent = {
                        Text(
                            text = "${season.episodes.size} episodes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { onDeleteSeason(season) }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .clickable { onSeasonClick(season.seasonNumber) },
                )

                if (isSeasonExpanded) {
                    season.episodes.forEach { ep ->
                        val ctx = LocalContext.current
                        val kpId = ep.showId?.removePrefix("kp_")?.toIntOrNull()
                        val watched = if (kpId != null && ep.seasonNumber != null && ep.episodeNumber != null) {
                            ctx.getSharedPreferences("collaps_watched", android.content.Context.MODE_PRIVATE)
                                .getBoolean("kp_${kpId}_s${ep.seasonNumber}_e${ep.episodeNumber}_watched", false)
                        } else false

                        ListItem(
                            headlineContent = {
                                Text(
                                    text = "Episode ${ep.episodeNumber ?: 0}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = if (watched) {
                                { Text(text = stringResource(R.string.episode_watched), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall) }
                            } else null,
                            trailingContent = {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(onClick = { onPlayEpisode(ep) }) {
                                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                                    }
                                    IconButton(onClick = { onDeleteEpisode(ep) }) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            },
                            modifier = Modifier.padding(start = 32.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoviePosterCard(
    entry: DownloadEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            AsyncImage(
                model = entry.posterUrl,
                contentDescription = entry.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), CircleShape),
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        Text(
            text = entry.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ActiveDownloadItem(download: Download) {
    val context = LocalContext.current
    val progress = download.percentDownloaded.takeIf { it >= 0f } ?: 0f
    val label = Regex("_s(\\d+)_e(\\d+)_").find(download.request.id)
        ?.let { "S${it.groupValues[1]}E${it.groupValues[2]}" }
        ?: download.request.id

    ListItem(
        headlineContent = { Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "${progress.toInt()}%", style = MaterialTheme.typography.bodySmall)
                IconButton(
                    onClick = {
                        DownloadService.sendRemoveDownload(context, NeoDownloadService::class.java, download.request.id, false)
                    }
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = null)
                }
            }
        },
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun CollapsActiveItem(
    downloadId: String,
    progress: Int,
    onCancel: () -> Unit,
) {
    val label = Regex("_s(\\d+)_e(\\d+)_").find(downloadId)
        ?.let { "S${it.groupValues[1]}E${it.groupValues[2]}" }
        ?: downloadId

    ListItem(
        headlineContent = { Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "$progress%", style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = onCancel) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = null)
                }
            }
        },
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}
