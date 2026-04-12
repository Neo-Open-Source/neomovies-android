package com.neo.neomovies.data.alloha

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaFormat
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.neo.neomovies.alloha.AllohaParser
import com.neo.neomovies.alloha.HlsProxyServer
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "AllohaSession"

/**
 * Manages the lifecycle of an Alloha streaming session:
 * parser (WebView), proxy server, and active headers.
 *
 * One instance is held per WatchSelectorViewModel so the session
 * survives configuration changes. Call [release] when done.
 */
class AllohaSessionManager(private val context: Context) {

    val activeHeaders = ConcurrentHashMap<String, String>()

    var parser: AllohaParser? = null
        private set

    var hlsProxy: HlsProxyServer? = null
        private set

    /** The current master.m3u8 CDN URL captured from the iframe. */
    @Volatile
    var currentM3u8Url: String = ""
        private set

    /** The fallback CDN master URL (second URL after " or " in bnsi). */
    @Volatile
    var fallbackM3u8Url: String = ""
        private set

    /** Localhost URL that ExoPlayer should use. */
    val proxyMasterUrl: String get() = hlsProxy?.fixedMasterUrl ?: ""

    @Volatile
    private var configUpdateReceived = false

    private var proactiveRestartJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Callback invoked when the session has a playable stream ready. */
    var onStreamReady: ((qualitiesJson: String, m3u8Url: String) -> Unit)? = null

    /** Callback invoked when the session fails. */
    var onError: ((String) -> Unit)? = null

    /** Callback invoked when the proxy CDN url is refreshed. */
    var onM3u8Updated: ((String) -> Unit)? = null

    /** Quality map from last bnsi parse (e.g. "1080" -> URL). */
    var lastQualityMap: Map<String, String> = emptyMap()
        private set

    /** Currently selected quality key. */
    @Volatile
    var lastSelectedQuality: String = ""

    /**
     * Switch to a different quality by updating the proxy's master URL.
     * Caller should re-prepare ExoPlayer after this returns.
     */
    fun switchQuality(qualityKey: String) {
        val url = lastQualityMap[qualityKey] ?: return
        currentM3u8Url = url
        lastSelectedQuality = qualityKey
        hlsProxy?.updateMasterUrl(url)
    }

    fun ensureInitialized() {
        if (parser == null) {
            parser = AllohaParser(context)
        }
        if (hlsProxy == null) {
            hlsProxy = HlsProxyServer(activeHeaders, onSessionExpired = {
                val iframe = parser?.lastIframeUrl
                if (!iframe.isNullOrBlank()) {
                    Log.d(TAG, "Proxy: session expired, forcing restart")
                    startSession(iframe, isRestart = true)
                }
            })
            hlsProxy!!.start()
        }
    }

    fun startSession(iframeUrl: String, isRestart: Boolean = false) {
        ensureInitialized()

        val p = parser ?: return
        p.rotateUserAgent()
        configUpdateReceived = false
        fallbackM3u8Url = ""

        val parsedUrl = URL(iframeUrl)
        val iframeOrigin = "${parsedUrl.protocol}://${parsedUrl.host.lowercase(Locale.ROOT)}"

        p.parse(iframeUrl, object : AllohaParser.Callback {
            override fun onHlsLinksReceived(json: String, extraHeaders: Map<String, String>) {
                try {
                    val jsonObj = JSONObject(json)
                    val hlsSource = jsonObj.optJSONArray("hlsSource")
                        ?: throw IllegalStateException("No hlsSource in response")

                    val qualitiesMap = mutableMapOf<String, String>()
                    fallbackM3u8Url = ""
                    // Only use the first hlsSource entry — it corresponds to the selected translation.
                    // Iterating all entries overwrites qualitiesMap with the last (often English) entry.
                    val firstSource = hlsSource.optJSONObject(0)
                    val qualityObj = firstSource?.optJSONObject("quality")
                    if (qualityObj != null) {
                        qualityObj.keys().forEach { q ->
                            val parts = qualityObj.optString(q, "").split(" or ")
                            val link = parts[0].trim()
                            if (link.isNotBlank()) {
                                qualitiesMap[q] = if (link.startsWith("//")) "https:$link" else link
                            }
                            if (fallbackM3u8Url.isBlank() && parts.size > 1) {
                                val fb = parts[1].trim()
                                if (fb.isNotBlank()) fallbackM3u8Url = if (fb.startsWith("//")) "https:$fb" else fb
                            }
                        }
                    }
                    if (qualitiesMap.isEmpty()) throw IllegalStateException("No qualities found")

                    activeHeaders.clear()
                    activeHeaders.putAll(extraHeaders)

                    // Parse subtitle tracks from bnsi JSON (field: "tracks")
                    val subtitles = mutableListOf<Triple<String, String, String>>()
                    val tracksArray = jsonObj.optJSONArray("tracks")
                    if (tracksArray != null) {
                        for (i in 0 until tracksArray.length()) {
                            val track = tracksArray.optJSONObject(i) ?: continue
                            if (track.optString("kind") != "captions") continue
                            val url = track.optString("src", "")
                            val lang = track.optString("language", "und")
                            val name = track.optString("label", lang)
                            if (url.isNotBlank()) {
                                subtitles.add(Triple(lang, name, if (url.startsWith("//")) "https:$url" else url))
                            }
                        }
                    }
                    Log.d(TAG, "Parsed ${subtitles.size} subtitle tracks")
                    hlsProxy?.subtitleTracks = subtitles

                    // Parse skip times (e.g. "436-539,3628-3700")
                    val skipTimeStr = jsonObj.optString("skipTime", "")
                    Log.d(TAG, "skipTime: '$skipTimeStr'")
                    AllohaSessionHolder.skipRanges = parseSkipRanges(skipTimeStr)

                    lastQualityMap = qualitiesMap.toMap()

                    if (!isRestart) {
                        // Prefer previously selected quality (persists across episodes in same session)
                        val savedKey = AllohaSessionHolder.currentQuality
                        val bestKey = if (savedKey.isNotBlank() && qualitiesMap.containsKey(savedKey)) {
                            savedKey
                        } else {
                            pickBestQuality(context, qualitiesMap)
                        }
                        val bestUrl = qualitiesMap[bestKey] ?: qualitiesMap.values.first()
                        currentM3u8Url = bestUrl
                        lastSelectedQuality = bestKey
                        onStreamReady?.invoke(json, bestUrl)
                    }
                    // For restarts: wait for onM3u8Refreshed to get the fresh signed URL
                } catch (e: Exception) {
                    Log.e(TAG, "onHlsLinksReceived error: ${e.message}")
                    onError?.invoke("Parse error: ${e.message}")
                }
            }

            override fun onConfigUpdate(edgeHash: String, ttlSeconds: Int, extraHeaders: Map<String, String>) {
                activeHeaders.putAll(extraHeaders)
                Log.d(TAG, "config_update: edge_hash=$edgeHash TTL=${ttlSeconds}s")

                val ttlMs = ttlSeconds * 1000L
                proactiveRestartJob?.cancel()
                proactiveRestartJob = scope.launch {
                    delay((ttlMs - 20_000L).coerceAtLeast(ttlMs / 2))
                    val iframe = p.lastIframeUrl
                    if (iframe.isNotBlank()) {
                        Log.d(TAG, "Proactive session restart before TTL expiry")
                        startSession(iframe, isRestart = true)
                    }
                }

                if (!configUpdateReceived) {
                    configUpdateReceived = true
                    if (!isRestart && currentM3u8Url.isNotBlank()) {
                        hlsProxy?.updateMasterUrl(currentM3u8Url)
                        onM3u8Updated?.invoke(currentM3u8Url)
                    }
                }
            }

            override fun onM3u8Refreshed(url: String, extraHeaders: Map<String, String>) {
                activeHeaders.putAll(extraHeaders)
                val prevHost = currentM3u8Url.substringAfter("://").substringBefore("/")
                val newHost = url.substringAfter("://").substringBefore("/")
                currentM3u8Url = url
                Log.d(TAG, "m3u8 refreshed: $url")

                if (configUpdateReceived) {
                    val hostChanged = prevHost.isNotBlank() && prevHost != newHost
                    if (hostChanged) {
                        configUpdateReceived = false
                        Log.d(TAG, "CDN host changed $prevHost -> $newHost, waiting for config_update")
                    } else if (isRestart) {
                        // Proactive restart: update URL silently — no cache reset, player keeps playing
                        hlsProxy?.updateMasterUrlSilently(url)
                        Log.d(TAG, "Proactive restart: CDN URL updated silently")
                    } else {
                        hlsProxy?.updateMasterUrl(url)
                        onM3u8Updated?.invoke(url)
                    }
                }
            }

            override fun onStreamHeadersUpdated(extraHeaders: Map<String, String>) {
                activeHeaders.putAll(extraHeaders)
            }

            override fun onError(error: String) {
                Log.e(TAG, "Parser error: $error")
                this@AllohaSessionManager.onError?.invoke(error)
            }
        })
    }

    fun release() {
        proactiveRestartJob?.cancel()
        scope.cancel()
        hlsProxy?.stop()
        hlsProxy = null
        parser?.release()
        parser = null
        activeHeaders.clear()
    }

    private fun pickBestQuality(context: Context, qualities: Map<String, String>): String {
        val supportsAv1 = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.any { info ->
            !info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AV1, ignoreCase = true) }
        }
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val downMbps = caps?.linkDownstreamBandwidthKbps?.div(1000) ?: 0

        // Auto quality caps at 1080p — higher resolutions (2K/4K) require AV1 and manual selection.
        // AV1 constraint: if not supported, 1080p is the ceiling regardless of bandwidth.
        val maxRes = when {
            !supportsAv1 -> 1080
            isWifi || downMbps >= 10 -> 1080
            downMbps >= 5 -> 720
            else -> 480
        }

        val ordered = listOf("1080", "720", "480", "360")
            .filter { (it.toIntOrNull() ?: 0) <= maxRes }
        return ordered.firstOrNull { qualities.containsKey(it) }
            ?: listOf("1080", "720", "480", "360", "1440", "2160")
                .firstOrNull { qualities.containsKey(it) }
            ?: qualities.keys.first()
    }

    private fun parseSkipRanges(skipTime: String): List<LongRange> {
        if (skipTime.isBlank()) return emptyList()
        return skipTime.split(",").mapNotNull { part ->
            val (s, e) = part.trim().split("-").takeIf { it.size == 2 } ?: return@mapNotNull null
            val start = s.toLongOrNull() ?: return@mapNotNull null
            val end = e.toLongOrNull() ?: return@mapNotNull null
            (start * 1000)..(end * 1000)
        }
    }
}
