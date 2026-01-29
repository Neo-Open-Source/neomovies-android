package com.neo.neomovies.data.collaps

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

class CollapsRepository(
    private val okHttpClient: OkHttpClient,
    private val context: Context,
    private val base: String = "https://api.luxembd.ws",
) {
    data class CollapsSubtitle(
        val url: String,
        val label: String,
        val lang: String,
    )

    data class CollapsEpisode(
        val season: Int,
        val episode: Int,
        val mpdUrl: String?,
        val hlsUrl: String?,
        val voices: List<String>,
        val subtitles: List<CollapsSubtitle>,
    )

    data class CollapsSeason(
        val season: Int,
        val episodes: List<CollapsEpisode>,
    )

    data class CollapsMovie(
        val mpdUrl: String?,
        val hlsUrl: String?,
        val voices: List<String>,
        val subtitles: List<CollapsSubtitle>,
    )

    suspend fun getSeasonsByKpId(kpId: Int): List<CollapsSeason> {
        val html = fetchEmbedHtml("$base/embed/kp/$kpId")
        val seasonsJson = extractSeasonsJson(html) ?: return emptyList()

        val arr = JSONArray(seasonsJson)
        val seasons = ArrayList<CollapsSeason>(arr.length())

        for (i in 0 until arr.length()) {
            val sObj = arr.getJSONObject(i)
            val seasonNum = sObj.optInt("season", -1)
            if (seasonNum <= 0) continue

            val epsArr = sObj.optJSONArray("episodes") ?: continue
            val episodes = ArrayList<CollapsEpisode>(epsArr.length())

            for (j in 0 until epsArr.length()) {
                val eObj = epsArr.getJSONObject(j)
                val epStr = eObj.optString("episode", "")
                val epNum = epStr.toIntOrNull() ?: continue

                val hls = eObj.optString("hls", "").takeIf { it.isNotBlank() }
                val dasha = eObj.optString("dasha", "").takeIf { it.isNotBlank() }
                val dash = eObj.optString("dash", "").takeIf { it.isNotBlank() }

                val mpd = (dasha ?: dash)

                val voices = ArrayList<String>()
                val audio = eObj.optJSONObject("audio")
                val names = audio?.optJSONArray("names")
                if (names != null) {
                    for (k in 0 until names.length()) {
                        val v = names.optString(k, "").trim()
                        if (v.isNotBlank()) voices.add(v)
                    }
                }

                val subtitles = ArrayList<CollapsSubtitle>()
                val cc = eObj.optJSONArray("cc")
                if (cc != null) {
                    for (k in 0 until cc.length()) {
                        val sObj = cc.optJSONObject(k) ?: continue
                        val url = (sObj.optString("url", "").ifBlank { sObj.optString("src", "") }).trim()
                        if (url.isBlank()) continue

                        val label = (sObj.optString("name", "").ifBlank { sObj.optString("label", "") }).trim()
                            .ifBlank { "Subtitle" }

                        val langRaw = sObj.optString("lang", "").trim()
                        val lang = when {
                            langRaw.isNotBlank() -> langRaw
                            label.contains("eng", ignoreCase = true) || label.contains("original", ignoreCase = true) -> "en"
                            else -> "ru"
                        }

                        subtitles.add(CollapsSubtitle(url = url, label = label, lang = lang))
                    }
                }

                episodes.add(
                    CollapsEpisode(
                        season = seasonNum,
                        episode = epNum,
                        mpdUrl = mpd,
                        hlsUrl = hls,
                        voices = voices,
                        subtitles = subtitles,
                    )
                )
            }

            episodes.sortBy { it.episode }
            seasons.add(CollapsSeason(season = seasonNum, episodes = episodes))
        }

        seasons.sortBy { it.season }
        return seasons
    }

    suspend fun getMovieByKpId(kpId: Int): CollapsMovie? {
        val html = fetchEmbedHtml("$base/embed/kp/$kpId")

        val mpdUrl = extractFirstUrl(html, listOf("dasha", "dash"), ".mpd")
        val hlsUrl = extractFirstUrl(html, listOf("hls"), ".m3u8")

        val voices = extractStringArrayFromObject(html, "audio", "names")
            .ifEmpty { extractVoiceNamesFromTranslations(html) }
        val subtitles = extractSubtitlesArray(html)

        if (mpdUrl.isNullOrBlank() && hlsUrl.isNullOrBlank()) return null
        return CollapsMovie(
            mpdUrl = mpdUrl,
            hlsUrl = hlsUrl,
            voices = voices,
            subtitles = subtitles,
        )
    }

    suspend fun buildRewrittenMpdUri(
        kpId: Int,
        season: Int,
        episode: Int,
        mpdUrl: String,
        voices: List<String>,
        subtitles: List<CollapsSubtitle>,
    ): String {
        val mpd = httpGet(mpdUrl)
        val rewritten = CollapsMpdRewriter.rewrite(mpd, voices, subtitles)

        val outDir = File(context.cacheDir, "collaps")
        outDir.mkdirs()

        val outFile = File(outDir, "kp_${kpId}_s${season}_e${episode}.mpd")
        outFile.writeText(rewritten)

        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", outFile)
        return uri.toString()
    }

    private fun buildSubtitlePlaylist(subtitleUrl: String): String {
        // HLS subtitles expect a WebVTT playlist (.m3u8). Collaps often provides a single .vtt file.
        // Use a simple VOD playlist with one segment referencing the absolute subtitle URL.
        return """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:999999
            #EXT-X-MEDIA-SEQUENCE:0
            #EXTINF:999999,
            $subtitleUrl
            #EXT-X-ENDLIST
        """.trimIndent()
    }

    suspend fun buildRewrittenHlsUri(
        kpId: Int,
        season: Int,
        episode: Int,
        hlsUrl: String,
        voices: List<String>,
        subtitles: List<CollapsSubtitle>,
    ): String {
        val outDir = File(context.cacheDir, "collaps")
        outDir.mkdirs()

        val rewrittenSubtitles =
            subtitles.mapIndexedNotNull { idx, s ->
                val url = s.url.trim()
                if (url.isBlank()) return@mapIndexedNotNull null

                val playlist = buildSubtitlePlaylist(url)
                val subFile = File(outDir, "kp_${kpId}_s${season}_e${episode}_sub$idx.m3u8")
                subFile.writeText(playlist)

                // Use a relative URI so:
                // - ExoPlayer can resolve it against the base content:// master URI
                // - MPV can resolve it against the local filesystem master path
                s.copy(url = subFile.name)
            }

        val master = httpGet(hlsUrl)
        val rewritten = CollapsHlsRewriter.rewrite(master, voices, rewrittenSubtitles)

        val outFile = File(outDir, "kp_${kpId}_s${season}_e${episode}.m3u8")
        outFile.writeText(rewritten)

        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", outFile)
        return uri.toString()
    }

    suspend fun dashContainsAv1(mpdUrl: String): Boolean {
        val mpd = httpGet(mpdUrl)
        return mpdContainsAv1(mpd)
    }

    fun mpdContainsAv1(mpd: String): Boolean {
        return mpd.contains("av01", ignoreCase = true)
    }

    private suspend fun fetchEmbedHtml(url: String): String = withContext(Dispatchers.IO) {
        httpGet(url)
    }

    private fun extractSeasonsJson(html: String): String? {
        val idx = html.indexOf("seasons:")
        if (idx < 0) return null

        val start = idx + "seasons:".length
        var end = start
        while (end < html.length) {
            val c = html[end]
            if (c == '\n' || c == '\r') break
            end++
        }

        return html.substring(start, end).trim().takeIf { it.startsWith("[") }
    }

    private fun extractFirstUrl(html: String, keys: List<String>, suffix: String): String? {
        for (key in keys) {
            val r = Regex("(?is)\\b${Regex.escape(key)}\\s*:\\s*['\"]([^'\"]+${Regex.escape(suffix)}[^'\"]*)['\"]")
            val m = r.find(html)
            if (m != null) return m.groupValues.getOrNull(1)?.trim()
        }
        return null
    }

    private fun extractStringArrayFromObject(html: String, objectKey: String, arrayKey: String): List<String> {
        val key1 = Regex.escape(objectKey)
        val key2 = Regex.escape(arrayKey)
        val patterns = listOf(
            // Unquoted keys: audio: { names: [ ... ] }
            Regex("(?is)\\b$key1\\s*:\\s*\\{.*?\\b$key2\\s*:\\s*(\\[[\\s\\S]*?\\])"),
            // Quoted keys: \"audio\": { \"names\": [ ... ] }
            Regex("(?is)\\\"$key1\\\"\\s*:\\s*\\{.*?\\\"$key2\\\"\\s*:\\s*(\\[[\\s\\S]*?\\])"),
        )

        val raw = patterns.firstNotNullOfOrNull { it.find(html)?.groupValues?.getOrNull(1) }?.trim()
            ?: return emptyList()
        if (!raw.startsWith("[")) return emptyList()

        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val v = arr.optString(i, "").trim()
                    if (v.isNotBlank()) add(v)
                }
            }
        }.getOrElse {
            parseJsStringArray(raw)
        }
    }

    private fun extractVoiceNamesFromTranslations(html: String): List<String> {
        val patterns = listOf(
            Regex("(?is)\\btranslations\\s*:\\s*(\\[[\\s\\S]*?\\])"),
            Regex("(?is)\\\"translations\\\"\\s*:\\s*(\\[[\\s\\S]*?\\])"),
        )

        val raw = patterns.firstNotNullOfOrNull { it.find(html)?.groupValues?.getOrNull(1) }?.trim()
            ?: return emptyList()
        if (!raw.startsWith("[")) return emptyList()

        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val name = o.optString("name", "").trim().ifBlank { o.optString("title", "").trim() }
                    if (name.isNotBlank()) add(name)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun parseJsStringArray(raw: String): List<String> {
        // Handles JS arrays that are not strict JSON (e.g. single quotes, unescaped chars).
        // Example: ['Rus', "Eng.Original"]
        return Regex("['\"]([^'\"]+)['\"]")
            .findAll(raw)
            .map { it.groupValues.getOrNull(1).orEmpty().trim() }
            .filter { it.isNotBlank() }
            .toList()
    }

    private fun extractSubtitlesArray(html: String): List<CollapsSubtitle> {
        val r = Regex("(?is)\\bcc\\s*:\\s*(\\[[^\\]]*\\])")
        val m = r.find(html) ?: return emptyList()
        val raw = m.groupValues.getOrNull(1).orEmpty().trim()
        if (!raw.startsWith("[")) return emptyList()

        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val sObj = arr.optJSONObject(i) ?: continue
                    val url = (sObj.optString("url", "").ifBlank { sObj.optString("src", "") }).trim()
                    if (url.isBlank()) continue

                    val label = (sObj.optString("name", "").ifBlank { sObj.optString("label", "") }).trim()
                        .ifBlank { "Subtitle" }

                    val langRaw = sObj.optString("lang", "").trim()
                    val lang = when {
                        langRaw.isNotBlank() -> langRaw
                        label.contains("eng", ignoreCase = true) || label.contains("original", ignoreCase = true) -> "en"
                        else -> "ru"
                    }

                    add(CollapsSubtitle(url = url, label = label, lang = lang))
                }
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
        val request =
            Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

        okHttpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }
    }
}
