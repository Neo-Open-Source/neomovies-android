package com.neo.neomovies.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.neo.neomovies.R
import com.neo.neomovies.torrserver.TorServerService
import com.neo.neomovies.torrserver.TorrServerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val PREFS_NAME = "settings"
private const val KEY_TORRSERVER_VERSION = "torrserver_version"
private const val KEY_TORRSERVER_AUTOSTART = "torrserver_autostart"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorrServerSettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var version by remember { mutableStateOf(prefs.getString(KEY_TORRSERVER_VERSION, "136") ?: "136") }
    var autoStart by remember { mutableStateOf(prefs.getBoolean(KEY_TORRSERVER_AUTOSTART, false)) }
    var status by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf("") }
    val logsScroll = rememberScrollState()

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

    suspend fun refreshLogs() {
        logs =
            withContext(Dispatchers.IO) {
                val f = File(context.filesDir, "torrserver.log")
                if (!f.exists()) {
                    return@withContext ""
                }
                runCatching {
                    // limit size to keep UI responsive
                    val text = f.readText()
                    if (text.length > 60_000) text.takeLast(60_000) else text
                }.getOrDefault("")
            }
    }

    suspend fun refreshAll() {
        refreshStatus()
        refreshLogs()
    }

    LaunchedEffect(Unit) {
        refreshAll()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_torrserver)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val downloaded = TorrServerManager.isServerDownloaded(context)
            val installedVersion = TorrServerManager.getInstalledVersion(context)
            val showDownloadOrUpdate = !downloaded || (installedVersion != null && installedVersion != version) || (installedVersion == null && downloaded)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(text = stringResource(R.string.torrserver_status_label, status))
                    if (installedVersion != null) {
                        Text(text = stringResource(R.string.torrserver_installed_version_label, installedVersion))
                    }
                    Text(text = stringResource(R.string.torrserver_url_label, TorrServerManager.baseUrl()))
                }
            }

            OutlinedTextField(
                value = version,
                onValueChange = {
                    version = it.filter { ch -> ch.isDigit() }.take(3)
                    prefs.edit().putString(KEY_TORRSERVER_VERSION, version).apply()
                },
                label = { Text(stringResource(R.string.torrserver_version_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.torrserver_autostart),
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = autoStart,
                        onCheckedChange = {
                            autoStart = it
                            prefs.edit().putBoolean(KEY_TORRSERVER_AUTOSTART, it).apply()
                        },
                    )
                }
            }

            if (confirmDelete) {
                AlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    title = { Text(stringResource(R.string.torrserver_delete_title)) },
                    text = { Text(stringResource(R.string.torrserver_delete_message)) },
                    confirmButton = {
                        Button(
                            onClick = {
                                confirmDelete = false
                                scope.launch {
                                    isBusy = true
                                    TorrServerManager.deleteServerFiles(context)
                                    isBusy = false
                                    refreshStatus()
                                }
                            },
                            enabled = !isBusy,
                        ) {
                            Text(stringResource(R.string.torrserver_delete_confirm))
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { confirmDelete = false }, enabled = !isBusy) {
                            Text(stringResource(R.string.torrserver_delete_cancel))
                        }
                    },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (showDownloadOrUpdate) {
                    Button(
                        enabled = !isBusy,
                        onClick = {
                            scope.launch {
                                isBusy = true
                                TorServerService.download(context, version)
                                repeat(120) {
                                    delay(1000)
                                    refreshAll()
                                    if (TorrServerManager.getInstalledVersion(context) == version && TorrServerManager.isServerDownloaded(context)) {
                                        return@repeat
                                    }
                                }
                                isBusy = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        Text(
                            text =
                                if (!downloaded) {
                                    stringResource(R.string.torrserver_download)
                                } else {
                                    stringResource(R.string.torrserver_update)
                                },
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        enabled = !isBusy && downloaded,
                        onClick = {
                            TorServerService.start(context)
                            scope.launch {
                                isBusy = true
                                delay(1200)
                                refreshAll()
                                isBusy = false
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) {
                        Text(text = stringResource(R.string.torrserver_start))
                    }

                    OutlinedButton(
                        enabled = !isBusy,
                        onClick = {
                            TorServerService.stop(context)
                            scope.launch {
                                isBusy = true
                                delay(600)
                                refreshAll()
                                isBusy = false
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) {
                        Text(text = stringResource(R.string.torrserver_stop))
                    }
                }

                OutlinedButton(
                    enabled = !isBusy && downloaded,
                    onClick = { confirmDelete = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text(text = stringResource(R.string.torrserver_delete))
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(text = stringResource(R.string.torrserver_logs_title))
                        OutlinedButton(
                            enabled = !isBusy,
                            onClick = {
                                scope.launch {
                                    isBusy = true
                                    refreshLogs()
                                    isBusy = false
                                }
                            },
                        ) {
                            Text(text = stringResource(R.string.torrserver_logs_refresh))
                        }
                    }

                    if (logs.isBlank()) {
                        Text(text = stringResource(R.string.torrserver_logs_empty))
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .verticalScroll(logsScroll),
                        ) {
                            Text(
                                text = logs,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }

        }
    }
}
