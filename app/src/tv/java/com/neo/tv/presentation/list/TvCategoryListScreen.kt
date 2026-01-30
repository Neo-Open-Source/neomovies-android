package com.neo.tv.presentation.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.neo.neomovies.R
import com.neo.neomovies.ui.home.collectAsStateWithLifecycleCompat
import com.neo.neomovies.ui.list.CategoryListViewModel
import com.neo.neomovies.ui.navigation.CategoryType
import com.neo.tv.presentation.common.TvMovieCard
import com.neo.tv.presentation.common.TvScreenScaffold
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun TvCategoryListScreen(
    categoryType: CategoryType,
    onBack: () -> Unit,
    onOpenDetails: (String) -> Unit,
) {
    val viewModel: CategoryListViewModel = koinViewModel(parameters = { parametersOf(categoryType) })
    val state by viewModel.state.collectAsStateWithLifecycleCompat()
    val listState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    val shouldLoadNextPage: Boolean by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 6
        }
    }

    LaunchedEffect(shouldLoadNextPage) {
        if (shouldLoadNextPage) viewModel.loadNextPage()
    }

    TvScreenScaffold(
        title = stringResource(categoryType.titleRes),
        onBack = onBack,
    ) { padding ->
        when {
            state.isLoading -> {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = stringResource(R.string.credits_loading))
                }
            }
            state.error != null -> {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = state.error ?: stringResource(R.string.common_error))
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 180.dp),
                    modifier = Modifier.fillMaxSize().padding(padding),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    items(state.items) { item ->
                        val rawId = when (val v = item.id) {
                            is Number -> v.toLong().toString()
                            else -> v?.toString()
                        }
                        val sourceId = rawId?.let { if (it.contains("_")) it else "kp_$it" }
                        TvMovieCard(
                            item = item,
                            modifier = Modifier,
                            onClick = { if (sourceId != null) onOpenDetails(sourceId) },
                        )
                    }
                }
            }
        }
    }
}
