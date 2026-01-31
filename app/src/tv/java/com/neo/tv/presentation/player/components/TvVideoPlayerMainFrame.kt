package com.neo.tv.presentation.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TvVideoPlayerMainFrame(
    mediaTitle: @Composable () -> Unit,
    seeker: @Composable () -> Unit,
    mediaActions: @Composable () -> Unit = {},
    more: (@Composable () -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(Modifier.weight(1f)) { mediaTitle() }
            mediaActions()
        }
        Spacer(modifier = Modifier.height(16.dp))
        seeker()
        if (more != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth()) { more() }
        }
    }
}
