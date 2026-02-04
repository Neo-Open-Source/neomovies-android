package com.neo.neomovies.ui.favorites

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.neo.neomovies.R
import com.neo.neomovies.data.network.dto.MediaDto
import com.neo.neomovies.ui.components.MediaPosterCard
import com.neo.neomovies.ui.home.collectAsStateWithLifecycleCompat
import org.koin.androidx.compose.koinViewModel

@Composable
fun FavoritesScreen(
    onOpenProfile: () -> Unit,
    onOpenDetails: (String) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isAuthorized = remember {
        val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
        !prefs.getString("token", null).isNullOrBlank()
    }

    if (!isAuthorized) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.favorites_auth_required),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = onOpenProfile) {
                    Text(text = stringResource(R.string.favorites_go_to_profile))
                }
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
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    when {
        state.isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        state.error != null -> {
            val error = state.error ?: stringResource(R.string.common_error)
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = error)
            }
        }

        state.items.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.favorites_empty))
            }
        }

        else -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.items) { item ->
                    val media = item.toMediaDto()
                    val mediaId = item.mediaId
                    val sourceId = mediaId?.let { "kp_$it" }
                    MediaPosterCard(
                        item = media,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { if (sourceId != null) onOpenDetails(sourceId) },
                    )
                }
            }
        }
    }
}

private fun com.neo.neomovies.data.network.dto.FavoriteDto.toMediaDto(): MediaDto {
    val idValue: Any? = when {
        !this.mediaId.isNullOrBlank() && this.mediaId.all { it.isDigit() } -> this.mediaId.toLong()
        else -> this.mediaId
    }

    val posterPreview = this.posterUrlPreview
        ?: this.posterPath

    return MediaDto(
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
import androidx.lifecycle.compose.LocalLifecycleOwner
