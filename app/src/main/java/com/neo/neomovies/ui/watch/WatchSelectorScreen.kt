package com.neo.neomovies.ui.watch

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neo.neomovies.BuildConfig
import com.neo.neomovies.R
import com.neo.neomovies.torrserver.TorServerService
import com.neo.neomovies.torrserver.TorrServerManager
import com.neo.neomovies.torrserver.api.model.TorrentFileStat
import com.neo.neomovies.ui.settings.SourceManager
import com.neo.neomovies.ui.settings.SourceMode
import com.neo.neomovies.ui.util.normalizeImageUrl
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchSelectorScreen(
    sourceId: String,
    onBack: () -> Unit,
    onWatch: (ArrayList<String>, ArrayList<String>, Int, String?, Int?, (Int, Int, Int, Long, Long) -> Unit) -> Unit,
) {
    val viewModel: WatchSelectorViewModel = koinViewModel(parameters = { parametersOf(sourceId) })
    val state = viewModel.state.collectAsState().value

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val sourceMode = SourceManager.getMode(context)

    var pendingMagnet by remember { mutableStateOf<String?>(null) }
    var pendingTitle by remember { mutableStateOf<String?>(null) }

    var showTorrServerDialog by remember { mutableStateOf(false) }
    var dialogNeedsDownload by remember { mutableStateOf(false) }
    var dialogBusy by remember { mutableStateOf(false) }

    var showAutostartDialog by remember { mutableStateOf(false) }

    val effectiveTitle = state.details?.title?.takeIf { it.isNotBlank() }
        ?: state.details?.name?.takeIf { it.isNotBlank() }

    // Create episode progress callback for Collaps
    val episodeProgressCallback: (Int, Int, Int, Long, Long) -> Unit = { kpId, season, episode, positionMs, durationMs ->
        viewModel.updateEpisodeWatchProgress(kpId, season, episode, positionMs, durationMs)
    }

    LaunchedEffect(state.selectedPlaybackUrl, state.selectedPlaylistUrls, state.selectedPlaylistNames, state.selectedPlaylistStartIndex) {
        val playlist = state.selectedPlaylistUrls
        val playlistNames = state.selectedPlaylistNames
        val startIndex = state.selectedPlaylistStartIndex

        when {
            playlist != null && playlistNames != null && startIndex != null -> {
                // Pass the episode progress callback to the player
                onWatch(ArrayList(playlist), ArrayList(playlistNames), startIndex, effectiveTitle, state.kinopoiskId, episodeProgressCallback)
                viewModel.clearSelectedPlaybackUrl()
            }
            state.selectedPlaybackUrl != null -> {
                onWatch(arrayListOf(state.selectedPlaybackUrl), arrayListOf(""), 0, effectiveTitle, state.kinopoiskId, episodeProgressCallback)
                viewModel.clearSelectedPlaybackUrl()
            }
        }
    }

    // Update episode progress when player reports progress
    LaunchedEffect(Unit) {
        // This will be called from PlayerActivity with progress updates
        // For now, we'll handle this through the callback system
    }

    if (showTorrServerDialog) {
        AlertDialog(
            onDismissRequest = { if (!dialogBusy) showTorrServerDialog = false },
            title = { Text(stringResource(R.string.torrserver_required_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text =
                            if (dialogNeedsDownload) {
                                stringResource(R.string.torrserver_required_not_downloaded)
                            } else {
                                stringResource(R.string.torrserver_required_not_running)
                            },
                    )

                    if (dialogBusy) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(text = stringResource(R.string.torrserver_notif_downloading))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !dialogBusy,
                    onClick = {
                        val magnet = pendingMagnet
                        val title = pendingTitle
                        if (magnet == null || title == null) {
                            showTorrServerDialog = false
                            return@Button
                        }

                        scope.launch {
                            dialogBusy = true
                            if (dialogNeedsDownload) {
                                val prefs = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
                                val version = prefs.getString("torrserver_version", "136") ?: "136"
                                TorServerService.download(context, version)

                                // Wait for binary to be downloaded by the foreground service.
                                repeat(120) {
                                    if (TorrServerManager.isServerDownloaded(context)) {
                                        return@repeat
                                    }
                                    delay(500)
                                }

                                // Offer enabling autostart right after download.
                                dialogBusy = false
                                showTorrServerDialog = false
                                showAutostartDialog = true
                                return@launch
                            }

                            TorServerService.start(context)

                            // Wait for server to become reachable.
                            repeat(15) {
                                if (TorrServerManager.isServerRunning()) {
                                    dialogBusy = false
                                    showTorrServerDialog = false
                                    viewModel.resolveTorrent(magnet, title)
                                    return@launch
                                }
                                delay(800)
                            }

                            dialogBusy = false
                            showTorrServerDialog = false
                        }
                    },
                ) {
                    Text(
                        text =
                            if (dialogNeedsDownload) {
                                stringResource(R.string.torrserver_required_download_and_start)
                            } else {
                                stringResource(R.string.torrserver_required_start)
                            },
                    )
                }
            },
            dismissButton = {
                Button(
                    enabled = !dialogBusy,
                    onClick = { showTorrServerDialog = false },
                ) {
                    Text(stringResource(R.string.torrserver_required_cancel))
                }
            },
        )
    }

    if (showAutostartDialog) {
        AlertDialog(
            onDismissRequest = { showAutostartDialog = false },
            title = { Text(stringResource(R.string.torrserver_autostart_prompt_title)) },
            text = { Text(stringResource(R.string.torrserver_autostart_prompt_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        val magnet = pendingMagnet
                        val title = pendingTitle
                        if (magnet == null || title == null) {
                            showAutostartDialog = false
                            return@Button
                        }
                        val prefs = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
                        prefs.edit().putBoolean("torrserver_autostart", true).apply()
                        showAutostartDialog = false

                        scope.launch {
                            TorServerService.start(context)
                            repeat(15) {
                                if (TorrServerManager.isServerRunning()) {
                                    viewModel.resolveTorrent(magnet, title)
                                    return@launch
                                }
                                delay(800)
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.torrserver_autostart_prompt_enable))
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        val magnet = pendingMagnet
                        val title = pendingTitle
                        showAutostartDialog = false
                        if (magnet == null || title == null) return@Button
                        scope.launch {
                            TorServerService.start(context)
                            repeat(15) {
                                if (TorrServerManager.isServerRunning()) {
                                    viewModel.resolveTorrent(magnet, title)
                                    return@launch
                                }
                                delay(800)
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.torrserver_autostart_prompt_not_now))
                }
            },
        )
    }

    val topBarTitle = effectiveTitle
        ?: stringResource(R.string.app_name)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = topBarTitle)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading || state.isSourcesLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                state.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = state.error ?: stringResource(R.string.common_error))
                    }
                }

                else -> {
                    when (sourceMode) {
                        SourceMode.COLLAPS -> {
                            val poster = resolveDetailsImageUrl(state.details?.backdropUrl)
                                ?: resolveDetailsImageUrl(state.details?.posterUrl)

                            val seasons = state.tvSeasons.orEmpty()
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
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        items(seasons) { s ->
                                            SeasonCard(
                                                title = "Season ${s.number}",
                                                posterUrl = poster,
                                                onClick = { viewModel.selectSeason(s.number) },
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
                                        items(episodes) { ep ->
                                            val progressPercent = if (ep.watchProgressMs > 0) {
                                                val duration = 45 * 60 * 1000L // Approximate duration in ms
                                                ((ep.watchProgressMs.toFloat() / duration) * 100).toInt()
                                            } else {
                                                null
                                            }
                                            val supportingContent: (@Composable () -> Unit)? = when {
                                                ep.isWatched -> {
                                                    { Text(text = stringResource(R.string.episode_watched), color = MaterialTheme.colorScheme.primary) }
                                                }
                                                progressPercent != null -> {
                                                    { Text(text = stringResource(R.string.episode_progress, progressPercent), color = MaterialTheme.colorScheme.secondary) }
                                                }
                                                else -> null
                                            }
                                            val leadingContent: (@Composable () -> Unit)? = when {
                                                ep.isWatched -> {
                                                    {
                                                        Icon(
                                                            imageVector = Icons.Default.CheckCircle,
                                                            contentDescription = "Watched",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    }
                                                }
                                                progressPercent != null -> {
                                                    {
                                                        CircularProgressIndicator(
                                                            progress = { ep.watchProgressMs.toFloat() / (45 * 60 * 1000L) },
                                                            modifier = Modifier.size(24.dp),
                                                            strokeWidth = 2.dp,
                                                            color = MaterialTheme.colorScheme.secondary
                                                        )
                                                    }
                                                }
                                                else -> null
                                            }
                                            ListItem(
                                                headlineContent = { Text(text = "Episode ${ep.number}") },
                                                supportingContent = supportingContent,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .clickable { viewModel.selectEpisode(ep.number) }
                                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                                leadingContent = leadingContent,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        SourceMode.TORRENTS -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (state.isSourcesLoading) {
                                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                                }

                                if (state.torrents.isEmpty() && !state.isSourcesLoading) {
                                    Text(text = stringResource(R.string.torrents_not_found))
                                }

                                LazyColumn(
                                    modifier = Modifier.weight(1f, fill = true),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    items(state.torrents) { t ->
                                        Button(
                                            onClick = {
                                                val magnet = t.magnet
                                                val title = t.title.ifBlank { t.name }
                                                pendingMagnet = magnet
                                                pendingTitle = title

                                                scope.launch {
                                                    val downloaded = TorrServerManager.isServerDownloaded(context)
                                                    val running = if (downloaded) TorrServerManager.isServerRunning() else false

                                                    when {
                                                        !downloaded -> {
                                                            dialogNeedsDownload = true
                                                            showTorrServerDialog = true
                                                        }
                                                        !running -> {
                                                            dialogNeedsDownload = false
                                                            showTorrServerDialog = true
                                                        }
                                                        else -> viewModel.resolveTorrent(magnet, title)
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(56.dp)
                                                .clip(RoundedCornerShape(16.dp)),
                                            colors = ButtonDefaults.buttonColors(),
                                            shape = RoundedCornerShape(16.dp),
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            ) {
                                                Text(
                                                    text = t.quality.takeIf { it > 0 }?.let { "${it}p" }
                                                        ?: stringResource(R.string.torrent_quality_unknown),
                                                    fontSize = 14.sp,
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = t.title.ifBlank { t.name },
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                        fontSize = 14.sp,
                                                    )
                                                    Text(
                                                        text = stringResource(R.string.torrent_seeds_format, t.sizeName, t.sid),
                                                        fontSize = 12.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        val first = state.torrents.firstOrNull() ?: return@Button
                                        val magnet = first.magnet
                                        val title = first.title.ifBlank { first.name }
                                        pendingMagnet = magnet
                                        pendingTitle = title
                                        scope.launch {
                                            val downloaded = TorrServerManager.isServerDownloaded(context)
                                            val running = if (downloaded) TorrServerManager.isServerRunning() else false
                                            when {
                                                !downloaded -> {
                                                    dialogNeedsDownload = true
                                                    showTorrServerDialog = true
                                                }
                                                !running -> {
                                                    dialogNeedsDownload = false
                                                    showTorrServerDialog = true
                                                }
                                                else -> viewModel.resolveTorrent(magnet, title)
                                            }
                                        }
                                    },
                                    enabled = state.torrents.isNotEmpty(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    Text(text = stringResource(R.string.action_watch), fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            }

            if (state.resolvingTorrent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(enabled = false) {}, // Block clicks
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    if (state.torrentFiles != null) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { viewModel.clearTorrentSelection() },
            sheetState = sheetState,
            dragHandle = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .size(width = 32.dp, height = 4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(100)
                            )
                    )
                    Text(
                        text = stringResource(R.string.select_file),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                itemsIndexed(state.torrentFiles) { index, file ->
                    ListItem(
                        headlineContent = { 
                            Text(
                                text = file.path?.substringAfterLast('/') ?: "Unknown",
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            ) 
                        },
                        supportingContent = { 
                            Text(
                                text = formatFileSize(file.length),
                                style = MaterialTheme.typography.bodySmall
                            ) 
                        },
                        modifier = Modifier.clickable {
                            viewModel.selectTorrentFile(index)
                        }
                    )
                }
            }
        }
    }
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(java.util.Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun resolveDetailsImageUrl(value: String?): String? {
    if (value.isNullOrBlank()) return null
    val v = value.trim()
    if (v.startsWith("http://") || v.startsWith("https://")) return v

    val fromId = normalizeImageUrl(v)
    if (fromId != null) return fromId

    val base = BuildConfig.API_BASE_URL.trimEnd('/')
    return when {
        v.startsWith("/") -> base + v
        v.startsWith("api/") || v.startsWith("api/v1/") -> "$base/$v"
        else -> v
    }
}

@Composable
private fun SeasonCard(
    title: String,
    posterUrl: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(14.dp)),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Text(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
        )
    }
}
