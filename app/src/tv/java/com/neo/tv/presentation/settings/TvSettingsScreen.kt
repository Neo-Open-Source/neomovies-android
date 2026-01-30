package com.neo.tv.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import com.neo.neomovies.ui.settings.SourceManager
import com.neo.neomovies.ui.settings.SourceMode
import com.neo.tv.presentation.common.TvScreenScaffold

@Composable
fun TvSettingsScreen(
    onBack: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenTorrServer: () -> Unit,
    onOpenSource: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sourceLabel =
        when (SourceManager.getMode(context)) {
            SourceMode.COLLAPS -> stringResource(R.string.settings_source_collaps)
            SourceMode.TORRENTS -> stringResource(R.string.settings_source_torrents)
        }

    val engineLabel =
        when (PlayerEngineManager.getMode(context)) {
            PlayerEngineMode.EXO -> stringResource(R.string.settings_player_exoplayer)
            PlayerEngineMode.MPV -> stringResource(R.string.settings_player_mpv_experimental)
        }

    TvScreenScaffold(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
        contentPadding = PaddingValues(horizontal = 72.dp, vertical = 16.dp),
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsItem(
                title = stringResource(R.string.settings_language),
                value = "",
                onClick = onOpenLanguage,
            )
            SettingsItem(
                title = stringResource(R.string.settings_torrserver),
                value = "",
                onClick = onOpenTorrServer,
            )
            SettingsItem(
                title = stringResource(R.string.settings_source),
                value = sourceLabel,
                onClick = onOpenSource,
            )
            SettingsItem(
                title = stringResource(R.string.settings_player),
                value = engineLabel,
                onClick = onOpenPlayer,
            )
        }
    }
}

@Composable
private fun SettingsItem(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.fillMaxWidth(),
        selected = false,
        onClick = onClick,
        headlineContent = { Text(text = title, style = MaterialTheme.typography.titleMedium) },
        trailingContent = {
            Text(text = if (value.isBlank()) stringResource(R.string.action_more) else value)
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
        ),
        shape = ListItemDefaults.shape(shape = MaterialTheme.shapes.medium),
    )
}
