package com.neo.neomovies.data.network.dto

import com.squareup.moshi.Json

data class MediaDto(
    @Json(name = "id") val id: Any?,
    // API v1 fields (SearchResultItem)
    @Json(name = "title") val title: String? = null,
    @Json(name = "originalTitle") val originalTitle: String? = null,
    @Json(name = "year") val year: Any? = null,
    @Json(name = "rating") val rating: Double? = null,
    @Json(name = "posterUrl") val posterUrl: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "genres") val genres: List<GenreDto>? = null,
    @Json(name = "externalIds") val externalIds: ExternalIdsDto? = null,
    // legacy fallback fields
    @Json(name = "name") val name: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
)
