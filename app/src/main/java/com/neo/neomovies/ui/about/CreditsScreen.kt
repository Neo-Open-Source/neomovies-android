package com.neo.neomovies.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.neo.neomovies.R
import com.neo.neomovies.ui.home.collectAsStateWithLifecycleCompat
import org.koin.androidx.compose.koinViewModel

private data class StaticCreditItem(
    val name: String,
    val description: String,
    val url: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditsScreen(
    onBack: () -> Unit,
    viewModel: CreditsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycleCompat()
    val uriHandler = LocalUriHandler.current

    val libraries =
        listOf(
            StaticCreditItem(
                name = "AndroidX Media3 (ExoPlayer)",
                description = "Player engine",
                url = "https://github.com/androidx/media",
            ),
            StaticCreditItem(
                name = "libmpv-android",
                description = "MPV integration",
                url = "https://github.com/mpv-android/libmpv-android",
            ),
            StaticCreditItem(
                name = "Koin",
                description = "Dependency injection",
                url = "https://github.com/InsertKoinIO/koin",
            ),
            StaticCreditItem(
                name = "Retrofit",
                description = "Networking",
                url = "https://github.com/square/retrofit",
            ),
            StaticCreditItem(
                name = "OkHttp",
                description = "HTTP client",
                url = "https://github.com/square/okhttp",
            ),
            StaticCreditItem(
                name = "Moshi",
                description = "JSON",
                url = "https://github.com/square/moshi",
            ),
            StaticCreditItem(
                name = "Coil",
                description = "Image loading",
                url = "https://github.com/coil-kt/coil",
            ),
            StaticCreditItem(
                name = "Jsoup",
                description = "HTML parsing",
                url = "https://jsoup.org/",
            ),
        )

    val thanks =
        listOf(
            StaticCreditItem(
                name = "Findroid",
                description = "Player UX ideas / code reference",
                url = "https://github.com/jarnedemeulemeester/findroid",
            ),
            StaticCreditItem(
                name = "Seal",
                description = "Animations and many UI/UX reference",
                url = "https://github.com/JunkFood02/Seal",
            ),
            StaticCreditItem(
                name = "FloraFilmsV2",
                description = "TorrServer and Jacred API code reference",
                url = "https://github.com/varnavsky07rus/FloraFilmV2",
            ),
        )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.credits_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
            )
        },
    ) { padding ->
        if (state.items.isEmpty() && state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.credits_loading))
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = stringResource(R.string.credits_section_libraries),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                items(libraries) { item ->
                    Card(
                        modifier =
                            Modifier.padding(horizontal = 16.dp)
                                .clickable { uriHandler.openUri(item.url) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
                        )
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 14.dp),
                        )
                    }
                }

                item {
                    Text(
                        text = stringResource(R.string.credits_section_thanks),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                items(thanks) { item ->
                    Card(
                        modifier =
                            Modifier.padding(horizontal = 16.dp)
                                .clickable { uriHandler.openUri(item.url) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
                        )
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 14.dp),
                        )
                    }
                }

                item {
                    Text(
                        text = stringResource(R.string.credits_section_supporters),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                if (state.fromCache) {
                    item {
                        Text(
                            text = stringResource(R.string.credits_offline_cache),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }

                items(state.items) { item ->
                    Card(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
                        )
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
                        )
                        val contrib = item.contributions.joinToString(" • ")
                        if (contrib.isNotBlank()) {
                            Text(
                                text = contrib,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp),
                            )
                        }
                    }
                }

                if (state.error != null && state.items.isEmpty()) {
                    item {
                        Text(
                            text = state.error ?: "Ошибка",
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}
