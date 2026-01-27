package com.neo.neomovies.ui.about

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.neo.neomovies.BuildConfig
import com.neo.neomovies.R
import com.neo.neomovies.ui.components.PreferenceItem

private const val telegramChannelUrl = "https://t.me/neomovies_news"
private const val latestReleaseUrl = "https://github.com/Neo-Open-Source/neomovies-android/releases/latest"
private const val preReleasesUrl = "https://github.com/Neo-Open-Source/neomovies-android/releases"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
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

            item {
                PreferenceItem(
                    title = stringResource(R.string.about_telegram_channel),
                    description = stringResource(R.string.about_telegram_channel_desc),
                    painter = painterResource(id = R.drawable.icons8_telegram_app),
                ) { uriHandler.openUri(telegramChannelUrl) }
            }

            item {
                PreferenceItem(
                    title = stringResource(R.string.about_credits),
                    description = stringResource(R.string.about_credits_desc),
                    icon = Icons.Outlined.AutoAwesome,
                ) { onOpenCredits() }
            }

            item {
                PreferenceItem(
                    title = stringResource(R.string.about_changes),
                    description = stringResource(R.string.about_changes_desc),
                    icon = Icons.Outlined.ReceiptLong,
                ) { onOpenChanges() }
            }

            item {
                PreferenceItem(
                    title = stringResource(R.string.about_version),
                    description = versionName,
                    icon = Icons.Outlined.Info,
                ) {}
            }
        }
    }
}
