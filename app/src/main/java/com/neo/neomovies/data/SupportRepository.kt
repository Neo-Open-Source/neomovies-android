package com.neo.neomovies.data

import android.content.Context
import com.neo.neomovies.data.network.MoviesApi
import com.neo.neomovies.data.network.dto.SupportItemDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

private const val PREFS_NAME = "support_cache"
private const val KEY_JSON = "json"

class SupportRepository(
    private val api: MoviesApi,
    private val moshi: Moshi,
    private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val adapter =
        moshi.adapter<List<SupportItemDto>>(
            Types.newParameterizedType(List::class.java, SupportItemDto::class.java),
        )

    fun getCached(): List<SupportItemDto>? {
        val json = prefs.getString(KEY_JSON, null) ?: return null
        return runCatching { adapter.fromJson(json) }.getOrNull()
    }

    fun cache(items: List<SupportItemDto>) {
        val json = adapter.toJson(items)
        prefs.edit().putString(KEY_JSON, json).apply()
    }

    suspend fun fetch(): List<SupportItemDto> {
        val items = api.getSupportList()
        cache(items)
        return items
    }
}
