package com.neo.neomovies.data

import com.neo.neomovies.data.network.MoviesApi
import com.neo.neomovies.data.network.dto.MediaDto

class MoviesRepository(
    private val api: MoviesApi,
) {
    suspend fun getPopular(page: Int = 1): List<MediaDto> {
        val envelope = api.getPopularMovies(page)
        val data = envelope.data
        if (envelope.success != true || data == null) {
            error("API error")
        }
        return data.results
    }

    suspend fun getTopMovies(page: Int = 1): List<MediaDto> {
        val envelope = api.getTopMovies(page)
        val data = envelope.data
        if (envelope.success != true || data == null) {
            error("API error")
        }
        return data.results
    }

    suspend fun getTopTv(page: Int = 1): List<MediaDto> {
        val envelope = api.getTopTv(page)
        val data = envelope.data
        if (envelope.success != true || data == null) {
            error("API error")
        }
        return data.results
    }
}
