package com.neo.neomovies.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neo.neomovies.ui.components.MediaPosterCard
import com.neo.neomovies.ui.navigation.CategoryType
import org.koin.androidx.compose.koinViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search

@Composable
fun HomeScreen(
    onOpenCategory: (CategoryType) -> Unit,
    onOpenDetails: (String) -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycleCompat()

    val mode = when {
        state.isLoading -> HomeMode.Loading
        state.error != null -> HomeMode.Error
        else -> HomeMode.Content
    }

    Crossfade(targetState = mode, label = "home_mode") { m ->
        when (m) {
            HomeMode.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            HomeMode.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = state.error ?: "Ошибка")
                }
            }

            HomeMode.Content -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "NeoMovies",
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = onOpenSearch) {
                                Icon(imageVector = Icons.Filled.Search, contentDescription = "Search")
                            }
                        }
                    }

                    item {
                        HomeSection(
                            title = "Популярное",
                            items = state.popular,
                            onMore = { onOpenCategory(CategoryType.POPULAR) },
                            onOpenDetails = onOpenDetails,
                        )
                    }

                    item {
                        HomeSection(
                            title = "Топ фильмов",
                            items = state.topMovies,
                            onMore = { onOpenCategory(CategoryType.TOP_MOVIES) },
                            onOpenDetails = onOpenDetails,
                        )
                    }

                    item {
                        HomeSection(
                            title = "Топ сериалов",
                            items = state.topTv,
                            onMore = { onOpenCategory(CategoryType.TOP_TV) },
                            onOpenDetails = onOpenDetails,
                        )
                    }
                }
            }
        }
    }
}

private enum class HomeMode {
    Loading,
    Error,
    Content,
}

@Composable
private fun HomeSection(
    title: String,
    items: List<com.neo.neomovies.data.network.dto.MediaDto>,
    onMore: () -> Unit,
    onOpenDetails: (String) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cardWidth: Dp = when {
            maxWidth >= 900.dp -> 200.dp
            maxWidth >= 600.dp -> 170.dp
            else -> 130.dp
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onMore, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = "More",
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items.take(12)) { item ->
                    val rawId = when (val v = item.id) {
                        is Number -> v.toLong().toString()
                        else -> v?.toString()
                    }
                    val sourceId = rawId?.let { if (it.contains("_")) it else "kp_$it" }
                    MediaPosterCard(
                        item = item,
                        modifier = Modifier.width(cardWidth),
                        onClick = { if (sourceId != null) onOpenDetails(sourceId) },
                    )
                }
            }
        }
    }
}
