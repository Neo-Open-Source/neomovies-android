package com.neo.tv.presentation.player.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults

@Composable
fun TvVideoPlayerPulse(state: TvVideoPlayerPulseState) {
    val alpha by animateFloatAsState(
        targetValue = if (state.type == null) 0f else 0.85f,
        label = "pulse_alpha",
    )
    if (alpha <= 0f) return

    Surface(
        modifier = Modifier.size(88.dp).alpha(alpha),
        shape = MaterialTheme.shapes.extraLarge,
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
        ),
    ) {
        // Intentionally empty: visual pulse only.
    }
}
