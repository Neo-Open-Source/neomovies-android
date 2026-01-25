package com.neo.neomovies.data.network.dto

import com.squareup.moshi.Json

data class MediaDto(
    @Json(name = "id") val id: Any?,
    @Json(name = "kinopoisk_id") val kinopoiskId: Int? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "nameRu") val nameRu: String? = null,
    @Json(name = "nameOriginal") val nameOriginal: String? = null,
    @Json(name = "posterUrlPreview") val posterUrlPreview: String? = null,
    @Json(name = "posterUrl") val posterUrl: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "poster") val poster: String? = null,
    @Json(name = "ratingKinopoisk") val ratingKinopoisk: Double? = null,
    @Json(name = "rating") val rating: Double? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "year") val year: Any? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null,
)
