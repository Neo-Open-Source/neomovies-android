package com.neo.neomovies.downloads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.neo.neomovies.R
import com.neo.neomovies.data.collaps.CollapsRepository
import com.neo.neomovies.data.network.dto.MediaDetailsDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

data class CollapsDownloadTask(
    val kpId: Int,
    val showId: String,
    val showTitle: String,
    val posterUrl: String?,
    val details: MediaDetailsDto,
    val seasonFilter: Int? = null,
    val episodeFilter: Int? = null,
    val preferredVoice: String? = null,
)

data class CollapsQueueState(
    val current: String? = null,
    val voices: List<String> = emptyList(),
    val pendingTask: CollapsDownloadTask? = null,
    val showVoiceDialog: Boolean = false,
    /** downloadId -> 0..100 */
    val progress: Map<String, Int> = emptyMap(),
)

object CollapsDownloadQueue {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val jobs = mutableMapOf<String, Job>()

    private val _state = MutableStateFlow(CollapsQueueState())
    val state: StateFlow<CollapsQueueState> = _state

    private const val NOTIF_CHANNEL_ID = "collaps_downloads"
    private const val NOTIF_ID = 9001

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(NOTIF_CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(NOTIF_CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    private fun updateNotification(context: Context) {
        val progress = _state.value.progress
        if (progress.isEmpty()) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(NOTIF_ID)
            return
        }
        ensureChannel(context)
        val avg = progress.values.average().toInt()
        val label = if (progress.size == 1) {
            val id = progress.keys.first()
            Regex("_s(\\d+)_e(\\d+)_").find(id)?.let { "S${it.groupValues[1]}E${it.groupValues[2]}" } ?: id
        } else {
            "${progress.size} episodes"
        }
        val notif = NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(label)
            .setContentText("$avg%")
            .setProgress(100, avg, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, notif)
    }

    fun enqueue(
        context: Context,
        collapsRepository: CollapsRepository,
        task: CollapsDownloadTask,
    ) {
        scope.launch {
            android.util.Log.d("CollapsDownloadQueue", "enqueue: kpId=${task.kpId}, seasonFilter=${task.seasonFilter}")
            _state.update { it.copy(current = "fetching_${task.kpId}") }
            val voices = runCatching {
                val seasons = collapsRepository.getSeasonsByKpId(task.kpId)
                android.util.Log.d("CollapsDownloadQueue", "enqueue: found ${seasons.size} seasons")
                if (seasons.isNotEmpty()) {
                    seasons.flatMap { s -> s.episodes.flatMap { it.voices } }.distinct()
                } else {
                    collapsRepository.getMovieByKpId(task.kpId)?.voices ?: emptyList()
                }
            }.getOrDefault(emptyList())

            android.util.Log.d("CollapsDownloadQueue", "enqueue: found ${voices.size} voices: $voices")
            _state.update { it.copy(current = null) }

            if (voices.size > 1) {
                android.util.Log.d("CollapsDownloadQueue", "enqueue: showing voice dialog")
                _state.update { it.copy(voices = voices, pendingTask = task, showVoiceDialog = true) }
            } else {
                android.util.Log.d("CollapsDownloadQueue", "enqueue: processing task directly")
                processTask(context, collapsRepository, task.copy(preferredVoice = voices.firstOrNull()))
            }
        }
    }

    fun selectVoice(context: Context, collapsRepository: CollapsRepository, voice: String?) {
        val task = _state.value.pendingTask ?: return
        _state.update { it.copy(showVoiceDialog = false, pendingTask = null, voices = emptyList()) }
        scope.launch { processTask(context, collapsRepository, task.copy(preferredVoice = voice)) }
    }

    fun dismissVoiceDialog() {
        _state.update { it.copy(showVoiceDialog = false, pendingTask = null, voices = emptyList(), current = null) }
    }

    fun cancel(downloadId: String, context: Context) {
        jobs[downloadId]?.cancel()
        jobs.remove(downloadId)
        DownloadsStore(context).removeById(downloadId)
        // Remove the download folder
        File(context.filesDir, "downloads/media/$downloadId").deleteRecursively()
        _state.update { it.copy(progress = it.progress - downloadId) }
        updateNotification(context)
    }

    private suspend fun processTask(
        context: Context,
        collapsRepository: CollapsRepository,
        task: CollapsDownloadTask,
    ) {
        mutex.withLock {
            android.util.Log.d("CollapsDownloadQueue", "processTask: kpId=${task.kpId}, seasonFilter=${task.seasonFilter}")
            _state.update { it.copy(current = "resolving_${task.kpId}") }
            runCatching {
                val seasons = collapsRepository.getSeasonsByKpId(task.kpId)
                android.util.Log.d("CollapsDownloadQueue", "processTask: found ${seasons.size} seasons")
                if (seasons.isNotEmpty()) {
                    val filtered = if (task.seasonFilter != null) seasons.filter { it.season == task.seasonFilter } else seasons
                    android.util.Log.d("CollapsDownloadQueue", "processTask: filtered to ${filtered.size} seasons")
                    filtered.forEach { season ->
                        season.episodes.forEach { ep ->
                            // If episodeFilter is set, only download that specific episode
                            if (task.episodeFilter != null && ep.episode != task.episodeFilter) return@forEach
                            val url = ep.hlsUrl?.takeIf { it.isNotBlank() } ?: ep.mpdUrl?.takeIf { it.isNotBlank() }
                            android.util.Log.d("CollapsDownloadQueue", "processTask: S${season.season}E${ep.episode} url=$url")
                            if (!url.isNullOrBlank()) {
                                val downloadId = "${task.showId}_s${season.season}_e${ep.episode}_collaps"
                                enqueueItem(context, downloadId, url, task, season.season, ep.episode, "S${season.season}E${ep.episode}")
                            } else {
                                android.util.Log.w("CollapsDownloadQueue", "processTask: S${season.season}E${ep.episode} has no URL!")
                            }
                        }
                    }
                } else {
                    android.util.Log.d("CollapsDownloadQueue", "processTask: treating as movie")
                    val movie = collapsRepository.getMovieByKpId(task.kpId)
                    val url = movie?.hlsUrl?.takeIf { it.isNotBlank() } ?: movie?.mpdUrl?.takeIf { it.isNotBlank() }
                    android.util.Log.d("CollapsDownloadQueue", "processTask: movie url=$url")
                    if (!url.isNullOrBlank()) {
                        val downloadId = "${task.showId}_movie_${System.currentTimeMillis()}"
                        enqueueItem(context, downloadId, url, task, null, null, task.showTitle)
                    } else {
                        android.util.Log.w("CollapsDownloadQueue", "processTask: movie has no URL!")
                    }
                }
            }.onFailure { 
                android.util.Log.e("CollapsDownloadQueue", "processTask: error", it)
                it.printStackTrace() 
            }
            _state.update { it.copy(current = null) }
        }
    }

    private fun enqueueItem(
        context: Context,
        downloadId: String,
        url: String,
        task: CollapsDownloadTask,
        season: Int?,
        episode: Int?,
        title: String,
    ) {
        android.util.Log.d("CollapsDownloadQueue", "enqueueItem: downloadId=$downloadId, url=$url")
        // Store as a folder; master.m3u8 inside is the playback entry point
        val outputDir = File(context.filesDir, "downloads/media/$downloadId")
        val masterFile = File(outputDir, "master.m3u8")

        DownloadsStore(context).upsert(
            DownloadEntry(
                id = downloadId,
                type = if (season != null && episode != null) DownloadType.EPISODE else DownloadType.MOVIE,
                source = DownloadSource.COLLAPS,
                title = title,
                posterUrl = task.posterUrl,
                originalUrl = url,
                filePath = masterFile.absolutePath,
                fileSize = 0L,
                createdAt = System.currentTimeMillis(),
                showId = task.showId,
                showTitle = task.showTitle,
                seasonNumber = season,
                episodeNumber = episode,
            )
        )
        _state.update { it.copy(progress = it.progress + (downloadId to 0)) }
        updateNotification(context)

        val job = scope.launch {
            android.util.Log.d("CollapsDownloadQueue", "enqueueItem: starting download for $downloadId")
            runCatching {
                val result = CollapsHlsDownloader.download(
                    context = context,
                    hlsUrl = url,
                    preferredVoice = task.preferredVoice,
                    outputDir = outputDir,
                    onProgress = { p ->
                        val pct = if (p.total > 0) (p.downloaded * 100 / p.total) else 0
                        _state.update { it.copy(progress = it.progress + (downloadId to pct)) }
                        updateNotification(context)
                    },
                )
                android.util.Log.d("CollapsDownloadQueue", "enqueueItem: download completed for $downloadId, master=${result.masterPath}")
                val totalSize = outputDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                DownloadsStore(context).upsert(
                    DownloadEntry(
                        id = downloadId,
                        type = if (season != null && episode != null) DownloadType.EPISODE else DownloadType.MOVIE,
                        source = DownloadSource.COLLAPS,
                        title = title,
                        posterUrl = task.posterUrl,
                        originalUrl = url,
                        filePath = result.masterPath,
                        fileSize = totalSize,
                        createdAt = System.currentTimeMillis(),
                        showId = task.showId,
                        showTitle = task.showTitle,
                        seasonNumber = season,
                        episodeNumber = episode,
                    )
                )
                _state.update { it.copy(progress = it.progress - downloadId) }
                updateNotification(context)
            }.onFailure { e ->
                android.util.Log.e("CollapsDownloadQueue", "enqueueItem: download failed for $downloadId", e)
                e.printStackTrace()
                DownloadsStore(context).removeById(downloadId)
                outputDir.deleteRecursively()
                _state.update { it.copy(progress = it.progress - downloadId) }
                updateNotification(context)
            }
            jobs.remove(downloadId)
        }
        jobs[downloadId] = job
    }
}
