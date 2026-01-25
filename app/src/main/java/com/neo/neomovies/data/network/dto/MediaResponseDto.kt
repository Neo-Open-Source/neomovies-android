package com.neo.neomovies.data.network.dto

import com.squareup.moshi.Json

data class MediaResponseDto(
    @Json(name = "page") val page: Int? = null,
    @Json(name = "results") val results: List<MediaDto> = emptyList(),
    @Json(name = "total_pages") val totalPages: Int? = null,
    @Json(name = "total_results") val totalResults: Int? = null,
)
