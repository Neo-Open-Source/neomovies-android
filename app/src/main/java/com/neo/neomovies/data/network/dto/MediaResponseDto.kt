package com.neo.neomovies.data.network.dto

import com.squareup.moshi.Json

data class MediaResponseDto(
    @field:Json(name = "page") val page: Int? = null,
    @field:Json(name = "results") val results: List<MediaDto> = emptyList(),
    @field:Json(name = "total_pages") val totalPages: Int? = null,
    @field:Json(name = "total_results") val totalResults: Int? = null,
)
