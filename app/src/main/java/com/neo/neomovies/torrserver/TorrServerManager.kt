package com.neo.neomovies.torrserver

import android.content.Context
import android.os.Build
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

object TorrServerManager {
    private const val BINARY_NAME = "TorrServer"
    private const val PORT = 8090

    private const val BIN_SUBDIR_NAME = "torrserver"

    private const val PREFS_NAME = "settings"
    private const val KEY_TORRSERVER_INSTALLED_VERSION = "torrserver_installed_version"

    fun getInstalledVersion(context: Context): String? {
        return context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TORRSERVER_INSTALLED_VERSION, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun setInstalledVersion(context: Context, version: String) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TORRSERVER_INSTALLED_VERSION, version)
            .apply()
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var activeDownloadConnection: HttpURLConnection? = null

    fun cancelActiveDownload() {
        runCatching { activeDownloadConnection?.disconnect() }
    }

    fun baseUrl(): String = "http://127.0.0.1:$PORT"

    private fun getBinaryFile(context: Context): File {
        return File(context.filesDir, BINARY_NAME)
    }

    private fun migrateOldBinaryIfNeeded(context: Context) {
        val oldCodeCacheDir = File(context.codeCacheDir, BIN_SUBDIR_NAME)
        val oldCodeCacheFile = File(oldCodeCacheDir, BINARY_NAME)
        val old2 = File(context.getDir("torrserver_bin", Context.MODE_PRIVATE), BINARY_NAME)
        val newFile = getBinaryFile(context)

        if (!newFile.exists()) {
            val source = when {
                oldCodeCacheFile.exists() -> oldCodeCacheFile
                old2.exists() -> old2
                else -> null
            }

            if (source != null) {
                runCatching {
                    source.copyTo(newFile, overwrite = true)
                    ensureExecutable(newFile)
                }
            }
        }

        runCatching { oldCodeCacheDir.deleteRecursively() }
        runCatching { old2.delete() }
    }

    fun isServerDownloaded(context: Context): Boolean {
        migrateOldBinaryIfNeeded(context)
        return getBinaryFile(context).exists()
    }

    fun deleteServerFiles(context: Context) {
        stopServer()
        runCatching { getBinaryFile(context).delete() }
        runCatching { File(context.codeCacheDir, BIN_SUBDIR_NAME).deleteRecursively() }
        runCatching { File(context.filesDir, BINARY_NAME).delete() }
        runCatching { File(context.getDir("torrserver_bin", Context.MODE_PRIVATE), BINARY_NAME).delete() }
        runCatching { File(context.filesDir, "torrserver.log").delete() }
        runCatching { File(context.filesDir, "torrserver.pid").delete() }
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_TORRSERVER_INSTALLED_VERSION)
            .apply()
    }

    suspend fun isServerRunning(): Boolean = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(baseUrl() + "/echo").get().build()
        runCatching {
            httpClient.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    fun stopServer() {
        runCatching {
            Runtime.getRuntime().exec(arrayOf("sh", "-c", "killall -9 $BINARY_NAME"))
        }
    }

    private fun ensureExecutable(file: File) {
        runCatching { file.setExecutable(true) }
        runCatching { Os.chmod(file.absolutePath, 0b111101101) } // 755
    }

    private fun appendLog(context: Context, line: String) {
        runCatching {
            File(context.filesDir, "torrserver.log").appendText(line + "\n")
        }
    }

    private fun startAttemptLog(context: Context, binary: File) {
        val abis = Build.SUPPORTED_ABIS.joinToString()
        appendLog(context, "--- TorrServer start attempt ---")
        appendLog(context, "path=${binary.absolutePath}")
        appendLog(context, "exists=${binary.exists()} canExecute=${binary.canExecute()} size=${binary.length()}")
        appendLog(context, "abis=$abis")
    }

    fun startServer(context: Context) {
        migrateOldBinaryIfNeeded(context)
        val binary = getBinaryFile(context)
        if (!binary.exists()) return

        ensureExecutable(binary)

        startAttemptLog(context, binary)

        val logFile = File(context.filesDir, "torrserver.log")

        runCatching {
            val cmd = "export GODEBUG=madvdontneed=1; ${binary.absolutePath} --port=$PORT --path=${context.filesDir.absolutePath} --logpath=${logFile.absolutePath} 1>>${logFile.absolutePath} 2>&1 &"
            Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
        }.onFailure { t ->
            appendLog(context, "Start failed: ${t::class.java.simpleName}: ${t.message ?: ""}")
        }
    }

    suspend fun downloadServerBinary(context: Context, version: String, onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        val arch = getArch()
        val url = getDownloadUrlForArch(version = version, arch = arch)
            ?: error("Unsupported arch: $arch")

        val destination = getBinaryFile(context)
        runCatching { destination.parentFile?.mkdirs() }
        downloadFile(url, destination, onProgress)
        ensureExecutable(destination)
    }

    private fun getDownloadUrlForArch(version: String, arch: String): String? {
        val tag = "MatriX.$version"
        return when (arch) {
            "arm64" -> "https://github.com/YouROK/TorrServer/releases/download/$tag/TorrServer-android-arm64"
            "arm7" -> "https://github.com/YouROK/TorrServer/releases/download/$tag/TorrServer-android-arm7"
            "amd64" -> "https://github.com/YouROK/TorrServer/releases/download/$tag/TorrServer-android-amd64"
            "386" -> "https://github.com/YouROK/TorrServer/releases/download/$tag/TorrServer-android-386"
            else -> null
        }
    }

    @Suppress("DEPRECATION")
    private fun getArch(): String {
        val abi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Build.SUPPORTED_ABIS.firstOrNull()
        } else {
            Build.CPU_ABI
        } ?: return "unknown"

        return when (abi) {
            "arm64-v8a" -> "arm64"
            "armeabi-v7a" -> "arm7"
            "x86_64" -> "amd64"
            "x86" -> "386"
            else -> abi
        }
    }

    private suspend fun downloadFile(urlString: String, destination: File, onProgress: (Int) -> Unit) {
        val url = URL(urlString)
        val connection = (url.openConnection() as HttpURLConnection)
        activeDownloadConnection = connection
        try {
            connection.connectTimeout = 15_000
            // keep it responsive for cancellation (disconnect) and to avoid very long block on read()
            connection.readTimeout = 5_000
            connection.connect()

            val fileLength = connection.contentLength

            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val data = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(data)
                        if (count == -1) break
                        total += count
                        if (fileLength > 0) {
                            onProgress(((total * 100) / fileLength).toInt())
                        }
                        output.write(data, 0, count)
                    }
                }
            }
        } finally {
            runCatching { connection.disconnect() }
            activeDownloadConnection = null
        }
    }
}
