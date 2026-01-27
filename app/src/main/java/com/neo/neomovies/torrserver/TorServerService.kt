package com.neo.neomovies.torrserver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.neo.neomovies.MainActivity
import com.neo.neomovies.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TorServerService : Service() {
    companion object {
        private const val TAG = "TorServerService"

        const val ACTION_START = "com.neo.neomovies.torrserver.ACTION_START"
        const val ACTION_STOP = "com.neo.neomovies.torrserver.ACTION_STOP"
        const val ACTION_DOWNLOAD = "com.neo.neomovies.torrserver.ACTION_DOWNLOAD"

        private const val EXTRA_VERSION = "version"

        private const val CHANNEL_ID = "TorServerServiceChannel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, TorServerService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TorServerService::class.java).setAction(ACTION_STOP)
            ContextCompat.startForegroundService(context, intent)
        }

        fun download(context: Context, version: String) {
            val intent =
                Intent(context, TorServerService::class.java)
                    .setAction(ACTION_DOWNLOAD)
                    .putExtra(EXTRA_VERSION, version)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private var job: Job? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        appendAppLog("onTaskRemoved: stopping TorrServer")
        stopInternal()
        super.onTaskRemoved(rootIntent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        appendAppLog("onStartCommand action=$action")
        when (action) {
            ACTION_STOP -> {
                appendAppLog("Handling ACTION_STOP")
                startForegroundInternal(text = getString(R.string.torrserver_notif_stopping), progress = null)
                stopInternal()
                return START_NOT_STICKY
            }

            ACTION_DOWNLOAD -> {
                val version = intent.getStringExtra(EXTRA_VERSION) ?: "136"
                appendAppLog("ACTION_DOWNLOAD version=$version")
                startForegroundInternal(text = getString(R.string.torrserver_notif_downloading), progress = 0)
                job?.cancel()
                job = scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            TorrServerManager.downloadServerBinary(this@TorServerService, version) { p ->
                                scope.launch {
                                    startForegroundInternal(text = getString(R.string.torrserver_notif_downloading), progress = p)
                                }
                            }
                        }
                        TorrServerManager.setInstalledVersion(this@TorServerService, version)
                        appendAppLog("Download finished version=$version")
                        startForegroundInternal(text = getString(R.string.torrserver_notif_downloaded), progress = null)
                    }.onFailure {
                        if (it is CancellationException || !isActive) {
                            appendAppLog("Download cancelled")
                            return@onFailure
                        }
                        Log.e(TAG, "Download failed", it)
                        appendAppLog("Download failed", it)
                        startForegroundInternal(text = getString(R.string.torrserver_notif_download_error), progress = null)
                    }

                    delay(1200)
                    stopForegroundAndRemoveNotification()
                }
                return START_NOT_STICKY
            }

            ACTION_START, null -> {
                appendAppLog("ACTION_START")
                startForegroundInternal(text = getString(R.string.torrserver_notif_starting), progress = null)
                job?.cancel()
                job = scope.launch {
                    if (!TorrServerManager.isServerDownloaded(this@TorServerService)) {
                        appendAppLog("Start aborted: binary not found")
                        startForegroundInternal(text = getString(R.string.torrserver_notif_not_downloaded), progress = null)
                        stopSelf()
                        return@launch
                    }

                    runCatching {
                        TorrServerManager.startServer(this@TorServerService)
                    }.onFailure {
                        Log.e(TAG, "Start server command failed", it)
                        appendAppLog("Start server command failed", it)
                        startForegroundInternal(
                            text = getString(
                                R.string.torrserver_notif_start_error,
                                it.message ?: it::class.java.simpleName,
                            ),
                            progress = null,
                        )
                        return@launch
                    }

                    repeat(15) {
                        if (TorrServerManager.isServerRunning()) {
                            appendAppLog("TorrServer is running")
                            startForegroundInternal(text = getString(R.string.torrserver_notif_started), progress = null)
                            return@launch
                        }
                        delay(2000)
                    }

                    appendAppLog("Start check timed out")
                    startForegroundInternal(text = getString(R.string.torrserver_notif_start_failed), progress = null)
                }

                return START_NOT_STICKY
            }

            else -> return START_NOT_STICKY
        }
    }

    private fun stopInternal() {
        appendAppLog("Stopping server")
        TorrServerManager.cancelActiveDownload()
        job?.cancel()
        TorrServerManager.stopServer()
        stopForegroundAndRemoveNotification()
    }

    private fun stopForegroundAndRemoveNotification() {
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIFICATION_ID)
        }
        stopSelf()
    }

    private fun appendAppLog(message: String, throwable: Throwable? = null) {
        runCatching {
            val f = File(filesDir, "torrserver_app.log")
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val line = buildString {
                append(ts)
                append("  ")
                append(message)
                if (throwable != null) {
                    append(" | ")
                    append(throwable::class.java.simpleName)
                    val m = throwable.message
                    if (!m.isNullOrBlank()) {
                        append(": ")
                        append(m)
                    }
                }
                append("\n")
            }
            f.appendText(line)
        }
    }

    private fun startForegroundInternal(text: String, progress: Int?) {
        createChannelIfNeeded()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = buildNotification(text = text, progress = progress)

        // first call must be startForeground; after that we can update via notify too
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(text: String, progress: Int?): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(this, TorServerService::class.java).setAction(ACTION_STOP)
        val stopPending =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    this,
                    1,
                    stopIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            } else {
                PendingIntent.getService(
                    this,
                    1,
                    stopIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.torrserver_notification_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPending)
            .addAction(R.mipmap.ic_launcher, getString(R.string.torrserver_action_stop), stopPending)

        if (progress != null) {
            builder.setProgress(100, progress.coerceIn(0, 100), false)
        } else {
            builder.setProgress(0, 0, false)
        }

        return builder.build()
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, "TorrServer", NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { stopInternal() }
        super.onDestroy()
    }
}
