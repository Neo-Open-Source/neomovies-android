package com.neo.neomovies.data.network.dto

import com.squareup.moshi.Json

data class ApiEnvelopeDto<T>(
    @field:Json(name = "success") val success: Boolean? = null,
    @field:Json(name = "data") val data: T? = null,
)
