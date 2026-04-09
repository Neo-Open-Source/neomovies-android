package com.neo.neomovies.data.network.dto

import com.squareup.moshi.Json

data class MediaResponseDto(
    @Json(name = "page") val page: Int? = null,
    @Json(name = "results") val results: List<MediaDto> = emptyList(),
    // API returns "pages" and "total"
    @Json(name = "pages") val pages: Int? = null,
    @Json(name = "total") val total: Int? = null,
    // legacy fallback
    @Json(name = "total_pages") val totalPages: Int? = null,
    @Json(name = "total_results") val totalResults: Int? = null,
) {
    val effectiveTotalPages: Int get() = pages ?: totalPages ?: 1
    val effectiveTotalResults: Int get() = total ?: totalResults ?: results.size
}
