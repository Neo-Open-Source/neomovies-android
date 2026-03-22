package com.neo.neomovies.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import com.neo.neomovies.R
import com.neo.neomovies.ui.home.collectAsStateWithLifecycleCompat
import org.koin.androidx.compose.koinViewModel
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import com.neo.neomovies.downloads.NeoDownloadService
import com.neo.neomovies.downloads.DownloadEntry

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Downloads") },
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
            state.movies.isEmpty() && state.shows.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Нет загрузок")
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (state.active.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.downloads_active),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        items(state.active) { d ->
                            ActiveDownloadRow(download = d)
                        }
                    }
                    if (state.movies.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.downloads_movies),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 16.dp),
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
                                    DownloadPosterCard(
                                        title = entry.title,
                                        posterUrl = entry.posterUrl,
                                        onClick = {
                                            if (onPlayEntry != null) {
                                                onPlayEntry(entry)
                                            } else {
                                                onOpenDetails?.invoke(entry.showId ?: "")
                                            }
                                        },
                                        onDelete = { onDeleteEntry?.invoke(entry) },
                                    )
                                }
                            }
                        }
                    }

                    if (state.shows.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.downloads_series),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        items(state.shows.size) { idx ->
                            val show = state.shows[idx]
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = show.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Button(
                                        onClick = {
                                            show.seasons.flatMap { it.episodes }.forEach { onDeleteEntry?.invoke(it) }
                                        }
                                    ) {
                                        Text(text = stringResource(R.string.download_delete_series))
                                    }
                                }
                                show.seasons.forEach { season ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "Season ${season.seasonNumber}",
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Button(
                                            onClick = {
                                                season.episodes.forEach { onDeleteEntry?.invoke(it) }
                                            }
                                        ) {
                                            Text(text = stringResource(R.string.download_delete_season))
                                        }
                                    }
                                    season.episodes.forEach { ep ->
                                        val ctx = androidx.compose.ui.platform.LocalContext.current
                                        val kpId = ep.showId?.removePrefix("kp_")?.toIntOrNull()
                                        val watched = if (kpId != null && ep.seasonNumber != null && ep.episodeNumber != null) {
                                            val prefs = ctx.getSharedPreferences("collaps_watched", android.content.Context.MODE_PRIVATE)
                                            prefs.getBoolean("kp_${kpId}_s${ep.seasonNumber}_e${ep.episodeNumber}_watched", false)
                                        } else {
                                            false
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "E${ep.episodeNumber ?: 0}: ${ep.title}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                if (watched) {
                                                    Text(
                                                        text = stringResource(R.string.episode_watched),
                                                        style = MaterialTheme.typography.labelSmall,
                                                    )
                                                }
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(onClick = { onPlayEntry?.invoke(ep) }) {
                                                    Text(text = stringResource(R.string.download_play))
                                                }
                                                OutlinedButton(onClick = { onDeleteEntry?.invoke(ep) }) {
                                                    Text(text = stringResource(R.string.download_remove))
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
        }
    }
}

@Composable
private fun ActiveDownloadRow(download: Download) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val progress = download.percentDownloaded.takeIf { it >= 0f } ?: 0f
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = download.request.id, style = MaterialTheme.typography.bodyMedium)
        val size = if (download.contentLength > 0) formatBytes(download.contentLength) else ""
        val pct = "${progress.toInt()}%"
        Text(text = listOf(pct, size).filter { it.isNotBlank() }.joinToString(" • "), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (download.state) {
                Download.STATE_DOWNLOADING -> {
                    OutlinedButton(
                        onClick = {
                            DownloadService.sendSetStopReason(
                                context,
                                NeoDownloadService::class.java,
                                download.request.id,
                                Download.STOP_REASON_NONE + 1,
                                false
                            )
                        }
                    ) { Text(stringResource(R.string.download_pause)) }
                }
                Download.STATE_STOPPED -> {
                    OutlinedButton(
                        onClick = {
                            DownloadService.sendSetStopReason(
                                context,
                                NeoDownloadService::class.java,
                                download.request.id,
                                Download.STOP_REASON_NONE,
                                false
                            )
                        }
                    ) { Text(stringResource(R.string.download_resume)) }
                }
            }
            OutlinedButton(
                onClick = {
                    DownloadService.sendRemoveDownload(
                        context,
                        NeoDownloadService::class.java,
                        download.request.id,
                        false
                    )
                }
            ) { Text(stringResource(R.string.download_remove)) }
        }
    }
}

@Composable
private fun DownloadPosterCard(
    title: String,
    posterUrl: String?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onDelete) {
                    Text(text = stringResource(R.string.download_remove))
                }
            }
        }
    }
}

private fun formatBytes(size: Long): String {
    if (size <= 0) return ""
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(java.util.Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
