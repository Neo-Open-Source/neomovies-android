package com.neo.tv.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.neo.neomovies.R
import com.neo.neomovies.data.network.dto.MediaDto

// Fixed row height = card height (240dp) + title (20dp) + spacing (12dp) + top padding (12dp)
private val ROW_HEIGHT = 284.dp

@Composable
fun TvMoviesRow(
    title: String,
    items: List<MediaDto>,
    onMore: () -> Unit,
    onOpenDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardWidth: Dp = 160.dp
    val displayItems = remember(items) { items.take(8) } // reduced from 12 to 8
    val rowState = rememberLazyListState() // preserved across recompositions

    // Fixed height prevents LazyColumn from re-measuring on scroll
    Column(modifier = modifier.fillMaxWidth().height(ROW_HEIGHT)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(displayItems, key = { item ->
                when (val v = item.id) {
                    is Number -> v.toLong()
                    else -> v?.toString() ?: item.hashCode()
                }
            }) { item ->
                val rawId = when (val v = item.id) {
                    is Number -> v.toLong().toString()
                    else -> v?.toString()
                }
                val sourceId = rawId?.let { if (it.contains("_")) it else "kp_$it" }
                TvMovieCard(
                    item = item,
                    modifier = Modifier.width(cardWidth),
                    onClick = { if (sourceId != null) onOpenDetails(sourceId) },
                )
            }
            item {
                Surface(
                    onClick = onMore,
                    modifier = Modifier.width(cardWidth).aspectRatio(2f / 3f),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        Text(text = stringResource(R.string.action_more), style = MaterialTheme.typography.headlineLarge)
                    }
                }
            }
        }
    }
}
