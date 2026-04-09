package com.neo.neomovies.data.network.dto

import com.squareup.moshi.Json

// Matches FavoriteDto from API (camelCase, serde rename_all = "camelCase")
data class FavoriteDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "mediaId") val mediaId: String? = null,
    @Json(name = "mediaType") val mediaType: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "posterUrl") val posterUrl: String? = null,
    @Json(name = "rating") val rating: Double? = null,
    @Json(name = "year") val year: Int? = null,
    @Json(name = "createdAt") val createdAt: String? = null,
)

// API returns { "isFavorite": bool }
data class FavoriteCheckDto(
    @Json(name = "isFavorite") val isFavorite: Boolean = false,
)
