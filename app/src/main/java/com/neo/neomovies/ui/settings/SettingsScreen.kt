package com.neo.neomovies.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import com.neo.neomovies.update.UpdateChecker
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
    onOpenSource: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showChannelDialog by remember { mutableStateOf(false) }
    var currentChannel by remember { mutableStateOf(UpdateChecker.getUpdateChannel(context)) }

    val sourceLabel =
        when (SourceManager.getMode(context)) {
            SourceMode.COLLAPS -> stringResource(R.string.settings_source_collaps)
            SourceMode.TORRENTS -> stringResource(R.string.settings_source_torrents)
            SourceMode.ALLOHA -> stringResource(R.string.settings_source_alloha)
        }

    val engineLabel =
        when (PlayerEngineManager.getMode(context)) {
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
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .navigationBarsPadding()
                .padding(vertical = 8.dp),
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
                title = stringResource(R.string.settings_source),
                description = sourceLabel,
                icon = Icons.Outlined.Tune,
                onClick = onOpenSource,
            )

            PreferenceItem(
                title = stringResource(R.string.settings_player),
                description = engineLabel,
                icon = Icons.Outlined.PlayCircleOutline,
                onClick = onOpenPlayer,
            )

            PreferenceItem(
                title = "Канал обновлений",
                description = if (currentChannel == UpdateChecker.UpdateChannel.PRERELEASE) "Пре-релизы" else "Стабильные",
                icon = Icons.Outlined.SystemUpdate,
                onClick = { showChannelDialog = true },
            )
        }
    }

    if (showChannelDialog) {
        AlertDialog(
            onDismissRequest = { showChannelDialog = false },
            title = { Text("Канал обновлений") },
            text = {
                Column {
                    listOf(
                        UpdateChecker.UpdateChannel.STABLE to "Стабильные релизы",
                        UpdateChecker.UpdateChannel.PRERELEASE to "Пре-релизы",
                    ).forEach { (ch, label) ->
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    UpdateChecker.setUpdateChannel(context, ch)
                                    currentChannel = ch
                                    showChannelDialog = false
                                }
                                .padding(vertical = 12.dp),
                        ) {
                            RadioButton(
                                selected = currentChannel == ch,
                                onClick = {
                                    UpdateChecker.setUpdateChannel(context, ch)
                                    currentChannel = ch
                                    showChannelDialog = false
                                },
                            )
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showChannelDialog = false }) { Text("Закрыть") } },
        )
    }
}
