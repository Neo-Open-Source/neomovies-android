package com.neo.neomovies.data

import com.neo.neomovies.data.network.MoviesApi
import com.neo.neomovies.data.network.dto.FavoriteCheckDto
import com.neo.neomovies.data.network.dto.FavoriteDto

class FavoritesRepository(
    private val api: MoviesApi,
) {
    suspend fun getFavorites(): List<FavoriteDto> {
        val envelope = api.getFavorites()
        val data = envelope.data
        if (envelope.success != true || data == null) {
            error("API error")
        }
        return data
    }

    suspend fun isFavorite(mediaId: String, mediaType: String): Boolean {
        val envelope = api.checkIsFavorite(id = mediaId, type = mediaType)
        val data: FavoriteCheckDto? = envelope.data
        if (envelope.success != true || data == null) {
            error("API error")
        }
        return data.isFavorite
    }

    suspend fun addToFavorites(mediaId: String, mediaType: String) {
        val envelope = api.addToFavorites(id = mediaId, type = mediaType)
        if (envelope.success != true) {
            error("API error")
        }
    }

    suspend fun removeFromFavorites(mediaId: String, mediaType: String) {
        val envelope = api.removeFromFavorites(id = mediaId, type = mediaType)
        if (envelope.success != true) {
            error("API error")
        }
    }
}
