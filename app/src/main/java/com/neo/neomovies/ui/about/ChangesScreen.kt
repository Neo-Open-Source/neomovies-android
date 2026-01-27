package com.neo.neomovies.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neo.neomovies.BuildConfig
import com.neo.neomovies.R
import com.neo.neomovies.ui.components.PreferenceItem

private const val latestReleaseUrl = "https://github.com/Neo-Open-Source/neomovies-android/releases/latest"
private const val preReleasesUrl = "https://github.com/Neo-Open-Source/neomovies-android/releases"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangesScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val versionName = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: ""

    val historyRaw = stringArrayResource(R.array.changes_history)
    val history =
        historyRaw.mapNotNull { raw ->
            val parts = raw.split("||", limit = 2)
            val v = parts.getOrNull(0)?.trim().orEmpty()
            val notes = parts.getOrNull(1)?.trim().orEmpty()
            if (v.isBlank() || notes.isBlank()) null else v to notes
        }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.changes_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.changes_version_label, versionName),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            items(history) { (ver, notes) ->
                val isCurrent = ver == versionName
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                if (isCurrent) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                        ),
                ) {
                    Text(
                        text = ver,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
                    )
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 14.dp),
                    )
                }
            }

            item {
                PreferenceItem(
                    title = stringResource(R.string.about_latest_release),
                    description = stringResource(R.string.about_latest_release_desc),
                    icon = Icons.Outlined.NewReleases,
                ) { uriHandler.openUri(latestReleaseUrl) }
            }

            if (BuildConfig.PRE_RELEASE) {
                item {
                    PreferenceItem(
                        title = stringResource(R.string.about_prereleases),
                        description = stringResource(R.string.about_prereleases_desc),
                        icon = Icons.Outlined.NewReleases,
                    ) { uriHandler.openUri(preReleasesUrl) }
                }
            }
        }
    }
}
