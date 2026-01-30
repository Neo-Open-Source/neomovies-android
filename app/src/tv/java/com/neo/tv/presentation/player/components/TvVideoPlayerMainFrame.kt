package com.neo.tv.presentation.player.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun TvVideoPlayerMainFrame(
    title: String?,
    actions: @Composable () -> Unit,
    seeker: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!title.isNullOrBlank()) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(12.dp))
        }
        actions()
        Spacer(modifier = Modifier.height(12.dp))
        seeker()
    }
}
