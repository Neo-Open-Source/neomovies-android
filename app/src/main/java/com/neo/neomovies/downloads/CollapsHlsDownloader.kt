package com.neo.neomovies.downloads

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.context.GlobalContext
import java.io.File

/**
 * Downloads a Collaps HLS stream into a local folder:
 *   <outputDir>/
 *     video.ts        — video segments concatenated
 *     audio.ts        — selected audio track segments concatenated (if separate)
 *     sub_N.vtt       — subtitle files
 *     master.m3u8     — local HLS playlist referencing the above
 *
 * ExoPlayer can play the local master.m3u8 directly via file:// URI.
 */
object CollapsHlsDownloader {

    data class Progress(val downloaded: Int, val total: Int)

    data class DownloadResult(
        /** Path to the local master.m3u8 (or video.ts if no separate audio) */
        val masterPath: String,
    )

    suspend fun download(
        context: Context,
        hlsUrl: String,
        /** Preferred voice name to match against EXT-X-MEDIA NAME attribute */
        preferredVoice: String? = null,
        outputDir: File,
        onProgress: (Progress) -> Unit = {},
    ): DownloadResult = withContext(Dispatchers.IO) {
        val okHttp = runCatching { GlobalContext.get().get<OkHttpClient>() }.getOrNull()
            ?: OkHttpClient()

        outputDir.mkdirs()

        // 1. Fetch master manifest
        val masterText = httpGet(okHttp, hlsUrl)

        // 2. Parse master: video variants + audio/subtitle tracks
        val parsed = parseMaster(masterText, hlsUrl)

        if (parsed.videoVariants.isEmpty()) {
            // Not a multi-variant stream — download directly as video.ts
            val videoFile = File(outputDir, "video.ts")
            downloadSegments(okHttp, hlsUrl, videoFile, onProgress)
            return@withContext DownloadResult(videoFile.absolutePath)
        }

        // 3. Pick best video variant
        val bestVideo = parsed.videoVariants.maxByOrNull { it.bandwidth }!!

        // 4. Pick audio track — prefer matching voice name, else first
        val audioTrack = if (parsed.audioTracks.isNotEmpty()) {
            parsed.audioTracks.firstOrNull { track ->
                preferredVoice != null && track.name.contains(preferredVoice, ignoreCase = true)
            } ?: parsed.audioTracks.first()
        } else null

        // 5. Count total segments for progress
        val videoPlaylist = httpGet(okHttp, bestVideo.uri)
        val videoSegments = parseSegments(videoPlaylist, bestVideo.uri)

        val audioSegments = if (audioTrack != null) {
            val audioPlaylist = httpGet(okHttp, audioTrack.uri)
            parseSegments(audioPlaylist, audioTrack.uri)
        } else emptyList()

        val totalSegments = videoSegments.size + audioSegments.size
        var downloaded = 0

        // 6. Download video segments
        val videoFile = File(outputDir, "video.ts")
        videoFile.outputStream().buffered().use { out ->
            for (segUrl in videoSegments) {
                if (!isActive) return@withContext DownloadResult(videoFile.absolutePath)
                out.write(httpGetBytes(okHttp, segUrl))
                onProgress(Progress(++downloaded, totalSegments))
            }
        }

        // 7. Download audio segments
        val audioFile = if (audioSegments.isNotEmpty()) {
            val f = File(outputDir, "audio.ts")
            f.outputStream().buffered().use { out ->
                for (segUrl in audioSegments) {
                    if (!isActive) return@withContext DownloadResult(videoFile.absolutePath)
                    out.write(httpGetBytes(okHttp, segUrl))
                    onProgress(Progress(++downloaded, totalSegments))
                }
            }
            f
        } else null

        // 8. Download subtitles
        val subtitleFiles = parsed.subtitleTracks.mapIndexedNotNull { idx, sub ->
            runCatching {
                val vttContent = httpGet(okHttp, sub.uri)
                val f = File(outputDir, "sub_$idx.vtt")
                f.writeText(vttContent)
                SubtitleFile(name = sub.name, lang = sub.lang, file = f)
            }.getOrNull()
        }

        // 9. Write local master.m3u8
        val masterFile = File(outputDir, "master.m3u8")
        masterFile.writeText(buildLocalMaster(
            videoFile = videoFile,
            audioFile = audioFile,
            audioName = audioTrack?.name,
            audioLang = audioTrack?.lang,
            subtitles = subtitleFiles,
            videoInfo = bestVideo,
        ))

        DownloadResult(masterPath = masterFile.absolutePath)
    }

    // ── Manifest parsing ──────────────────────────────────────────────────────

    data class VideoVariant(val uri: String, val bandwidth: Int, val resolution: String?)
    data class MediaTrack(val uri: String, val name: String, val lang: String, val groupId: String)
    data class SubtitleFile(val name: String, val lang: String, val file: File)

    data class ParsedMaster(
        val videoVariants: List<VideoVariant>,
        val audioTracks: List<MediaTrack>,
        val subtitleTracks: List<MediaTrack>,
    )

    private fun parseMaster(master: String, baseUrl: String): ParsedMaster {
        val lines = master.lines()
        val videos = mutableListOf<VideoVariant>()
        val audio = mutableListOf<MediaTrack>()
        val subs = mutableListOf<MediaTrack>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            when {
                line.startsWith("#EXT-X-MEDIA:", ignoreCase = true) -> {
                    val type = extractAttr(line, "TYPE") ?: ""
                    val uri = extractAttr(line, "URI")?.let { resolveUrl(baseUrl, it.trim('"')) }
                    val name = extractAttr(line, "NAME")?.trim('"') ?: ""
                    val lang = extractAttr(line, "LANGUAGE")?.trim('"') ?: ""
                    val groupId = extractAttr(line, "GROUP-ID")?.trim('"') ?: ""
                    if (uri != null) {
                        val track = MediaTrack(uri = uri, name = name, lang = lang, groupId = groupId)
                        when (type.uppercase()) {
                            "AUDIO" -> audio.add(track)
                            "SUBTITLES" -> subs.add(track)
                        }
                    }
                    i++
                }
                line.startsWith("#EXT-X-STREAM-INF:", ignoreCase = true) -> {
                    val bw = extractAttr(line, "BANDWIDTH")?.toIntOrNull() ?: 0
                    val res = extractAttr(line, "RESOLUTION")
                    val uriLine = lines.getOrNull(i + 1)?.trim() ?: ""
                    if (uriLine.isNotBlank() && !uriLine.startsWith("#")) {
                        videos.add(VideoVariant(
                            uri = resolveUrl(baseUrl, uriLine),
                            bandwidth = bw,
                            resolution = res,
                        ))
                    }
                    i += 2
                }
                else -> i++
            }
        }
        return ParsedMaster(videos, audio, subs)
    }

    private fun extractAttr(line: String, key: String): String? {
        val pattern = Regex("\\b${Regex.escape(key)}=([^,\\s]+|\"[^\"]*\")", RegexOption.IGNORE_CASE)
        return pattern.find(line)?.groupValues?.getOrNull(1)?.trim('"')
    }

    private fun parseSegments(playlist: String, baseUrl: String): List<String> {
        return playlist.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { resolveUrl(baseUrl, it) }
    }

    // ── Local master.m3u8 builder ─────────────────────────────────────────────

    private fun buildLocalMaster(
        videoFile: File,
        audioFile: File?,
        audioName: String?,
        audioLang: String?,
        subtitles: List<SubtitleFile>,
        videoInfo: VideoVariant,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("#EXTM3U")
        sb.appendLine("#EXT-X-VERSION:3")

        // Audio group
        if (audioFile != null) {
            val lang = audioLang?.takeIf { it.isNotBlank() } ?: "ru"
            val name = audioName?.takeIf { it.isNotBlank() } ?: "Audio"
            sb.appendLine("""#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="$name",LANGUAGE="$lang",DEFAULT=YES,URI="${audioFile.name}"""")
        }

        // Subtitle groups
        subtitles.forEachIndexed { idx, sub ->
            val lang = sub.lang.takeIf { it.isNotBlank() } ?: "und"
            sb.appendLine("""#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="${sub.name}",LANGUAGE="$lang",DEFAULT=${if (idx == 0) "YES" else "NO"},URI="${sub.file.name}"""")
        }

        // Video stream
        val audioAttr = if (audioFile != null) """,AUDIO="audio"""" else ""
        val subsAttr = if (subtitles.isNotEmpty()) """,SUBTITLES="subs"""" else ""
        val res = videoInfo.resolution?.let { ",RESOLUTION=$it" } ?: ""
        sb.appendLine("""#EXT-X-STREAM-INF:BANDWIDTH=${videoInfo.bandwidth}$res$audioAttr$subsAttr""")
        sb.appendLine(videoFile.name)

        return sb.toString()
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private fun resolveUrl(base: String, path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val idx = base.lastIndexOf('/')
        return if (idx == -1) path else base.substring(0, idx + 1) + path
    }

    private fun httpGet(client: OkHttpClient, url: String): String {
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code} for $url")
            resp.body?.string().orEmpty()
        }
    }

    private fun httpGetBytes(client: OkHttpClient, url: String): ByteArray {
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code} for $url")
            resp.body?.bytes() ?: ByteArray(0)
        }
    }

    private fun downloadSegments(client: OkHttpClient, url: String, outputFile: File, onProgress: (Progress) -> Unit) {
        val playlist = httpGet(client, url)
        val segments = parseSegments(playlist, url)
        if (segments.isEmpty()) {
            outputFile.writeBytes(httpGetBytes(client, url))
            return
        }
        outputFile.outputStream().buffered().use { out ->
            segments.forEachIndexed { idx, segUrl ->
                out.write(httpGetBytes(client, segUrl))
                onProgress(Progress(idx + 1, segments.size))
            }
        }
    }
}
