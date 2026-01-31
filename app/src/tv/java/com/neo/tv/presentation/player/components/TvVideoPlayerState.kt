package com.neo.tv.presentation.player.components

import androidx.annotation.IntRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce

class TvVideoPlayerState(
    @IntRange(from = 0)
    private val hideSeconds: Int,
) {
    var isControlsVisible by mutableStateOf(true)
        private set

    fun showControls(isPlaying: Boolean = true) {
        if (isPlaying) {
            updateControlVisibility()
        } else {
            updateControlVisibility(seconds = Int.MAX_VALUE)
        }
    }

    fun hideControls() {
        isControlsVisible = false
    }

    private fun updateControlVisibility(seconds: Int = hideSeconds) {
        isControlsVisible = true
        channel.trySend(seconds)
    }

    private val channel = Channel<Int>(CONFLATED)

    @OptIn(FlowPreview::class)
    suspend fun observe() {
        channel.consumeAsFlow()
            .debounce { it.toLong() * 1000 }
            .collect { isControlsVisible = false }
    }
}

@Composable
fun rememberTvVideoPlayerState(
    @IntRange(from = 0) hideSeconds: Int = 2,
): TvVideoPlayerState {
    return remember {
        TvVideoPlayerState(hideSeconds = hideSeconds)
    }.also { LaunchedEffect(it) { it.observe() } }
}
