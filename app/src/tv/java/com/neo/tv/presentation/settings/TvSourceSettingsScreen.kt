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
import com.neo.neomovies.ui.settings.SourceManager
import com.neo.neomovies.ui.settings.SourceMode
import com.neo.tv.presentation.common.TvActionButton
import com.neo.tv.presentation.common.TvScreenScaffold

@Composable
fun TvSourceSettingsScreen(
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var mode by remember { mutableStateOf(SourceManager.getMode(context)) }

    fun select(newMode: SourceMode) {
        mode = newMode
        SourceManager.setMode(context, newMode)
    }

    TvScreenScaffold(
        title = stringResource(R.string.settings_source),
        onBack = onBack,
    ) { padding ->
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxWidth().padding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SourceRow(
                title = stringResource(R.string.settings_source_collaps),
                selected = mode == SourceMode.COLLAPS,
                onSelect = { select(SourceMode.COLLAPS) },
            )
            SourceRow(
                title = stringResource(R.string.settings_source_torrents),
                selected = mode == SourceMode.TORRENTS,
                onSelect = { select(SourceMode.TORRENTS) },
            )
        }
    }
}

@Composable
private fun SourceRow(
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
                text = if (selected) "✓" else stringResource(R.string.action_more),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
        ),
        shape = ListItemDefaults.shape(shape = MaterialTheme.shapes.medium),
    )
}
