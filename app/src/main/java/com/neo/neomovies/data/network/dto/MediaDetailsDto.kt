package com.neo.neomovies.data.network.dto

import com.squareup.moshi.Json

data class MediaDetailsDto(
    @field:Json(name = "id") val id: String? = null,
    @field:Json(name = "sourceId") val sourceId: String? = null,
    @field:Json(name = "title") val title: String? = null,
    @field:Json(name = "name") val name: String? = null,
    @field:Json(name = "originalTitle") val originalTitle: String? = null,
    @field:Json(name = "description") val description: String? = null,
    @field:Json(name = "releaseDate") val releaseDate: String? = null,
    @field:Json(name = "type") val type: String? = null,
    @field:Json(name = "genres") val genres: List<GenreDto>? = null,
    @field:Json(name = "rating") val rating: Double? = null,
    @field:Json(name = "posterUrl") val posterUrl: String? = null,
    @field:Json(name = "backdropUrl") val backdropUrl: String? = null,
    @field:Json(name = "duration") val duration: Int? = null,
    @field:Json(name = "country") val country: String? = null,
    @field:Json(name = "language") val language: String? = null,
    @field:Json(name = "externalIds") val externalIds: ExternalIdsDto? = null,
)

data class GenreDto(
    @field:Json(name = "id") val id: String? = null,
    @field:Json(name = "name") val name: String? = null,
)

data class ExternalIdsDto(
    @field:Json(name = "kp") val kp: Int? = null,
    @field:Json(name = "tmdb") val tmdb: Int? = null,
    @field:Json(name = "imdb") val imdb: String? = null,
)
