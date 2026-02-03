package com.neo.tv.presentation.player.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

@Composable
fun TvVideoPlayerSeeker(
    player: Player,
    modifier: Modifier = Modifier,
    isControlsVisible: Boolean,
    onShowControls: () -> Unit = {},
) {
    var currentPositionMs by remember { mutableLongStateOf(player.currentPosition.coerceAtLeast(0L)) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(player, isControlsVisible) {
        if (!isControlsVisible) return@LaunchedEffect
        while (isControlsVisible) {
            durationMs = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
            currentPositionMs = player.currentPosition.coerceAtLeast(0L)
            delay(1000)
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
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                        android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            val newPos = (player.currentPosition - 10000L).coerceAtLeast(0)
                            player.seekTo(newPos)
                            currentPositionMs = newPos
                            onShowControls()
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                        android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
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
                progress = if (durationMs > 0) currentPositionMs.toFloat() / durationMs.toFloat() else 0f,
                isFocused = isFocused
            )
        }

        TvVideoPlayerControllerText(text = formatDuration(durationMs))
    }
}

private fun formatDuration(ms: Long): String {
    val safeMs = ms.takeIf { it > 0 } ?: 0L
    val totalSeconds = (safeMs / 1000).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun Number.padStartWith0() = this.toString().padStart(2, '0')
