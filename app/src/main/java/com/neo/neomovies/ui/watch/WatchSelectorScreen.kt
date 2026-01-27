package com.neo.neomovies.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neo.neomovies.R
import com.neo.neomovies.torrserver.TorServerService
import com.neo.neomovies.torrserver.TorrServerManager
import com.neo.neomovies.torrserver.api.model.TorrentFileStat
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchSelectorScreen(
    sourceId: String,
    onBack: () -> Unit,
    onWatch: (ArrayList<String>, ArrayList<String>, Int, String?) -> Unit,
) {
    val viewModel: WatchSelectorViewModel = koinViewModel(parameters = { parametersOf(sourceId) })
    val state = viewModel.state.collectAsState().value

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pendingMagnet by remember { mutableStateOf<String?>(null) }
    var pendingTitle by remember { mutableStateOf<String?>(null) }

    var showTorrServerDialog by remember { mutableStateOf(false) }
    var dialogNeedsDownload by remember { mutableStateOf(false) }
    var dialogBusy by remember { mutableStateOf(false) }

    var showAutostartDialog by remember { mutableStateOf(false) }

    val effectiveTitle = state.details?.title?.takeIf { it.isNotBlank() }
        ?: state.details?.name?.takeIf { it.isNotBlank() }

    LaunchedEffect(state.selectedPlaybackUrl, state.selectedPlaylistUrls, state.selectedPlaylistNames, state.selectedPlaylistStartIndex) {
        val playlist = state.selectedPlaylistUrls
        val playlistNames = state.selectedPlaylistNames
        val startIndex = state.selectedPlaylistStartIndex

        when {
            playlist != null && playlistNames != null && startIndex != null -> {
                onWatch(ArrayList(playlist), ArrayList(playlistNames), startIndex, effectiveTitle)
                viewModel.clearSelectedPlaybackUrl()
            }
            state.selectedPlaybackUrl != null -> {
                onWatch(arrayListOf(state.selectedPlaybackUrl), arrayListOf(""), 0, effectiveTitle)
                viewModel.clearSelectedPlaybackUrl()
            }
        }
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
