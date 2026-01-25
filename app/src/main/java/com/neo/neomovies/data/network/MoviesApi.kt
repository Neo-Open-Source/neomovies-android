package com.neo.neomovies.data.network

import com.neo.neomovies.data.network.dto.ApiEnvelopeDto
import com.neo.neomovies.data.network.dto.MediaResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface MoviesApi {
    @GET("api/v1/movies/popular")
    suspend fun getPopularMovies(
        @Query("page") page: Int = 1,
        @Query("lang") lang: String = "ru",
    ): ApiEnvelopeDto<MediaResponseDto>

    @GET("api/v1/movies/top-rated")
    suspend fun getTopMovies(
        @Query("page") page: Int = 1,
        @Query("lang") lang: String = "ru",
    ): ApiEnvelopeDto<MediaResponseDto>

    @GET("api/v1/tv/top-rated")
    suspend fun getTopTv(
        @Query("page") page: Int = 1,
        @Query("lang") lang: String = "ru",
    ): ApiEnvelopeDto<MediaResponseDto>
}
