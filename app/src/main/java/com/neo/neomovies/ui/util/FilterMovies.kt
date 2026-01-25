package com.neo.neomovies.ui.util

import com.neo.neomovies.data.network.dto.MediaDto

fun filterValidMovies(items: List<MediaDto>): List<MediaDto> {
    return items.filter { item ->
        val posterCandidate = item.posterPath ?: item.posterUrlPreview ?: item.posterUrl

        val hasPoster = !posterCandidate.isNullOrBlank() &&
            !posterCandidate.contains("no-poster", ignoreCase = true)

        val hasTitle = !(
            item.title.isNullOrBlank() &&
                item.name.isNullOrBlank() &&
                item.nameRu.isNullOrBlank() &&
                item.nameOriginal.isNullOrBlank()
            )

        val hasYear = !(
            item.releaseDate.isNullOrBlank() &&
                item.firstAirDate.isNullOrBlank() &&
                item.year == null
            )

        val hasRating =
            (item.voteAverage != null && item.voteAverage > 0.0) ||
                (item.ratingKinopoisk != null && item.ratingKinopoisk > 0.0)

        hasPoster && hasTitle && hasYear && hasRating
    }
}
