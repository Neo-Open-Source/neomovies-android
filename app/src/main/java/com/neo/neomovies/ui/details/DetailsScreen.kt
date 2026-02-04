package com.neo.neomovies.ui.details
 
import android.content.Context
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import com.neo.neomovies.ui.home.collectAsStateWithLifecycleCompat
import com.neo.neomovies.ui.util.normalizeImageUrl
import com.neo.neomovies.R
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    sourceId: String,
    onBack: () -> Unit,
    onWatch: () -> Unit,
) {
    val viewModel: DetailsViewModel = koinViewModel(parameters = { parametersOf(sourceId) })
    val state by viewModel.state.collectAsStateWithLifecycleCompat()

    val context = androidx.compose.ui.platform.LocalContext.current
    val isAuthorized = remember {
        val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
        !prefs.getString("token", null).isNullOrBlank()
    }
    val watchedSummary = state.watchedSummary

    val waitForFavorite = isAuthorized && state.details != null && (state.isFavoriteLoading || state.isFavorite == null)

    val mode = when {
        state.isLoading || waitForFavorite -> DetailsMode.Loading
        state.error != null -> DetailsMode.Error
        state.details != null -> DetailsMode.Content
        else -> DetailsMode.Loading
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.details?.title ?: state.details?.name ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back),
                        )
                    }
                },
                actions = {
                    val detailsLoaded = state.details != null
                    val showFavoriteAction = isAuthorized && detailsLoaded
                    if (showFavoriteAction) {
                        val isFavorite = state.isFavorite == true
                        val enabled = !state.isFavoriteLoading && !state.isFavoriteUpdating
                        val icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder
                        val contentDescription = if (isFavorite) {
                            stringResource(R.string.favorites_remove)
                        } else {
                            stringResource(R.string.favorites_add)
                        }
                        IconButton(onClick = { viewModel.toggleFavorite() }, enabled = enabled) {
                            Icon(imageVector = icon, contentDescription = contentDescription)
                        }
                    }
                },
            )
        },
    ) { padding ->
        Crossfade(targetState = mode, label = "details_mode") { m ->
            when (m) {
                DetailsMode.Loading -> {
                    Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                DetailsMode.Error -> {
                    val error = state.error ?: stringResource(R.string.common_error)
                    Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        Text(text = error)
                    }
                }

                DetailsMode.Content -> {
                    val details = state.details!!
                    val posterId =
                        details.externalIds?.kp?.toString()
                            ?: details.id
                            ?: details.sourceId
                    val posterModel = normalizeImageUrl(posterId)

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    ) {
                        val isTablet = maxWidth >= 720.dp

                        if (isTablet) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .navigationBarsPadding()
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Poster(
                                        posterModel = posterModel,
                                        isTablet = true,
                                        modifier = Modifier.width(260.dp),
                                    )
                                    DetailsBody(
                                        details = details,
                                        watchedSummary = watchedSummary,
                                        onWatch = onWatch,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .navigationBarsPadding()
                                    .padding(vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Poster(
                                    posterModel = posterModel,
                                    isTablet = false,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                DetailsBody(
                                    details = details,
                                    watchedSummary = watchedSummary,
                                    onWatch = onWatch,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Poster(
    posterModel: Any?,
    isTablet: Boolean,
    modifier: Modifier,
) {
    val posterHeight = if (isTablet) 380.dp else 360.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(posterHeight)
            .clip(if (isTablet) RoundedCornerShape(20.dp) else RoundedCornerShape(0.dp)),
    ) {
        AsyncImage(
            model = posterModel,
            contentDescription = null,
            contentScale = if (isTablet) ContentScale.Fit else ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        if (!isTablet) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                MaterialTheme.colorScheme.surface,
                            ),
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun DetailsBody(
    details: com.neo.neomovies.data.network.dto.MediaDetailsDto,
    watchedSummary: WatchedSummary?,
    onWatch: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val title = details.title ?: details.name ?: ""
        val separator = stringResource(R.string.common_separator_dot)
        val metaParts = mutableListOf<String>()
        val year = details.releaseDate?.take(4)
        if (!year.isNullOrBlank()) metaParts.add(year)
        if (!details.country.isNullOrBlank()) metaParts.add(details.country)
        if (details.duration != null && details.duration > 0) {
            metaParts.add(stringResource(R.string.details_duration_minutes, details.duration))
        }
        if (details.rating != null && details.rating > 0) {
            metaParts.add(stringResource(R.string.details_rating_format, details.rating))
        }
        val meta = metaParts.joinToString(separator)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onWatch) {
                Text(text = stringResource(R.string.action_watch))
            }
        }
        if (meta.isNotBlank()) {
            Text(text = meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        val genres = details.genres?.mapNotNull { it.name?.trim() }?.filter { it.isNotBlank() }.orEmpty()
        if (genres.isNotEmpty()) {
            Text(
                text = genres.joinToString(separator),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        details.description?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, style = MaterialTheme.typography.bodyLarge)
        }

        watchedSummary?.let { summary ->
            if (summary.watchedCount > 0) {
                Text(
                    text = "Просмотрено серий: ${summary.watchedCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (summary.lastSeason > 0 && summary.lastEpisode > 0) {
                val progress = if (summary.lastDuration > 0) {
                    ((summary.lastPosition.toFloat() / summary.lastDuration) * 100).toInt()
                } else null
                val progressSuffix = progress?.let { " • ${it}%" }.orEmpty()
                Text(
                    text = "Остановились: S%02dE%02d%s".format(summary.lastSeason, summary.lastEpisode, progressSuffix),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private enum class DetailsMode {
    Loading,
    Error,
    Content,
}
