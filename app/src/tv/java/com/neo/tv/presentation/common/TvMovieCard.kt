package com.neo.tv.presentation.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.neo.neomovies.data.network.dto.MediaDto
import com.neo.neomovies.ui.util.normalizeImageUrl

@Composable
fun TvMovieCard(
    item: MediaDto,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
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
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        ) {
            AsyncImage(
                model = normalizeImageUrl(item.posterUrlPreview ?: item.posterUrl ?: item.posterPath),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )
        }
        Text(
            text = item.title ?: item.name ?: item.nameRu ?: "",
            maxLines = 1,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp),
        )
    }
}
