package com.neo.tv.presentation.favorites

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.neo.neomovies.R
import com.neo.neomovies.ui.favorites.FavoritesViewModel
import com.neo.neomovies.ui.home.collectAsStateWithLifecycleCompat
import com.neo.tv.presentation.common.TvActionButton
import com.neo.tv.presentation.common.TvMovieCard
import com.neo.tv.presentation.common.TvScreenScaffold
import org.koin.androidx.compose.koinViewModel

@Composable
fun TvFavoritesScreen(
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenDetails: (String) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isAuthorized = remember {
        val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
        !prefs.getString("token", null).isNullOrBlank()
    }

    if (!isAuthorized) {
        TvScreenScaffold(
            title = stringResource(R.string.tab_favorites),
            onBack = onBack,
        ) { padding ->
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                TvActionButton(
                    text = stringResource(R.string.favorites_go_to_profile),
                    onClick = onOpenProfile,
                )
            }
        }
        return
    }

    val viewModel: FavoritesViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycleCompat()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.load()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    TvScreenScaffold(
        title = stringResource(R.string.tab_favorites),
        onBack = onBack,
    ) { padding ->
        when {
            state.isLoading -> {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) { androidx.tv.material3.Text(text = stringResource(R.string.credits_loading)) }
            }
            state.error != null -> {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) { androidx.tv.material3.Text(text = state.error ?: stringResource(R.string.common_error)) }
            }
            state.items.isEmpty() -> {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) { androidx.tv.material3.Text(text = stringResource(R.string.favorites_empty)) }
            }
            else -> {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    FavoritesFilterRow(modifier = Modifier.fillMaxWidth())
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 180.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        items(state.items) { item ->
                            val mediaId = item.mediaId
                            val sourceId = mediaId?.let { "kp_$it" }
                            val media = item.toMediaDto()
                            TvMovieCard(
                                item = media,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { if (sourceId != null) onOpenDetails(sourceId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritesFilterRow(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.tab_favorites),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(end = 24.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Surface(
                    onClick = {},
                    shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.small),
                    colors = ClickableSurfaceDefaults.colors(),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                ) {
                    Text(
                        text = stringResource(R.string.tab_favorites),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }
    }
}

private fun com.neo.neomovies.data.network.dto.FavoriteDto.toMediaDto(): com.neo.neomovies.data.network.dto.MediaDto {
    val idValue: Any? = when {
        !this.mediaId.isNullOrBlank() && this.mediaId.all { it.isDigit() } -> this.mediaId.toLong()
        else -> this.mediaId
    }

    val posterPreview = this.posterUrlPreview ?: this.posterPath

    return com.neo.neomovies.data.network.dto.MediaDto(
        id = idValue,
        kinopoiskId = this.mediaId?.toIntOrNull(),
        title = this.title ?: this.nameRu,
        name = null,
        nameRu = this.nameRu,
        nameOriginal = this.nameEn,
        posterUrlPreview = posterPreview,
        posterUrl = this.posterPath,
        posterPath = this.posterPath,
        poster = null,
        ratingKinopoisk = this.rating,
        rating = this.rating,
        voteAverage = this.rating,
        year = this.year,
        releaseDate = null,
        firstAirDate = null,
    )
}
