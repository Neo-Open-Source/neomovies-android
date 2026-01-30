package com.neo.tv.presentation.player.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

class TvVideoPlayerPulseState {
    var type: TvVideoPlayerPulseType? = null
        private set

    fun setType(type: TvVideoPlayerPulseType) {
        this.type = type
    }

    fun clear() {
        type = null
    }
}

@Composable
fun rememberTvVideoPlayerPulseState(): TvVideoPlayerPulseState = remember {
    TvVideoPlayerPulseState()
}
