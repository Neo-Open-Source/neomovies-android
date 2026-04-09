package com.neo.neomovies.downloads

import android.app.Notification
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.neo.neomovies.R

class NeoDownloadService : DownloadService(
    1,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    "downloads",
    R.string.downloads_channel_name,
    0,
) {
    override fun getDownloadManager(): DownloadManager {
        DownloadUtil.ensureChannel(this)
        return DownloadUtil.getDownloadManager(this)
    }

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification {
        return DownloadUtil.buildProgressNotification(this, downloads, notMetRequirements)
    }
}