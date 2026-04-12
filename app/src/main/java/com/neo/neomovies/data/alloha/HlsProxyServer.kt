package com.neo.neomovies.data.alloha

import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import kotlinx.coroutines.*
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private const val TAG = "HlsProxy"
private const val PREFETCH = 2

class HlsProxyServer(
    private val activeHeaders: Map<String, String>,
    private val port: Int = 8080,
    private val onSessionExpired: () -> Unit = {}
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null

    val fixedMasterUrl: String get() = "http://127.0.0.1:$port/master.m3u8"

    @Volatile private var activeMasterUrl: String = ""
    @Volatile private var sessionVersion: Int = 0
    @Suppress("PrivatePropertyName")
    private val EMPTY_TS_PACKET = ByteArray(188).also { it[0] = 0x47; it[1] = 0x1F.toByte(); it[2] = 0xFF.toByte(); it[3] = 0x10 }

    private var connectionPool = ConnectionPool(5, 15, TimeUnit.SECONDS)

    @Volatile
    private var client = buildClient()

    private fun buildClient(): OkHttpClient = OkHttpClient.Builder()
        .connectionPool(connectionPool)
        .followRedirects(true)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun updateMasterUrl(url: String) {
        activeMasterUrl = url
        sessionVersion++

        val oldPool = connectionPool
        connectionPool = ConnectionPool(5, 15, TimeUnit.SECONDS)
        client = buildClient()

        synchronized(cacheLock) { cache.clear() }
        recentSegments.clear()
        inFlight.clear()

        scope.launch {
            try { oldPool.evictAll() }
            catch (e: Exception) { Log.w(TAG, "evictAll failed (non-fatal): ${e.message}") }
        }

        Log.d(TAG, "Session refreshed: client rebuilt, cache cleared. URL: $url")
    }

    private val cache = object : LinkedHashMap<String, ByteArray>(PREFETCH + 2, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, ByteArray>) = size > PREFETCH + 1
    }
    private val cacheLock = Any()
    private val inFlight = ConcurrentHashMap<String, Deferred<ByteArray?>>()
    private val recentSegments = ArrayDeque<String>()

    fun start() {
        serverSocket = ServerSocket(port)
        scope.launch {
            Log.d(TAG, "HLS proxy started on port $port")
            while (isActive) {
                try {
                    val socket = serverSocket!!.accept()
                    launch { handleConnection(socket) }
                } catch (_: Exception) {}
            }
        }
    }

    fun stop() { scope.cancel(); runCatching { serverSocket?.close() } }

    fun proxyUrl(originalUrl: String): String {
        val encoded = Base64.encodeToString(originalUrl.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)
        return "http://127.0.0.1:$port/proxy?url=$encoded"
    }

    private suspend fun handleConnection(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            socket.use {
                val input = it.getInputStream().bufferedReader()
                val output = it.getOutputStream()
                val requestLine = input.readLine() ?: return@use
                while (true) { if (input.readLine().isNullOrBlank()) break }
                val path = requestLine.split(" ").getOrNull(1) ?: return@use

                if (path.startsWith("/master.m3u8")) {
                    val master = activeMasterUrl
                    if (master.isBlank()) { send404(output); return@use }
                    servePlaylist(master, output); return@use
                }

                val encodedUrl = path.substringAfter("url=").substringBefore(" ")
                if (encodedUrl.isBlank()) { send404(output); return@use }
                val url = String(Base64.decode(URLDecoder.decode(encodedUrl, "UTF-8"), Base64.URL_SAFE))

                if (url.endsWith(".m3u8") || url.contains("master.m3u8")) {
                    servePlaylist(url, output)
                } else {
                    serveSegment(url, output)
                }
            }
        } catch (e: Exception) { Log.w(TAG, "Connection error: ${e.message}") }
    }

    private suspend fun servePlaylist(url: String, out: OutputStream) {
        val body = fetchText(url) ?: run { send404(out); return }
        val rewritten = rewriteM3u8(body, url)
        val bytes = rewritten.toByteArray()
        out.write("HTTP/1.1 200 OK\r\nContent-Type: application/vnd.apple.mpegurl\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(bytes); out.flush()
    }

    private fun rewriteToCurrentPath(oldUrl: String): String? {
        val master = activeMasterUrl
        if (master.isBlank()) return null
        val segName = oldUrl.substringAfterLast("/")
        if (segName.isBlank() || !segName.contains(".ts")) return null
        val newBase = master.substringBeforeLast("/") + "/"
        val oldBase = oldUrl.substringBeforeLast("/") + "/"
        if (oldBase == newBase) return null
        return newBase + segName
    }

    private suspend fun serveSegment(url: String, out: OutputStream) {
        val cached = synchronized(cacheLock) { cache[url] }
        val bytes = if (cached != null) {
            cached
        } else {
            val fetched = fetchBytes(url)
            if (fetched != null) {
                fetched
            } else {
                val rewritten = rewriteToCurrentPath(url)
                val rewrittenBytes = if (rewritten != null) {
                    Log.d(TAG, "Rewriting segment to current path: ${rewritten.takeLast(60)}")
                    fetchBytes(rewritten)
                } else null

                if (rewrittenBytes != null) {
                    synchronized(cacheLock) { cache[url] = rewrittenBytes }
                    rewrittenBytes
                } else {
                    scope.launch { fetchSegmentFromFreshPlaylist(url) }
                    if (url.contains("-a1.ts") || url.contains("-a2.ts")) {
                        EMPTY_TS_PACKET
                    } else {
                        run { send503(out); return }
                    }
                }
            }
        }

        if (bytes.size < 1000) Log.w(TAG, "Serving suspiciously small segment (${bytes.size}b): $url")

        val ct = when {
            url.contains(".vtt") || url.contains(".webvtt") -> "text/vtt"
            url.contains(".aac") -> "audio/aac"
            url.contains(".m4s") || url.contains(".mp4") -> "video/mp4"
            else -> "video/MP2T"
        }
        out.write("HTTP/1.1 200 OK\r\nContent-Type: $ct\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(bytes); out.flush()

        val idx = recentSegments.indexOf(url)
        if (idx >= 0) {
            for (i in 1..PREFETCH) {
                val next = recentSegments.getOrNull(idx + i) ?: break
                if (synchronized(cacheLock) { cache.containsKey(next) }) continue
                scope.launch { getOrFetch(next) }
            }
        }
    }

    private suspend fun getOrFetch(url: String): ByteArray? {
        synchronized(cacheLock) { cache[url] }?.let { return it }
        val deferred = inFlight.getOrPut(url) {
            scope.async {
                try { fetchBytes(url)?.also { bytes -> synchronized(cacheLock) { cache[url] = bytes } } }
                finally { inFlight.remove(url) }
            }
        }
        return deferred.await()
    }

    private fun rewriteM3u8(content: String, baseUrl: String): String {
        val base = baseUrl.substringBeforeLast("/") + "/"
        val segUrls = mutableListOf<String>()
        val result = content.lines().joinToString("\n") { line ->
            when {
                line.isBlank() -> line
                line.startsWith("#") -> {
                    if (line.contains("URI=")) {
                        line.replace(Regex("""URI="([^"]+)"""")) { match ->
                            val uri = match.groupValues[1]
                            if (uri.isBlank() || uri == "none") match.value
                            else { val absolute = if (uri.startsWith("http")) uri else base + uri; """URI="${proxyUrl(absolute)}"""" }
                        }
                    } else line
                }
                else -> { val absolute = if (line.startsWith("http")) line else base + line; segUrls.add(absolute); proxyUrl(absolute) }
            }
        }
        if (segUrls.isNotEmpty()) {
            recentSegments.clear(); recentSegments.addAll(segUrls)
            scope.launch { segUrls.take(PREFETCH).forEach { getOrFetch(it) } }
        }
        return result
    }

    private suspend fun fetchSegmentFromFreshPlaylist(failedUrl: String): ByteArray? {
        val segName = failedUrl.substringAfterLast("/")
        Log.d(TAG, "403 on $segName -- waiting for session refresh")
        val deadline = System.currentTimeMillis() + 30_000L
        var lastVersion = -1
        while (System.currentTimeMillis() < deadline) {
            val curVersion = sessionVersion
            if (curVersion == lastVersion) { delay(200); continue }
            lastVersion = curVersion
            val master = activeMasterUrl
            val masterBody = fetchText(master) ?: continue
            val base = master.substringBeforeLast("/") + "/"
            val variantPath = masterBody.lines().firstOrNull { !it.startsWith("#") && it.isNotBlank() } ?: continue
            val variantUrl = if (variantPath.startsWith("http")) variantPath else base + variantPath
            val variantBody = fetchText(variantUrl) ?: continue
            val variantBase = variantUrl.substringBeforeLast("/") + "/"
            val newSegUrl = variantBody.lines()
                .firstOrNull { line -> !line.startsWith("#") && line.isNotBlank() && line.substringAfterLast("/") == segName }
                ?.let { if (it.startsWith("http")) it else variantBase + it }
                ?: run { Log.w(TAG, "$segName not found in new playlist, skipping"); return null }
            val bytes = fetchBytes(newSegUrl)
            if (bytes != null) { Log.d(TAG, "Recovered $segName after session refresh"); synchronized(cacheLock) { cache[failedUrl] = bytes }; return bytes }
            Log.w(TAG, "New session also 403 for $segName -- triggering forced restart")
            scope.launch(Dispatchers.Main) { onSessionExpired() }
        }
        Log.w(TAG, "Failed to recover $segName within timeout"); return null
    }

    private fun fetchText(url: String): String? = try {
        client.newCall(buildRequest(url)).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "fetchText HTTP ${resp.code} for ${url.take(80)}")
                if (resp.code == 403) { scope.launch { try { connectionPool.evictAll() } catch (_: Exception) {} } }
                null
            } else { resp.body?.string() }
        }
    } catch (e: Exception) { Log.w(TAG, "fetchText: ${e.message}"); null }

    private fun fetchBytes(url: String): ByteArray? = try {
        val currentClient = client
        currentClient.newCall(buildRequest(url)).execute().use { resp ->
            if (resp.code == 403) {
                Log.w(TAG, "fetchBytes 403 for ${url.takeLast(60)}, evicting connections and retrying")
                connectionPool.evictAll()
                val retryReq = buildRequest(url).newBuilder().header("Connection", "close").build()
                currentClient.newCall(retryReq).execute().use { retryResp ->
                    if (!retryResp.isSuccessful) { Log.w(TAG, "fetchBytes retry also ${retryResp.code} for ${url.takeLast(60)}"); null }
                    else { retryResp.body?.bytes() }
                }
            } else if (!resp.isSuccessful) { Log.w(TAG, "fetchBytes HTTP ${resp.code} for ${url.takeLast(60)}"); null }
            else { resp.body?.bytes() }
        }
    } catch (e: Exception) { Log.w(TAG, "fetchBytes: ${e.message}"); null }

    private fun send503(out: OutputStream) { out.write("HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\nRetry-After: 2\r\nConnection: close\r\n\r\n".toByteArray()); out.flush() }

    private fun buildRequest(url: String): Request {
        val reqBuilder = Request.Builder().url(url)
            .header("User-Agent", activeHeaders["user-agent"] ?: "Mozilla/5.0")
            .header("Origin", activeHeaders["origin"] ?: "")
            .header("Referer", activeHeaders["referer"] ?: "")
            .header("Accept", "*/*")
        try { val cookie = CookieManager.getInstance().getCookie(url); if (!cookie.isNullOrBlank()) reqBuilder.header("Cookie", cookie) } catch (_: Exception) {}
        activeHeaders["accepts-controls"]?.let { reqBuilder.header("accepts-controls", it) }
        activeHeaders["authorizations"]?.let { reqBuilder.header("authorizations", it) }
        activeHeaders["sec-fetch-dest"]?.let { reqBuilder.header("Sec-Fetch-Dest", it) }
        activeHeaders["sec-fetch-mode"]?.let { reqBuilder.header("Sec-Fetch-Mode", it) }
        activeHeaders["sec-fetch-site"]?.let { reqBuilder.header("Sec-Fetch-Site", it) }
        activeHeaders["accept-language"]?.let { reqBuilder.header("Accept-Language", it) }
        return reqBuilder.build()
    }

    private fun send404(out: OutputStream) { out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()); out.flush() }
}
