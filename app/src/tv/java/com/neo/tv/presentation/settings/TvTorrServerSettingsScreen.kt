package com.neo.tv.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.neo.neomovies.R
import com.neo.neomovies.torrserver.TorrServerManager
import com.neo.neomovies.torrserver.TorServerService
import com.neo.tv.presentation.common.TvActionButton
import com.neo.tv.presentation.common.TvScreenScaffold
import kotlinx.coroutines.launch

@Composable
fun TvTorrServerSettingsScreen(
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var version by remember { mutableStateOf(prefs.getString("torrserver_version", "136") ?: "136") }
    var autoStart by remember { mutableStateOf(prefs.getBoolean("torrserver_autostart", false)) }
    var status by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }

    suspend fun refreshStatus() {
        val downloaded = TorrServerManager.isServerDownloaded(context)
        val running = TorrServerManager.isServerRunning()
        status =
            when {
                !downloaded -> context.getString(R.string.torrserver_status_not_downloaded)
                running -> context.getString(R.string.torrserver_status_running)
                else -> context.getString(R.string.torrserver_status_stopped)
            }
    }

    LaunchedEffect(Unit) {
        refreshStatus()
    }

    TvScreenScaffold(
        title = stringResource(R.string.settings_torrserver),
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = stringResource(R.string.torrserver_status_label, status))
            Text(text = stringResource(R.string.torrserver_url_label, TorrServerManager.baseUrl()))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = stringResource(R.string.torrserver_version_label))
                TvActionButton(text = version, onClick = {})
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = stringResource(R.string.torrserver_autostart))
                TvActionButton(
                    text = if (autoStart) stringResource(R.string.torrserver_status_running) else stringResource(R.string.torrserver_status_stopped),
                    onClick = {
                        autoStart = !autoStart
                        prefs.edit().putBoolean("torrserver_autostart", autoStart).apply()
                    },
                )
            }

            TvActionButton(
                text = stringResource(R.string.torrserver_start),
                onClick = {
                    scope.launch {
                        isBusy = true
                        TorServerService.start(context)
                        refreshStatus()
                        isBusy = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (isBusy) {
                Text(text = stringResource(R.string.credits_loading))
            }
        }
    }
}
