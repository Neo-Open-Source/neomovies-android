package com.neo.neomovies.data.alloha

import android.content.Context
import android.util.Log
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
                    for (i in 0 until hlsSource.length()) {
                        val qualityObj = hlsSource.getJSONObject(i).optJSONObject("quality") ?: continue
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

                    // Store all qualities for the player's quality picker
                    lastQualityMap = qualitiesMap.toMap()

                    // Pick best quality as default m3u8
                    val bestKey = listOf("1080", "720", "1440", "2160", "480", "360")
                        .firstOrNull { qualitiesMap.containsKey(it) }
                    val bestUrl = bestKey?.let { qualitiesMap[it] } ?: qualitiesMap.values.first()
                    currentM3u8Url = bestUrl
                    lastSelectedQuality = bestKey ?: ""

                    onStreamReady?.invoke(json, bestUrl)
                } catch (e: Exception) {
                    Log.e(TAG, "onHlsLinksReceived error: ${e.message}")
                    onError?.invoke("Parse error: ${e.message}")
                }
            }

            override fun onConfigUpdate(edgeHash: String, ttlSeconds: Int, extraHeaders: Map<String, String>) {
                activeHeaders.putAll(extraHeaders)
                Log.d(TAG, "config_update: edge_hash=$edgeHash TTL=${ttlSeconds}s")

                // Schedule proactive session restart before TTL expires
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
                    // Update proxy with the current CDN URL now that auth is valid
                    if (currentM3u8Url.isNotBlank()) {
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
}
