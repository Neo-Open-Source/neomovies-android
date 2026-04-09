package com.neo.neomovies.ui.util

import com.neo.neomovies.data.network.dto.MediaDto

fun filterValidMovies(items: List<MediaDto>): List<MediaDto> {
    return items.filter { item ->
        val hasPoster = !item.posterUrl.isNullOrBlank() &&
            !item.posterUrl.contains("no-poster", ignoreCase = true)

        val hasTitle = !item.title.isNullOrBlank() || !item.name.isNullOrBlank()

        val hasRating = item.rating != null && item.rating > 0.0

        hasPoster && hasTitle && hasRating
    }
}
