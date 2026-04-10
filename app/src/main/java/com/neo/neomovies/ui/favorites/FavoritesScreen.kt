package com.neo.neomovies.ui.favorites

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
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    val authState by com.neo.neomovies.auth.NeoIdAuthManager.authState().collectAsStateWithLifecycleCompat()
    val isAuthorized = authState.isAuthorized

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
                    // mediaId from API already has "kp_" prefix (e.g. "kp_12345")
                    val sourceId = mediaId?.let { if (it.startsWith("kp_")) it else "kp_$it" }
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
        !this.mediaId.isNullOrBlank() && this.mediaId.removePrefix("kp_").all { it.isDigit() } ->
            this.mediaId.removePrefix("kp_").toLongOrNull() ?: this.mediaId
        else -> this.mediaId
    }
    return MediaDto(
        id = idValue,
        title = this.title,
        posterUrl = this.posterUrl,
        rating = this.rating,
        year = this.year,
    )
}
