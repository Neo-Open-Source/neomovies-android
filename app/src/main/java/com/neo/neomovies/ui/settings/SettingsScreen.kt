package com.neo.neomovies.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.neo.neomovies.R
import com.neo.neomovies.ui.components.PreferenceItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenTorrServer: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val engineLabel =
        when (PlayerEngineManager.getMode(androidx.compose.ui.platform.LocalContext.current)) {
            PlayerEngineMode.EXO -> stringResource(R.string.settings_player_exoplayer)
            PlayerEngineMode.MPV -> stringResource(R.string.settings_player_mpv_experimental)
        }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
            )
        },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(it).padding(vertical = 8.dp),
        ) {
            PreferenceItem(
                title = stringResource(R.string.settings_language),
                description = null,
                icon = Icons.Outlined.Language,
                onClick = onOpenLanguage,
            )

            PreferenceItem(
                title = stringResource(R.string.settings_torrserver),
                description = null,
                icon = Icons.Outlined.CloudDownload,
                onClick = onOpenTorrServer,
            )

            PreferenceItem(
                title = stringResource(R.string.settings_player),
                description = engineLabel,
                icon = Icons.Outlined.PlayCircleOutline,
                onClick = onOpenPlayer,
            )
        }
    }
}
