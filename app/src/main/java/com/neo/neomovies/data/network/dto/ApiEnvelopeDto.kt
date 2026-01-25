package com.neo.neomovies.data.network.dto

import com.squareup.moshi.Json

data class ApiEnvelopeDto<T>(
    @Json(name = "success") val success: Boolean? = null,
    @Json(name = "data") val data: T? = null,
)
