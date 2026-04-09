package com.neo.neomovies.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.neo.neomovies.BuildConfig
import com.neo.neomovies.data.network.dto.MediaDto
import com.neo.neomovies.ui.util.normalizeImageUrl

@Composable
fun MediaPosterCard(
    item: MediaDto,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val title = item.title ?: item.name ?: ""
    val rawId = when (val v = item.id) {
        is Number -> v.toLong().toString()
        else -> v?.toString()
    }
    // API returns posterUrl as relative path like /api/v1/images/kp_small/12345
    val posterRaw = item.posterUrl ?: item.posterPath
    val poster = posterRaw?.let { value ->
        when {
            value.startsWith("http") -> value
            value.startsWith("/") -> BuildConfig.API_BASE_URL.trimEnd('/') + value
            value.startsWith("api/") -> BuildConfig.API_BASE_URL.trimEnd('/') + "/" + value
            else -> normalizeImageUrl(rawId) ?: value
        }
    } ?: normalizeImageUrl(rawId)

    Column(
        modifier = modifier.let { m -> if (onClick != null) m.clickable { onClick() } else m },
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(14.dp)),
        ) {
            Box {
                AsyncImage(
                    model = poster,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Text(
            text = title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
        )
    }
}
