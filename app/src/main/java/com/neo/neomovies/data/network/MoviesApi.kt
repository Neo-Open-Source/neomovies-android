package com.neo.neomovies.data.network

import com.neo.neomovies.data.network.dto.ApiEnvelopeDto
import com.neo.neomovies.data.network.dto.FavoriteCheckDto
import com.neo.neomovies.data.network.dto.FavoriteDto
import com.neo.neomovies.data.network.dto.MediaDetailsDto
import com.neo.neomovies.data.network.dto.MediaResponseDto
import com.neo.neomovies.data.network.dto.SupportItemDto
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
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

    @GET("api/v1/movie/{id}")
    suspend fun getDetails(
        @Path("id") id: String,
        @Query("lang") lang: String = "ru",
    ): ApiEnvelopeDto<MediaDetailsDto>

    @GET("api/v1/movies/search")
    suspend fun searchMovies(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("lang") lang: String = "ru",
    ): ApiEnvelopeDto<MediaResponseDto>

    @GET("api/v1/tv/search")
    suspend fun searchTv(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("lang") lang: String = "ru",
    ): ApiEnvelopeDto<MediaResponseDto>

    @GET("api/v1/support/list")
    suspend fun getSupportList(): List<SupportItemDto>

    @GET("api/v1/favorites")
    suspend fun getFavorites(): ApiEnvelopeDto<List<FavoriteDto>>

    @POST("api/v1/favorites/{id}")
    suspend fun addToFavorites(
        @Path("id") id: String,
        @Query("type") type: String,
    ): ApiEnvelopeDto<Unit>

    @DELETE("api/v1/favorites/{id}")
    suspend fun removeFromFavorites(
        @Path("id") id: String,
        @Query("type") type: String,
    ): ApiEnvelopeDto<Unit>

    @GET("api/v1/favorites/{id}/check")
    suspend fun checkIsFavorite(
        @Path("id") id: String,
        @Query("type") type: String,
    ): ApiEnvelopeDto<FavoriteCheckDto>
}
