package com.neo.neomovies.data.network.dto

import com.squareup.moshi.Json

data class MediaDto(
    @field:Json(name = "id") val id: Any?,
    @field:Json(name = "kinopoisk_id") val kinopoiskId: Int? = null,
    @field:Json(name = "title") val title: String? = null,
    @field:Json(name = "name") val name: String? = null,
    @field:Json(name = "nameRu") val nameRu: String? = null,
    @field:Json(name = "nameOriginal") val nameOriginal: String? = null,
    @field:Json(name = "posterUrlPreview") val posterUrlPreview: String? = null,
    @field:Json(name = "posterUrl") val posterUrl: String? = null,
    @field:Json(name = "poster_path") val posterPath: String? = null,
    @field:Json(name = "poster") val poster: String? = null,
    @field:Json(name = "ratingKinopoisk") val ratingKinopoisk: Double? = null,
    @field:Json(name = "rating") val rating: Double? = null,
    @field:Json(name = "vote_average") val voteAverage: Double? = null,
    @field:Json(name = "year") val year: Any? = null,
    @field:Json(name = "release_date") val releaseDate: String? = null,
    @field:Json(name = "first_air_date") val firstAirDate: String? = null,
)
