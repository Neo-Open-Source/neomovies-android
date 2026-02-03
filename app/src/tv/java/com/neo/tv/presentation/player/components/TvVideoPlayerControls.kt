package com.neo.tv.presentation.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }

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

    if (showQualityDialog) {
        TrackSelectionDialog(
            title = stringResource(R.string.select_video_quality),
            trackType = C.TRACK_TYPE_VIDEO,
            viewModel = viewModel,
            onDismiss = { showQualityDialog = false },
        )
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
                        if (player.hasPreviousMediaItem()) {
                            player.seekToPreviousMediaItem()
                        }
                        onShowControls()
                    },
                )
                TvVideoPlayerControlsIcon(
                    icon = Icons.Default.SkipNext,
                    isPlaying = player.isPlaying,
                    onClick = {
                        if (player.hasNextMediaItem()) {
                            player.seekToNextMediaItem()
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
                if (useCollapsHeaders) {
                    TvVideoPlayerControlsIcon(
                        icon = Icons.Default.Settings,
                        isPlaying = player.isPlaying,
                        onClick = {
                            showQualityDialog = true
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
    val tracks = remember(trackType) { viewModel.getSelectableTracks(trackType) }
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
