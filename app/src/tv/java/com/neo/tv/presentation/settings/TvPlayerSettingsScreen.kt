package com.neo.tv.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.neo.neomovies.R
import com.neo.neomovies.ui.settings.PlayerEngineManager
import com.neo.neomovies.ui.settings.PlayerEngineMode
import com.neo.tv.presentation.common.TvActionButton
import com.neo.tv.presentation.common.TvScreenScaffold

@Composable
fun TvPlayerSettingsScreen(
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var mode by remember { mutableStateOf(PlayerEngineManager.getMode(context)) }

    fun select(newMode: PlayerEngineMode) {
        mode = newMode
        PlayerEngineManager.setMode(context, newMode)
    }

    TvScreenScaffold(
        title = stringResource(R.string.settings_player),
        onBack = onBack,
    ) { padding ->
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxWidth().padding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EngineRow(
                title = stringResource(R.string.settings_player_exoplayer),
                selected = mode == PlayerEngineMode.EXO,
                onSelect = { select(PlayerEngineMode.EXO) },
            )
            EngineRow(
                title = stringResource(R.string.settings_player_mpv_experimental),
                selected = mode == PlayerEngineMode.MPV,
                onSelect = { select(PlayerEngineMode.MPV) },
            )
        }
    }
}

@Composable
private fun EngineRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    ListItem(
        modifier = Modifier.fillMaxWidth(),
        selected = selected,
        onClick = onSelect,
        headlineContent = { Text(text = title, style = MaterialTheme.typography.titleMedium) },
        trailingContent = {
            Text(
                text = if (selected) stringResource(R.string.action_selected) else stringResource(R.string.action_more),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
        ),
        shape = ListItemDefaults.shape(shape = MaterialTheme.shapes.medium),
    )
}
