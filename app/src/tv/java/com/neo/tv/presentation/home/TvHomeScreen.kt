package com.neo.tv.presentation.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neo.neomovies.R
import com.neo.neomovies.ui.home.HomeViewModel
import com.neo.neomovies.ui.home.collectAsStateWithLifecycleCompat
import com.neo.neomovies.ui.navigation.CategoryType
import com.neo.tv.presentation.common.TvMoviesRow
import org.koin.androidx.compose.koinViewModel

@Composable
fun TvHomeScreen(
    onOpenCategory: (CategoryType) -> Unit,
    onOpenDetails: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onScroll: (Boolean) -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycleCompat()
    val listState = rememberLazyListState()

    val shouldShowTopBar by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 300
        }
    }

    LaunchedEffect(shouldShowTopBar) { onScroll(shouldShowTopBar) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 108.dp),
    ) {
        item(key = "popular") {
            TvMoviesRow(
                title = stringResource(R.string.home_section_popular),
                items = state.popular,
                onMore = { onOpenCategory(CategoryType.POPULAR) },
                onOpenDetails = onOpenDetails,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        item(key = "top_movies") {
            TvMoviesRow(
                title = stringResource(R.string.home_section_top_movies),
                items = state.topMovies,
                onMore = { onOpenCategory(CategoryType.TOP_MOVIES) },
                onOpenDetails = onOpenDetails,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        item(key = "top_tv") {
            TvMoviesRow(
                title = stringResource(R.string.home_section_top_tv),
                items = state.topTv,
                onMore = { onOpenCategory(CategoryType.TOP_TV) },
                onOpenDetails = onOpenDetails,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
