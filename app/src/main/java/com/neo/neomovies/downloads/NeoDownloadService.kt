package com.neo.neomovies.downloads

import android.app.Notification
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService

class NeoDownloadService : DownloadService(
    1,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    "downloads",
    0,
    0,
) {
    override fun getDownloadManager(): DownloadManager {
        DownloadUtil.ensureChannel(this)
        return DownloadUtil.getDownloadManager(this)
    }

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(downloads: List<Download>): Notification {
        return DownloadUtil.buildProgressNotification(this, downloads)
    }
}
