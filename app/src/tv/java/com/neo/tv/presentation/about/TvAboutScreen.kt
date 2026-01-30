package com.neo.tv.presentation.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.neo.neomovies.BuildConfig
import com.neo.neomovies.R
import com.neo.tv.presentation.common.TvScreenScaffold

private const val telegramChannelUrl = "https://t.me/neomovies_news"
private const val latestReleaseUrl = "https://github.com/Neo-Open-Source/neomovies-android/releases/latest"
private const val preReleasesUrl = "https://github.com/Neo-Open-Source/neomovies-android/releases"

@Composable
fun TvAboutScreen(
    onBack: () -> Unit,
    onOpenCredits: () -> Unit,
    onOpenChanges: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val versionName = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: ""

    TvScreenScaffold(
        title = stringResource(R.string.about_title),
        onBack = onBack,
        contentPadding = PaddingValues(horizontal = 72.dp, vertical = 16.dp),
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AboutItem(
                title = stringResource(R.string.about_latest_release),
                description = stringResource(R.string.about_latest_release_desc),
                onClick = { uriHandler.openUri(latestReleaseUrl) },
            )

            if (BuildConfig.PRE_RELEASE) {
                AboutItem(
                    title = stringResource(R.string.about_prereleases),
                    description = stringResource(R.string.about_prereleases_desc),
                    onClick = { uriHandler.openUri(preReleasesUrl) },
                )
            }

            AboutItem(
                title = stringResource(R.string.about_telegram_channel),
                description = stringResource(R.string.about_telegram_channel_desc),
                onClick = { uriHandler.openUri(telegramChannelUrl) },
            )

            AboutItem(
                title = stringResource(R.string.about_credits),
                description = stringResource(R.string.about_credits_desc),
                onClick = onOpenCredits,
            )

            AboutItem(
                title = stringResource(R.string.about_changes),
                description = stringResource(R.string.about_changes_desc),
                onClick = onOpenChanges,
            )

            AboutItem(
                title = stringResource(R.string.about_settings),
                description = stringResource(R.string.about_settings_desc),
                onClick = onOpenSettings,
            )

            AboutItem(
                title = stringResource(R.string.about_version),
                description = versionName,
                onClick = {},
            )
        }
    }
}

@Composable
private fun AboutItem(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.fillMaxWidth(),
        selected = false,
        onClick = onClick,
        headlineContent = { Text(text = title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(text = description) },
        trailingContent = { Text(text = stringResource(R.string.action_more)) },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
        ),
        shape = ListItemDefaults.shape(shape = MaterialTheme.shapes.medium),
    )
}
