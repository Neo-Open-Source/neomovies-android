package com.neo.tv.presentation.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.neo.neomovies.BuildConfig
import com.neo.neomovies.data.network.dto.MediaDto
import com.neo.neomovies.ui.util.normalizeImageUrl

@Composable
fun TvMovieCard(
    item: MediaDto,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val context = LocalContext.current

    // Build URL and ImageRequest once per item, not on every recomposition
    val imageRequest = remember(item.id) {
        val rawId = when (val v = item.id) {
            is Number -> v.toLong().toString()
            else -> v?.toString()
        }
        val posterRaw = item.posterUrl ?: item.posterPath
        val poster = posterRaw?.let { value ->
            when {
                value.startsWith("http") -> value
                value.startsWith("/") -> BuildConfig.API_BASE_URL.trimEnd('/') + value
                value.startsWith("api/") -> BuildConfig.API_BASE_URL.trimEnd('/') + "/" + value
                else -> normalizeImageUrl(rawId) ?: value
            }
        } ?: normalizeImageUrl(rawId)

        ImageRequest.Builder(context)
            .data(poster)
            .size(Size(320, 480)) // decode at display size, not full resolution
            .crossfade(false)     // skip animation on weak hardware
            .memoryCacheKey("tv_card_${item.id}")
            .build()
    }

    Column(modifier = modifier) {
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
                    shape = RoundedCornerShape(12.dp),
                ),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f), // no scale animation
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null, // not needed for performance
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
            )
        }
        Text(
            text = item.title ?: item.name ?: "",
            maxLines = 1,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
        )
    }
}
