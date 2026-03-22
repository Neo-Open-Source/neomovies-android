package com.neo.neomovies.downloads

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.NotificationUtil
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import com.neo.neomovies.R
import java.io.File
import java.util.concurrent.Executors

object DownloadUtil {
    private const val CHANNEL_ID = "downloads"

    @Volatile private var downloadManager: DownloadManager? = null
    @Volatile private var downloadCache: SimpleCache? = null
    @Volatile private var databaseProvider: DatabaseProvider? = null
    @Volatile private var dataSourceFactory: CacheDataSource.Factory? = null
    @Volatile private var notificationHelper: DownloadNotificationHelper? = null

    fun getDatabaseProvider(context: Context): DatabaseProvider {
        val current = databaseProvider
        if (current != null) return current
        return synchronized(this) {
            databaseProvider ?: StandaloneDatabaseProvider(context.applicationContext).also { databaseProvider = it }
        }
    }

    fun getDownloadCache(context: Context): SimpleCache {
        val current = downloadCache
        if (current != null) return current
        return synchronized(this) {
            downloadCache ?: run {
                val cacheDir = File(context.applicationContext.filesDir, "downloads/cache")
                SimpleCache(cacheDir, NoOpCacheEvictor(), getDatabaseProvider(context)).also { downloadCache = it }
            }
        }
    }

    fun getDataSourceFactory(context: Context): CacheDataSource.Factory {
        val current = dataSourceFactory
        if (current != null) return current
        return synchronized(this) {
            dataSourceFactory ?: run {
                val httpFactory = DefaultHttpDataSource.Factory()
                val upstream = DefaultDataSource.Factory(context.applicationContext, httpFactory)
                CacheDataSource.Factory()
                    .setCache(getDownloadCache(context))
                    .setUpstreamDataSourceFactory(upstream)
                    .setCacheWriteDataSinkFactory(null)
                    .also { dataSourceFactory = it }
            }
        }
    }

    fun getDownloadManager(context: Context): DownloadManager {
        val current = downloadManager
        if (current != null) return current
        return synchronized(this) {
            downloadManager ?: run {
                val executor = Executors.newFixedThreadPool(2)
                val manager = DownloadManager(
                    context.applicationContext,
                    getDatabaseProvider(context),
                    getDownloadCache(context),
                    getDataSourceFactory(context),
                    executor,
                    executor
                )
                manager.maxParallelDownloads = 2
                downloadManager = manager
                manager
            }
        }
    }

    fun getNotificationHelper(context: Context): DownloadNotificationHelper {
        val current = notificationHelper
        if (current != null) return current
        return synchronized(this) {
            notificationHelper ?: DownloadNotificationHelper(context.applicationContext, CHANNEL_ID).also { notificationHelper = it }
        }
    }

    fun buildProgressNotification(context: Context, downloads: List<Download>) =
        getNotificationHelper(context).buildProgressNotification(
            context.applicationContext,
            android.R.drawable.stat_sys_download,
            null,
            null,
            downloads
        )

    fun ensureChannel(context: Context) {
        NotificationUtil.createNotificationChannel(
            context.applicationContext,
            CHANNEL_ID,
            R.string.downloads_channel_name,
            0,
            NotificationUtil.IMPORTANCE_LOW
        )
    }

    fun buildRequest(id: String, uri: Uri, mimeType: String? = null): DownloadRequest {
        val builder = DownloadRequest.Builder(id, uri)
        if (!mimeType.isNullOrBlank()) builder.setMimeType(mimeType)
        return builder.build()
    }
}
