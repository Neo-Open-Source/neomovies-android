package com.neo.neomovies.downloads

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.NotificationUtil
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import java.util.concurrent.Executors
import com.neo.neomovies.R
import java.io.File

object DownloadUtil {
    private const val CHANNEL_ID = "downloads"

    @Volatile private var downloadManager: DownloadManager? = null
    @Volatile private var downloadCache: SimpleCache? = null
    @Volatile private var databaseProvider: DatabaseProvider? = null
    @Volatile private var dataSourceFactory: CacheDataSource.Factory? = null
    @Volatile private var notificationHelper: DownloadNotificationHelper? = null

    fun getDatabaseProvider(context: Context): DatabaseProvider {
        return databaseProvider ?: synchronized(this) {
            databaseProvider ?: StandaloneDatabaseProvider(context).also { databaseProvider = it }
        }
    }

    fun getDownloadCache(context: Context): SimpleCache {
        return downloadCache ?: synchronized(this) {
            downloadCache ?: run {
                val cacheDir = File(context.filesDir, "downloads/cache")
                SimpleCache(cacheDir, NoOpCacheEvictor(), getDatabaseProvider(context)).also {
                    downloadCache = it
                }
            }
        }
    }

    fun getDataSourceFactory(context: Context): CacheDataSource.Factory {
        return dataSourceFactory ?: synchronized(this) {
            dataSourceFactory ?: run {
                val httpFactory = DefaultHttpDataSource.Factory()
                val upstream = DefaultDataSource.Factory(context, httpFactory)
                CacheDataSource.Factory()
                    .setCache(getDownloadCache(context))
                    .setUpstreamDataSourceFactory(upstream)
                    .setCacheWriteDataSinkFactory(null)
                    .also { dataSourceFactory = it }
            }
        }
    }

    fun getDownloadManager(context: Context): DownloadManager {
        return downloadManager ?: synchronized(this) {
            downloadManager ?: run {
                val executor = Executors.newFixedThreadPool(2)
                DownloadManager(
                    context,
                    getDatabaseProvider(context),
                    getDownloadCache(context),
                    getDataSourceFactory(context),
                    executor,
                ).also { manager ->
                    manager.maxParallelDownloads = 2
                    manager.addListener(object : DownloadManager.Listener {})
                    downloadManager = manager
                }
            }
        }
    }

    fun getNotificationHelper(context: Context): DownloadNotificationHelper {
        return notificationHelper ?: synchronized(this) {
            notificationHelper ?: DownloadNotificationHelper(context, CHANNEL_ID).also { notificationHelper = it }
        }
    }

    fun buildProgressNotification(context: Context, downloads: List<Download>) =
        getNotificationHelper(context).buildProgressNotification(
            context,
            android.R.drawable.stat_sys_download,
            null,
            null,
            downloads,
        )

    fun ensureChannel(context: Context) {
        NotificationUtil.createNotificationChannel(
            context,
            CHANNEL_ID,
            R.string.downloads_channel_name,
            NotificationUtil.IMPORTANCE_LOW,
            0,
        )
    }

    fun buildRequest(id: String, uri: Uri, mimeType: String? = null): DownloadRequest {
        val builder = DownloadRequest.Builder(id, uri)
        if (!mimeType.isNullOrBlank()) builder.setMimeType(mimeType)
        return builder.build()
    }
}
