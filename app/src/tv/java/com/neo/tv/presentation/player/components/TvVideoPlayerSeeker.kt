package com.neo.tv.presentation.player.components

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.media3.common.Player
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

@Composable
fun TvVideoPlayerSeeker(
    player: Player,
    modifier: Modifier = Modifier,
    onShowControls: () -> Unit = {},
) {
    var currentPositionMs by remember { mutableLongStateOf(player.currentPosition) }
    var isFocused by remember { mutableStateOf(false) }
    val duration = player.duration.coerceAtLeast(1L)

    LaunchedEffect(player.isPlaying) {
        while (player.isPlaying) {
            currentPositionMs = player.currentPosition
            delay(500)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                    when (event.nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                            val newPos = (player.currentPosition - 10000L).coerceAtLeast(0)
                            player.seekTo(newPos)
                            currentPositionMs = newPos
                            onShowControls()
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            val newPos = (player.currentPosition + 10000L).coerceAtMost(player.duration)
                            player.seekTo(newPos)
                            currentPositionMs = newPos
                            onShowControls()
                            true
                        }
                        else -> false
                    }
                } else false
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvVideoPlayerControllerText(text = formatDuration(currentPositionMs))
        
        Box(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            TvVideoPlayerIndicator(
                progress = currentPositionMs.toFloat() / duration.toFloat(),
                isFocused = isFocused
            )
        }
        
        TvVideoPlayerControllerText(text = formatDuration(player.duration))
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}

private fun Number.padStartWith0() = this.toString().padStart(2, '0')
