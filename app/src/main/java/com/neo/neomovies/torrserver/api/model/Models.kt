package com.neo.neomovies.torrserver.api.model

import com.google.gson.annotations.SerializedName

data class TorrentStatus(
    @SerializedName("hash") val hash: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("poster") val poster: String?,
    @SerializedName("category") val category: String?,
    @SerializedName("data") val data: String?,
    @SerializedName("stat") val stat: Int?,
    @SerializedName("stat_string") val statString: String?,
    @SerializedName("torrent_size") val torrentSize: Long?,
    @SerializedName("loaded_size") val loadedSize: Long?,
    @SerializedName("preload_size") val preloadSize: Long?,
    @SerializedName("preloaded_bytes") val preloadedBytes: Long?,
    @SerializedName("download_speed") val downloadSpeed: Double?,
    @SerializedName("upload_speed") val uploadSpeed: Double?,
    @SerializedName("total_peers") val totalPeers: Int?,
    @SerializedName("pending_peers") val pendingPeers: Int?,
    @SerializedName("active_peers") val activePeers: Int?,
    @SerializedName("connected_seeders") val connectedSeeders: Int?,
    @SerializedName("half_open_peers") val halfOpenPeers: Int?,
    @SerializedName("timestamp") val timestamp: Long?,
    @SerializedName("file_stats") val fileStats: List<TorrentFileStat>?
)

data class TorrentFileStat(
    @SerializedName("id") val id: Int,
    @SerializedName("path") val path: String?,
    @SerializedName("length") val length: Long
)

data class TorrentRequest(
    @SerializedName("action") val action: String,
    @SerializedName("hash") val hash: String? = null,
    @SerializedName("link") val link: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("poster") val poster: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("data") val data: String? = null,
    @SerializedName("save_to_db") val saveToDb: Boolean = false
)
