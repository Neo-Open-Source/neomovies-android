package com.neo.neomovies.data

import com.neo.neomovies.data.network.MoviesApi
import com.neo.neomovies.data.network.dto.MediaDetailsDto
import com.neo.neomovies.data.network.dto.MediaDto
import com.neo.neomovies.data.network.dto.MediaResponseDto
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

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

    suspend fun searchMovies(query: String, page: Int = 1): MediaResponseDto = coroutineScope {
        val moviesDeferred = async {
            try {
                api.searchMovies(query = query, page = page)
            } catch (e: Exception) {
                null
            }
        }
        val tvDeferred = async {
            try {
                api.searchTv(query = query, page = page)
            } catch (e: Exception) {
                null
            }
        }

        val moviesEnvelope = moviesDeferred.await()
        val tvEnvelope = tvDeferred.await()

        val moviesData = moviesEnvelope?.data
        val tvData = tvEnvelope?.data

        val allResults = mutableListOf<MediaDto>()
        if (moviesEnvelope?.success == true && moviesData != null) {
            allResults.addAll(moviesData.results)
        }
        if (tvEnvelope?.success == true && tvData != null) {
            allResults.addAll(tvData.results)
        }

        if (moviesEnvelope == null && tvEnvelope == null) {
            error("API error: both requests failed")
        }

        val maxPages = kotlin.math.max(moviesData?.totalPages ?: 1, tvData?.totalPages ?: 1)
        val totalRes = (moviesData?.totalResults ?: 0) + (tvData?.totalResults ?: 0)

        MediaResponseDto(
            page = page,
            results = allResults,
            totalPages = maxPages,
            totalResults = totalRes
        )
    }
}
