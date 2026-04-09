package com.neo.neomovies.data

import com.neo.neomovies.data.network.MoviesApi
import com.neo.neomovies.data.network.dto.MediaDetailsDto
import com.neo.neomovies.data.network.dto.MediaDto
import com.neo.neomovies.data.network.dto.MediaResponseDto

class MoviesRepository(
    private val api: MoviesApi,
) {
    suspend fun getPopularPage(page: Int = 1): MediaResponseDto {
        val envelope = api.getPopularMovies(page)
        val data = envelope.data
        if (envelope.success != true || data == null) {
            error("API error")
        }
        return data
    }

    suspend fun getPopular(page: Int = 1): List<MediaDto> {
        return getPopularPage(page).results
    }

    suspend fun getTopMoviesPage(page: Int = 1): MediaResponseDto {
        val envelope = api.getTopMovies(page)
        val data = envelope.data
        if (envelope.success != true || data == null) {
            error("API error")
        }
        return data
    }

    suspend fun getTopMovies(page: Int = 1): List<MediaDto> {
        return getTopMoviesPage(page).results
    }

    suspend fun getTopTvPage(page: Int = 1): MediaResponseDto {
        val envelope = api.getTopTv(page)
        val data = envelope.data
        if (envelope.success != true || data == null) {
            error("API error")
        }
        return data
    }

    suspend fun getTopTv(page: Int = 1): List<MediaDto> {
        return getTopTvPage(page).results
    }

    suspend fun getDetails(sourceId: String): MediaDetailsDto {
        val cleaned = sourceId.removeSuffix(".0")
        val normalized = if (cleaned.contains("_")) cleaned else "kp_$cleaned"
        val envelope = api.getDetails(normalized)
        val data = envelope.data
        if (envelope.success != true || data == null) {
            error("API error")
        }
        return data
    }

    suspend fun searchMovies(query: String, page: Int = 1): MediaResponseDto {
        val envelope = api.searchMovies(query = query, page = page)
        val data = envelope.data
        if (envelope.success != true || data == null) {
            error("API error")
        }
        return data
    }
}
