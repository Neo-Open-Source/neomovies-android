package com.neo.neomovies.downloads

import android.content.Context
import android.net.Uri
import android.os.StatFs
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.offline.DownloadService
import com.neo.neomovies.data.network.dto.MediaDetailsDto
import java.io.File
import kotlin.math.max

object DownloadActions {
    fun enqueueCollapsDownload(
        context: Context,
        downloadId: String,
        url: String,
        details: MediaDetailsDto?,
        title: String,
        posterUrl: String?,
        showId: String?,
        showTitle: String?,
        seasonNumber: Int?,
        episodeNumber: Int?,
    ) {
        val mimeType = when {
            url.endsWith(".m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
            url.endsWith(".mpd", ignoreCase = true) -> MimeTypes.APPLICATION_MPD
            else -> null
        }

        val request = DownloadUtil.buildRequest(downloadId, Uri.parse(url), mimeType)
        DownloadService.sendAddDownload(
            context,
            NeoDownloadService::class.java,
            request,
            false,
        )

        val store = DownloadsStore(context)
        store.upsert(
            DownloadEntry(
                id = downloadId,
                type = if (seasonNumber != null && episodeNumber != null) DownloadType.EPISODE else DownloadType.MOVIE,
                source = DownloadSource.COLLAPS,
                title = title,
                posterUrl = posterUrl,
                originalUrl = url,
                filePath = "media3://$downloadId",
                fileSize = 0L,
                createdAt = System.currentTimeMillis(),
                showId = showId,
                showTitle = showTitle,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
            )
        )
    }

    fun enqueueTorrentDownload(
        context: Context,
        downloadId: String,
        fileUrl: String,
        fileName: String,
        fileSize: Long,
        showId: String?,
        showTitle: String?,
        seasonNumber: Int?,
        episodeNumber: Int?,
        posterUrl: String?,
    ): Boolean {
        if (!hasEnoughSpace(context, fileSize)) return false
        val request = DownloadUtil.buildRequest(downloadId, Uri.parse(fileUrl))
        DownloadService.sendAddDownload(
            context,
            NeoDownloadService::class.java,
            request,
            false,
        )

        val targetPath = "media3://$downloadId"
        DownloadsStore(context).upsert(
            DownloadEntry(
                id = downloadId,
                type = if (seasonNumber != null && episodeNumber != null) DownloadType.EPISODE else DownloadType.MOVIE,
                source = DownloadSource.TORRENT,
                title = fileName,
                posterUrl = posterUrl,
                originalUrl = fileUrl,
                filePath = targetPath,
                fileSize = fileSize,
                createdAt = System.currentTimeMillis(),
                showId = showId,
                showTitle = showTitle,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
            )
        )
        return true
    }

    fun hasEnoughSpace(context: Context, bytesNeeded: Long): Boolean {
        if (bytesNeeded <= 0) return true
        val stat = StatFs(context.filesDir.absolutePath)
        val available = stat.availableBytes
        val safe = max(bytesNeeded, bytesNeeded + 100L * 1024L * 1024L)
        return available > safe
    }
}
