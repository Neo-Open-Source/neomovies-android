package com.neo.tv.presentation.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.neo.neomovies.R
import com.neo.neomovies.ui.details.DetailsViewModel
import com.neo.neomovies.ui.home.collectAsStateWithLifecycleCompat
import com.neo.neomovies.ui.util.normalizeImageUrl
import com.neo.tv.presentation.common.TvActionButton
import com.neo.tv.presentation.common.TvScreenScaffold
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun TvDetailsScreen(
    sourceId: String,
    onBack: () -> Unit,
    onWatch: () -> Unit,
) {
    val viewModel: DetailsViewModel = koinViewModel(parameters = { parametersOf(sourceId) })
    val state by viewModel.state.collectAsStateWithLifecycleCompat()
    val context = androidx.compose.ui.platform.LocalContext.current
    val isAuthorized = remember {
        val prefs = context.getSharedPreferences("auth", android.content.Context.MODE_PRIVATE)
        !prefs.getString("token", null).isNullOrBlank()
    }

    val title = state.details?.title
        ?: state.details?.name
        ?: state.details?.originalTitle
        ?: ""

    TvScreenScaffold(
        title = title.ifBlank { stringResource(R.string.details_back) },
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
            state.details != null -> {
                val details = state.details!!
                val posterModel = resolveDetailsImageUrl(details.posterUrl)
                    ?: resolveDetailsImageUrl(details.backdropUrl)
                    ?: resolveDetailsImageUrl(details.externalIds?.kp?.toString())
                Row(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                ) {
                    AsyncImage(
                        model = posterModel,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(260.dp)
                            .fillMaxHeight()
                            .weight(0.35f, fill = false)
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(16.dp)),
                    )
                    Column(
                        modifier = Modifier.weight(0.65f),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val metaParts = buildList {
                            details.releaseDate?.take(4)?.let { add(it) }
                            details.country?.let { add(it) }
                            details.duration?.let { add(stringResource(R.string.details_duration_minutes, it)) }
                            details.rating?.let { add(stringResource(R.string.details_rating_format, it)) }
                        }
                        if (metaParts.isNotEmpty()) {
                            Text(
                                text = metaParts.joinToString(stringResource(R.string.common_separator_dot)),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            text = details.genres?.joinToString(" • ") { it.name ?: "" }.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = details.description ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            TvActionButton(
                                text = stringResource(R.string.action_watch),
                                onClick = onWatch,
                            )
                            if (isAuthorized) {
                                val favLabel = if (state.isFavorite == true) {
                                    stringResource(R.string.favorites_remove)
                                } else {
                                    stringResource(R.string.favorites_add)
                                }
                                TvActionButton(
                                    text = favLabel,
                                    onClick = { viewModel.toggleFavorite() },
                                )
                            }
                        }
                        if (state.isFavoriteLoading || state.isFavoriteUpdating) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = stringResource(R.string.credits_loading))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun resolveDetailsImageUrl(value: String?): String? {
    if (value.isNullOrBlank()) return null
    val v = value.trim()
    if (v.startsWith("http://") || v.startsWith("https://")) return v
    return normalizeImageUrl(v)
}
