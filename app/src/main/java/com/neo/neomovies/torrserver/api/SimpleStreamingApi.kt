package com.neo.neomovies.torrserver.api

import com.neo.neomovies.torrserver.api.model.TorrentFileStat
import com.neo.neomovies.torrserver.api.model.TorrentStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

class SimpleStreamingApi(
    private val baseUrl: String
) {
    private val api = TorrServeApi(baseUrl)

    suspend fun startStreaming(link: String, title: String?, poster: String?): TorrentStatus {
        return api.addTorrent(link, title, poster, true)
    }

    suspend fun waitForReady(hash: String, timeoutMs: Long = 60000): TorrentStatus {
        return withTimeout(timeoutMs) {
            while (true) {
                try {
                    val status = api.getTorrent(hash)
                    if (!status.fileStats.isNullOrEmpty()) {
                        return@withTimeout status
                    }
                } catch (e: Exception) {
                    // ignore
                }
                delay(2000)
            }
            throw RuntimeException("Timeout")
        }
    }

    fun getFileStreamUrl(link: String, fileIndex: Int): String {
        return "$baseUrl/stream/mov.m3u?play=play&link=$link&index=$fileIndex"
    }
}
