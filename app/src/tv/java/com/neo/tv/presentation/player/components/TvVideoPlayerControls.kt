package com.neo.tv.presentation.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.neo.neomovies.R
import com.neo.neomovies.ui.home.collectAsStateWithLifecycleCompat
import com.neo.player.PlayerViewModel
import com.neo.player.PlayerEvents
import com.neo.tv.presentation.common.TvActionButton

@Composable
fun TvVideoPlayerControls(
    player: Player,
    viewModel: PlayerViewModel,
    title: String?,
    useCollapsHeaders: Boolean,
    isControlsVisible: Boolean,
    resizeMode: Int,
    onToggleResizeMode: () -> Unit,
    onShowControls: () -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showAllohaQualityDialog by remember { mutableStateOf(false) }
    var showAllohaTranslationDialog by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }

    val tracksVersion by viewModel.tracksVersion.collectAsStateWithLifecycleCompat()
    val isAlloha = remember(tracksVersion) {
        com.neo.neomovies.data.alloha.AllohaSessionHolder.translationNames.size > 1
    }
    val allohaHolder = com.neo.neomovies.data.alloha.AllohaSessionHolder
    // Recompute on every recomposition — session is set before player screen opens
    val isAllohaSource = allohaHolder.session != null
    val hasEpisodes = allohaHolder.episodeIframeUrls.size > 1

    fun switchAllohaEpisode(delta: Int) {
        val holder = allohaHolder
        val newIndex = holder.currentEpisodeIndex + delta
        if (newIndex < 0 || newIndex >= holder.episodeIframeUrls.size) return
        val iframeUrl = holder.episodeIframeUrls[newIndex].takeIf { it.isNotBlank() } ?: return
        val session = holder.session ?: return
        viewModel.updatePlaybackProgress()
        holder.currentEpisodeIndex = newIndex
        session.onStreamReady = { _, _ -> }
        session.onM3u8Updated = { _ ->
            viewModel.resetAudioOverride()
            viewModel.clearEpisodeProgress(newIndex)
            player.stop()
            player.seekTo(0)
            player.prepare()
            player.playWhenReady = true
        }
        session.onError = { }
        session.startSession(iframeUrl)
    }

    LaunchedEffect(isControlsVisible) {
        if (isControlsVisible) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.eventsChannelFlow.collect { event ->
            if (event is PlayerEvents.IsPlayingChanged) {
                isPlaying = event.isPlaying
            }
        }
    }

    if (showSubtitleDialog) {
        TrackSelectionDialog(
            title = stringResource(R.string.select_subtitle_track),
            trackType = C.TRACK_TYPE_TEXT,
            viewModel = viewModel,
            onDismiss = { showSubtitleDialog = false },
        )
    }

    if (showAudioDialog) {
        TrackSelectionDialog(
            title = stringResource(R.string.select_audio_track),
            trackType = C.TRACK_TYPE_AUDIO,
            viewModel = viewModel,
            onDismiss = { showAudioDialog = false },
        )
    }

    var isSwitchingTranslation by remember { mutableStateOf(false) }

    if (isSwitchingTranslation) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            androidx.compose.material3.CircularProgressIndicator()
        }
    }

    if (showAllohaTranslationDialog) {
        val holder = com.neo.neomovies.data.alloha.AllohaSessionHolder
        val context = androidx.compose.ui.platform.LocalContext.current
        AllohaTranslationDialog(
            names = holder.translationNames,
            currentName = holder.currentTranslation,
            onSelect = { name, iframeUrl ->
                showAllohaTranslationDialog = false
                val session = holder.session ?: return@AllohaTranslationDialog
                if (name == holder.currentTranslation) return@AllohaTranslationDialog

                isSwitchingTranslation = true
                viewModel.updatePlaybackProgress()

                session.onStreamReady = { _, m3u8Url ->
                    session.hlsProxy?.updateMasterUrl(m3u8Url)
                    holder.currentTranslation = name
                    context.getSharedPreferences("alloha_translation", android.content.Context.MODE_PRIVATE)
                        .edit()
                        .putString("last_translation_name", name)
                        .apply()
                    viewModel.resetAudioOverride()
                    player.stop()
                    player.prepare()
                    player.playWhenReady = true
                    isSwitchingTranslation = false
                }
                session.onError = { isSwitchingTranslation = false }
                session.startSession(iframeUrl)
            },
            onDismiss = { showAllohaTranslationDialog = false },
        )
    }

    if (showQualityDialog) {
        TrackSelectionDialog(
            title = stringResource(R.string.select_video_quality),
            trackType = C.TRACK_TYPE_VIDEO,
            viewModel = viewModel,
            onDismiss = { showQualityDialog = false },
        )
    }

    if (showAllohaQualityDialog) {
        val holder = com.neo.neomovies.data.alloha.AllohaSessionHolder
        val session = holder.session
        val qualityMap = session?.lastQualityMap ?: emptyMap()
        val orderedKeys = listOf("2160", "1440", "1080", "720", "480", "360").filter { qualityMap.containsKey(it) }
        val context = androidx.compose.ui.platform.LocalContext.current
        if (orderedKeys.isNotEmpty() && session != null) {
            val autoLabel = stringResource(R.string.quality_auto)
            val allKeys = listOf("") + orderedKeys
            val labels = listOf(autoLabel) + orderedKeys.map { "${it}p" }
            val currentIdx = if (holder.isAutoQuality) 0 else allKeys.indexOf(holder.currentQuality).coerceAtLeast(0)
            Dialog(onDismissRequest = { showAllohaQualityDialog = false }) {
                Surface(shape = MaterialTheme.shapes.large) {
                    Column(modifier = Modifier.padding(24.dp).widthIn(min = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.lumex_select_quality), style = MaterialTheme.typography.titleMedium)
                        labels.forEachIndexed { idx, label ->
                            TrackSelectionRow(
                                text = label,
                                selected = idx == currentIdx,
                                onClick = {
                                    showAllohaQualityDialog = false
                                    val newKey = allKeys[idx]
                                    if (newKey.isBlank()) {
                                        holder.isAutoQuality = true
                                        holder.currentQuality = ""
                                        val best = com.neo.neomovies.data.alloha.AllohaSessionManager.pickBestQualityPublic(context, qualityMap)
                                        session.switchQuality(best)
                                    } else {
                                        holder.isAutoQuality = false
                                        holder.currentQuality = newKey
                                        session.switchQuality(newKey)
                                    }
                                    viewModel.resetAudioOverride()
                                    player.stop()
                                    player.prepare()
                                    player.playWhenReady = true
                                },
                            )
                        }
                    }
                }
            }
        } else {
            showAllohaQualityDialog = false
        }
    }

    TvVideoPlayerMainFrame(
        mediaTitle = {
            if (!title.isNullOrBlank()) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
            }
        },
        mediaActions = {
            Row(
                modifier = Modifier.padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TvVideoPlayerControlsIcon(
                    modifier = Modifier.focusRequester(focusRequester),
                    icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    isPlaying = isPlaying,
                    onClick = {
                        if (player.isPlaying) player.pause() else player.play()
                        onShowControls()
                    },
                )
                TvVideoPlayerControlsIcon(
                    icon = Icons.Default.SkipPrevious,
                    isPlaying = player.isPlaying,
                    onClick = {
                        if (hasEpisodes) switchAllohaEpisode(-1)
                        else if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
                        onShowControls()
                    },
                )
                TvVideoPlayerControlsIcon(
                    icon = Icons.Default.SkipNext,
                    isPlaying = player.isPlaying,
                    onClick = {
                        if (hasEpisodes) switchAllohaEpisode(+1)
                        else if (player.hasNextMediaItem()) player.seekToNextMediaItem()
                        onShowControls()
                    },
                )
                TvVideoPlayerControlsIcon(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    isPlaying = player.isPlaying,
                    onClick = {
                        if (isAllohaSource) {
                            showAllohaTranslationDialog = true
                        } else {
                            showAudioDialog = true
                        }
                        onShowControls()
                    },
                )
                TvVideoPlayerControlsIcon(
                    icon = Icons.Default.ClosedCaption,
                    isPlaying = player.isPlaying,
                    onClick = {
                        showSubtitleDialog = true
                        onShowControls()
                    },
                )
                if (useCollapsHeaders || isAllohaSource) {
                    TvVideoPlayerControlsIcon(
                        icon = Icons.Default.Settings,
                        isPlaying = player.isPlaying,
                        onClick = {
                            if (isAllohaSource) showAllohaQualityDialog = true
                            else showQualityDialog = true
                            onShowControls()
                        },
                    )
                }
                TvVideoPlayerControlsIcon(
                    icon = Icons.Default.AspectRatio,
                    isPlaying = player.isPlaying,
                    onClick = {
                        onToggleResizeMode()
                        onShowControls()
                    },
                )
            }
        },
        seeker = {
            TvVideoPlayerSeeker(
                player = player,
                isControlsVisible = isControlsVisible,
                onShowControls = onShowControls,
            )
        },
    )
}

@Composable
private fun TrackSelectionDialog(
    title: String,
    trackType: @C.TrackType Int,
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit,
) {
    val tracksVersion by viewModel.tracksVersion.collectAsStateWithLifecycleCompat()
    val tracks = remember(trackType, tracksVersion) { viewModel.getSelectableTracks(trackType) }
    val selectedIndex = tracks.indexOfFirst { it.isSelected }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .widthIn(min = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                TrackSelectionRow(
                    text = stringResource(R.string.none),
                    selected = selectedIndex == -1,
                    onClick = {
                        viewModel.switchToTrack(trackType, -1)
                        onDismiss()
                    },
                )
                tracks.forEachIndexed { index, track ->
                    TrackSelectionRow(
                        text = track.label,
                        selected = track.isSelected,
                        onClick = {
                            viewModel.switchToTrack(trackType, index)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackSelectionRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium),
        colors = ClickableSurfaceDefaults.colors(
            containerColor =
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}

@Composable
fun AllohaTranslationDialog(
    names: List<String>,
    currentName: String,
    onSelect: (name: String, iframeUrl: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val holder = com.neo.neomovies.data.alloha.AllohaSessionHolder
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            colors = androidx.tv.material3.SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .widthIn(min = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.lumex_select_voiceover),
                    style = MaterialTheme.typography.titleMedium,
                )
                names.forEachIndexed { index, name ->
                    TrackSelectionRow(
                        text = name,
                        selected = name == currentName,
                        onClick = {
                            val url = holder.translationUrls.getOrNull(index) ?: return@TrackSelectionRow
                            onSelect(name, url)
                        },
                    )
                }
            }
        }
    }
}
