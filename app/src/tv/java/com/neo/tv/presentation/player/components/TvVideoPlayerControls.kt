package com.neo.tv.presentation.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player

@Composable
fun TvVideoPlayerControls(
    player: Player,
    title: String?,
    onShowControls: () -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }

    TvVideoPlayerMainFrame(
        title = title,
        actions = {
            Row(
                modifier = Modifier.padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TvVideoPlayerControlsIcon(
                    modifier = Modifier.focusRequester(focusRequester),
                    icon = if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    isPlaying = player.isPlaying,
                    onClick = {
                        if (player.isPlaying) player.pause() else player.play()
                        onShowControls()
                    },
                )
            }
        },
        seeker = {
            TvVideoPlayerSeeker(
                player = player,
                onShowControls = onShowControls,
            )
        },
    )
}
