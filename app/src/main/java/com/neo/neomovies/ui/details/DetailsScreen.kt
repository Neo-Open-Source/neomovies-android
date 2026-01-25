package com.neo.neomovies.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.neo.neomovies.ui.home.collectAsStateWithLifecycleCompat
import com.neo.neomovies.ui.util.normalizeImageUrl
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    sourceId: String,
    onBack: () -> Unit,
) {
    val viewModel: DetailsViewModel = koinViewModel(parameters = { parametersOf(sourceId) })
    val state by viewModel.state.collectAsStateWithLifecycleCompat()

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
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            state.error != null -> {
                val error = state.error ?: "Ошибка"
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(text = error)
                }
            }

            state.details != null -> {
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
                    val isTablet = this.maxWidth >= 600.dp

                    if (isTablet) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Poster(posterModel = posterModel, modifier = Modifier.width(260.dp))
                            DetailsBody(details = details, modifier = Modifier.weight(1f))
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Poster(posterModel = posterModel, modifier = Modifier.fillMaxWidth())
                            DetailsBody(details = details, modifier = Modifier.fillMaxWidth())
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
    modifier: Modifier,
) {
    AsyncImage(
        model = posterModel,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxWidth()
            .height(360.dp),
    )
}

@Composable
private fun DetailsBody(
    details: com.neo.neomovies.data.network.dto.MediaDetailsDto,
    modifier: Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val title = details.title ?: details.name ?: ""
        val meta = buildString {
            val year = details.releaseDate?.take(4)
            if (!year.isNullOrBlank()) append(year)
            if (!details.country.isNullOrBlank()) {
                if (isNotEmpty()) append(" • ")
                append(details.country)
            }
            if (details.duration != null && details.duration > 0) {
                if (isNotEmpty()) append(" • ")
                append("${details.duration} мин")
            }
            if (details.rating != null && details.rating > 0) {
                if (isNotEmpty()) append(" • ")
                append("★ ${details.rating}")
            }
        }

        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        if (meta.isNotBlank()) {
            Text(text = meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        val genres = details.genres?.mapNotNull { it.name?.trim() }?.filter { it.isNotBlank() }.orEmpty()
        if (genres.isNotEmpty()) {
            Text(
                text = genres.joinToString(" • "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        details.description?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
