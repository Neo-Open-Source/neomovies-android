package com.neo.neomovies.data.network.dto

import com.squareup.moshi.Json

data class SupportItemDto(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String,
    @Json(name = "type") val type: String,
    @Json(name = "description") val description: String,
    @Json(name = "contributions") val contributions: List<String> = emptyList(),
    @Json(name = "year") val year: Int? = null,
    @Json(name = "isActive") val isActive: Boolean? = null,
)
