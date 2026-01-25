package com.neo.neomovies.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neo.neomovies.ui.components.MediaPosterCard
import com.neo.neomovies.ui.home.collectAsStateWithLifecycleCompat
import org.koin.androidx.compose.koinViewModel

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenDetails: (String) -> Unit,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycleCompat()
    val listState = rememberLazyGridState()

    val reachedBottom: Boolean by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem?.index != 0 && lastVisibleItem?.index == listState.layoutInfo.totalItemsCount - 1
        }
    }

    LaunchedEffect(reachedBottom) {
        if (reachedBottom) viewModel.loadNextPage()
    }

    val mode = when {
        state.isLoading -> SearchMode.Loading
        state.error != null -> SearchMode.Error
        state.query.isBlank() -> SearchMode.Idle
        state.items.isEmpty() -> SearchMode.Empty
        else -> SearchMode.Content
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Поиск") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
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
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Введите название") },
                singleLine = true,
            )

            Crossfade(targetState = mode, label = "search_mode") { m ->
                when (m) {
                    SearchMode.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    SearchMode.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = state.error ?: "Ошибка", color = MaterialTheme.colorScheme.error)
                                Button(onClick = viewModel::retry, modifier = Modifier.padding(top = 12.dp)) {
                                    Text("Повторить")
                                }
                            }
                        }
                    }

                    SearchMode.Idle -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = "Начните вводить запрос", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    SearchMode.Empty -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = "Ничего не найдено", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    SearchMode.Content -> {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 140.dp),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                items(state.items) { item ->
                                    val rawId = when (val v = item.id) {
                                        is Number -> v.toLong().toString()
                                        else -> v?.toString()
                                    }
                                    val sourceId = rawId?.let { if (it.contains("_")) it else "kp_$it" }

                                    MediaPosterCard(
                                        item = item,
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = { if (sourceId != null) onOpenDetails(sourceId) },
                                    )
                                }

                                items(listOf(Unit)) {
                                    if (state.totalPages > 1) {
                                        PaginationRow(
                                            page = state.page,
                                            totalPages = state.totalPages,
                                            onPrev = { viewModel.setPage(state.page - 1) },
                                            onNext = { viewModel.setPage(state.page + 1) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class SearchMode {
    Loading,
    Error,
    Idle,
    Empty,
    Content,
}

@Composable
private fun PaginationRow(
    page: Int,
    totalPages: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onPrev, enabled = page > 1) {
            Text("Назад")
        }
        Text(text = "$page / $totalPages")
        TextButton(onClick = onNext, enabled = page < totalPages) {
            Text("Вперёд")
        }
    }
}
