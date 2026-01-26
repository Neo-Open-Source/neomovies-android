package com.neo.neomovies.data.network.dto

import com.squareup.moshi.Json

data class FavoriteDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "userId") val userId: String? = null,
    @Json(name = "kinopoiskId") val kinopoiskId: String? = null,
    @Json(name = "mediaId") val mediaId: String? = null,
    @Json(name = "mediaType") val mediaType: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "nameRu") val nameRu: String? = null,
    @Json(name = "nameEn") val nameEn: String? = null,
    @Json(name = "posterPath") val posterPath: String? = null,
    @Json(name = "posterUrlPreview") val posterUrlPreview: String? = null,
    @Json(name = "year") val year: Int? = null,
    @Json(name = "rating") val rating: Double? = null,
)

data class FavoriteCheckDto(
    @Json(name = "isFavorite") val isFavorite: Boolean = false,
)
