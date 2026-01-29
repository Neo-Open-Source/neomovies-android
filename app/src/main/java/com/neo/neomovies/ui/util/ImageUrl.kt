package com.neo.neomovies.ui.util

import com.neo.neomovies.BuildConfig

fun normalizeImageUrl(path: String?): String? {
    if (path.isNullOrBlank()) return null

    val base = BuildConfig.API_BASE_URL.trimEnd('/')

    val value = path.trim()
    if (value.startsWith("http://") || value.startsWith("https://")) return value
    val id = when {
        value.all { it.isDigit() } -> value
        value.startsWith("kp_") && value.drop(3).all { it.isDigit() } -> value.drop(3)
        else -> null
    } ?: return null

    return "$base/api/v1/images/kp_small/$id?fallback=true"
}
