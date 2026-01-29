package com.neo.player

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.neo.player.mpv.MPVPlayer
import java.io.File
import java.net.URLDecoder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.os.Bundle
import android.util.Log
import android.os.Build

class PlayerViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application), Player.Listener {

    var player: Player
        private set

    private var useExo: Boolean = false

    var playWhenReady: Boolean = true
    var playbackSpeed: Float = 1f

    var isInPictureInPictureMode: Boolean = false

    private var baseTitle: String = ""

    private val forceFirstAudioTrack: Boolean by lazy {
        savedStateHandle.get<Boolean>(PlayerActivity.EXTRA_USE_COLLAPS_HEADERS) ?: false
    }

    private var appliedFirstAudioOverride: Boolean = false

    private val _uiState =
        MutableStateFlow(
            UiState(
                currentItemTitle = "",
                fileLoaded = false,
            )
        )
    val uiState = _uiState.asStateFlow()

    private val eventsChannel = Channel<PlayerEvents>(capacity = Channel.BUFFERED)
    val eventsChannelFlow = eventsChannel.receiveAsFlow()

    data class UiState(
        val currentItemTitle: String,
        val fileLoaded: Boolean,
    )

    private val prefs by lazy {
        application.getSharedPreferences("player_progress", Context.MODE_PRIVATE)
    }

    init {
        val audioAttributes =
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build()

        useExo = savedStateHandle.get<Boolean>(PlayerActivity.EXTRA_USE_EXO) ?: false
        player = createPlayer(useExo, audioAttributes)
        player.addListener(this)
    }

    fun setEngine(useExo: Boolean) {
        if (this.useExo == useExo) return
        this.useExo = useExo

        val audioAttributes =
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build()

        player.removeListener(this)
        player.release()

        player = createPlayer(useExo, audioAttributes)
        player.addListener(this)
    }

    private fun createPlayer(useExo: Boolean, audioAttributes: AudioAttributes): Player {
        return if (!useExo) {
            val builder =
                MPVPlayer.Builder(getApplication())
                .setAudioAttributes(audioAttributes, true)
                .setSeekBackIncrementMs(10_000)
                .setSeekForwardIncrementMs(10_000)
                .setPauseAtEndOfMediaItems(true)

            val useCollapsHeaders =
                savedStateHandle.get<Boolean>(PlayerActivity.EXTRA_USE_COLLAPS_HEADERS) ?: false
            if (useCollapsHeaders) {
                builder.setHttpHeaders(
                    userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                    referrer = "https://kinokrad.my/",
                    headerFields = "Referer: https://kinokrad.my/,Origin: https://kinokrad.my",
                )
                builder.setDefaultAudioTrack(1)
            }

            builder.build()
        } else {
            // "One in one" with moneytoo/Player: ExoPlayer tuned for streaming/TS.
            val trackSelector =
                DefaultTrackSelector(getApplication()).apply {
                    setParameters(buildUponParameters().setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true))
                }

            val extractorsFactory =
                DefaultExtractorsFactory()
                    .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS)
                    .setTsExtractorTimestampSearchBytes(1500 * TsExtractor.TS_PACKET_SIZE)

            val httpDataSourceFactory = DefaultHttpDataSource.Factory()

            val useCollapsHeaders =
                savedStateHandle.get<Boolean>(PlayerActivity.EXTRA_USE_COLLAPS_HEADERS) ?: false
            if (useCollapsHeaders) {
                httpDataSourceFactory.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                httpDataSourceFactory.setDefaultRequestProperties(
                    mapOf(
                        "Referer" to "https://kinokrad.my/",
                        "Origin" to "https://kinokrad.my",
                    )
                )
            }

            val dataSourceFactory = DefaultDataSource.Factory(getApplication(), httpDataSourceFactory)
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)

            @Suppress("WrongConstant")
            val extensionMode =
                if (
                    Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                    Build.MODEL.contains("emulator", ignoreCase = true) ||
                    Build.MODEL.contains("sdk_gphone", ignoreCase = true) ||
                    Build.MANUFACTURER.contains("genymotion", ignoreCase = true)
                ) {
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                } else {
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                }

            val renderersFactory: RenderersFactory =
                DefaultRenderersFactory(getApplication())
                    .setExtensionRendererMode(extensionMode)
                    .setEnableDecoderFallback(true)

            ExoPlayer.Builder(getApplication(), renderersFactory)
                .setTrackSelector(trackSelector)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .apply {
                    setAudioAttributes(audioAttributes, true)
                    addAnalyticsListener(
                        object : AnalyticsListener {
                            override fun onVideoDecoderInitialized(
                                eventTime: AnalyticsListener.EventTime,
                                decoderName: String,
                                initializedTimestampMs: Long,
                                initializationDurationMs: Long,
                            ) {
                                Log.d("PlayerVM", "VideoDecoderInitialized: $decoderName")
                            }

                            override fun onAudioDecoderInitialized(
                                eventTime: AnalyticsListener.EventTime,
                                decoderName: String,
                                initializedTimestampMs: Long,
                                initializationDurationMs: Long,
                            ) {
                                Log.d("PlayerVM", "AudioDecoderInitialized: $decoderName")
                            }

                            override fun onPlayerError(
                                eventTime: AnalyticsListener.EventTime,
                                error: androidx.media3.common.PlaybackException,
                            ) {
                                Log.e(
                                    "PlayerVM",
                                    "PlayerError: ${error.errorCodeName} ${error.message}",
                                    error,
                                )
                            }
                        }
                    )
                }
        }
    }

    fun initializePlayer(
        urls: List<String>,
        names: List<String>?,
        startIndex: Int,
        title: String?,
        startFromBeginning: Boolean,
    ) {
        baseTitle = title?.takeIf { it.isNotBlank() } ?: ""
        _uiState.update { it.copy(currentItemTitle = baseTitle, fileLoaded = false) }

        appliedFirstAudioOverride = false

        Log.d(
            "PlayerVM",
            "initializePlayer urls=${urls.size} names=${names?.size} startIndex=$startIndex nameAtIndex=${names?.getOrNull(startIndex)}",
        )

        val resolvedUrls =
            if (!useExo) {
                urls.map { resolveMpvUri(it) }
            } else {
                urls
            }

        val mediaItems =
            resolvedUrls.mapIndexed { index, url ->
                val displayName = names?.getOrNull(index).orEmpty()
                val extras = Bundle().apply {
                    putString("display_name", displayName)
                }
                MediaItem.Builder()
                    .setMediaId(url)
                    .setUri(url)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(baseTitle)
                            .setExtras(extras)
                            .build(),
                    )
                    .build()
            }

        val currentUrl = resolvedUrls.getOrNull(startIndex) ?: resolvedUrls.firstOrNull().orEmpty()
        val startPosition =
            if (startFromBeginning) {
                0L
            } else {
                prefs.getLong("pos_" + currentUrl, 0L)
            }

        player.setMediaItems(mediaItems.toMutableList(), startIndex, startPosition)
        player.prepare()
        player.playWhenReady = true
        player.play()
    }

    private fun resolveMpvUri(url: String): String {
        val authority = getApplication<Application>().packageName + ".fileprovider"
        val prefix = "content://$authority/cache/collaps/"
        if (!url.startsWith(prefix)) return url

        val fileName = url.removePrefix(prefix).substringBefore('?').substringBefore('#')
        if (fileName.isBlank()) return url

        val f = File(getApplication<Application>().cacheDir, "collaps/$fileName")
        // libmpv/ffmpeg may not be able to detect MPD/HLS formats through content:// streams reliably.
        // Prefer a real filesystem path when possible.
        if (!f.exists()) return url

        if (f.extension.equals("mpd", ignoreCase = true)) {
            val hls = File(f.parentFile, f.nameWithoutExtension + ".m3u8")
            if (hls.exists()) return hls.absolutePath
        }

        return f.absolutePath
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        super.onMediaItemTransition(mediaItem, reason)
        _uiState.update { it.copy(currentItemTitle = buildDisplayTitle(mediaItem)) }

        appliedFirstAudioOverride = false
    }

    override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
        super.onTracksChanged(tracks)

        if (!useExo) return
        if (!forceFirstAudioTrack) return
        if (appliedFirstAudioOverride) return

        val group = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO } ?: return
        val trackGroup = group.mediaTrackGroup
        if (trackGroup.length <= 0) return

        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .setOverrideForType(TrackSelectionOverride(trackGroup, listOf(0)))
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .build()

        appliedFirstAudioOverride = true
    }

    private fun buildDisplayTitle(mediaItem: MediaItem?): String {
        val title = baseTitle
        val displayName = mediaItem?.mediaMetadata?.extras?.getString("display_name").orEmpty()
        val rawName = displayName.ifBlank {
            val url = mediaItem?.localConfiguration?.uri?.toString().orEmpty()
            url.substringAfterLast('/').substringAfterLast('\\')
        }
        val fileName = runCatching { URLDecoder.decode(rawName, "UTF-8") }.getOrDefault(rawName)

        val se = parseSeasonEpisode(fileName)
        return if (se != null && title.isNotBlank()) {
            "$title • $se"
        } else if (title.isNotBlank()) {
            title
        } else {
            fileName.ifBlank { "" }
        }
    }

    private fun parseSeasonEpisode(name: String): String? {
        // S01E02 / s1e2
        Regex("(?i)\\bS(\\d{1,2})\\s*[._-]?\\s*E(\\d{1,3})\\b").find(name)?.let { m ->
            val s = m.groupValues[1].toIntOrNull()
            val e = m.groupValues[2].toIntOrNull()
            if (s != null && e != null) return "S%02dE%02d".format(s, e)
        }
        // 1x02
        Regex("(?i)\\b(\\d{1,2})\\s*[xX]\\s*(\\d{1,3})\\b").find(name)?.let { m ->
            val s = m.groupValues[1].toIntOrNull()
            val e = m.groupValues[2].toIntOrNull()
            if (s != null && e != null) return "%dx%02d".format(s, e)
        }
        // Season 1 Episode 2
        Regex("(?i)season\\s*(\\d{1,2}).*episode\\s*(\\d{1,3})").find(name)?.let { m ->
            val s = m.groupValues[1].toIntOrNull()
            val e = m.groupValues[2].toIntOrNull()
            if (s != null && e != null) return "S%02dE%02d".format(s, e)
        }
        return null
    }

    fun updatePlaybackProgress() {
        val mediaId = player.currentMediaItem?.mediaId ?: return
        val position = player.currentPosition
        prefs.edit().putLong("pos_" + mediaId, position).apply()
        savedStateHandle["position"] = position
    }

    fun getSelectableTracks(trackType: @C.TrackType Int): List<SelectableTrack> {
        val groups = player.currentTracks.groups.filter { it.type == trackType }

        var displayIndex = 1
        val result = ArrayList<SelectableTrack>()
        for (group in groups) {
            val trackGroup = group.mediaTrackGroup
            for (trackIndex in 0 until trackGroup.length) {
                val format = runCatching { trackGroup.getFormat(trackIndex) }.getOrNull()
                val label = format?.label
                val language = format?.language
                val fallback = "Track ${displayIndex++}"
                val videoQuality =
                    if (trackType == C.TRACK_TYPE_VIDEO) {
                        val h = format?.height ?: 0
                        h.takeIf { it > 0 }?.let { "${it}p" }
                    } else {
                        null
                    }
                val displayLabel =
                    when {
                        !videoQuality.isNullOrBlank() -> videoQuality
                        !label.isNullOrBlank() -> label
                        !language.isNullOrBlank() && language != "und" -> language
                        else -> fallback
                    }

                result +=
                    SelectableTrack(
                        label = displayLabel,
                        formatId = format?.id,
                        trackGroup = trackGroup,
                        trackIndex = trackIndex,
                        isSelected = group.isTrackSelected(trackIndex),
                        isSupported = group.isTrackSupported(trackIndex),
                    )
            }
        }
        if (trackType == C.TRACK_TYPE_VIDEO) {
            fun qualityValue(label: String): Int {
                val m = Regex("(\\d{3,4})p", RegexOption.IGNORE_CASE).find(label)
                return m?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            }

            result.sortWith(
                compareByDescending<SelectableTrack> { qualityValue(it.label) }
                    .thenBy { it.label }
            )
        }
        return result
    }

    fun switchToTrack(trackType: @C.TrackType Int, index: Int) {
        if (!useExo) {
            val mpv = (player as? MPVPlayer) ?: return
            if (index == -1) {
                mpv.selectTrack(trackType, null)
                return
            }

            val track = getSelectableTracks(trackType).getOrNull(index) ?: return
            mpv.selectTrack(trackType, track.formatId)
            return
        }

        if (index == -1) {
            player.trackSelectionParameters =
                player.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(trackType)
                    .setTrackTypeDisabled(trackType, trackType == C.TRACK_TYPE_TEXT)
                    .build()
        } else {
            val track = getSelectableTracks(trackType).getOrNull(index) ?: return
            player.trackSelectionParameters =
                player.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(trackType)
                    .setOverrideForType(TrackSelectionOverride(track.trackGroup, listOf(track.trackIndex)))
                    .setTrackTypeDisabled(trackType, false)
                    .build()
        }
    }

    fun selectSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        playbackSpeed = speed
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        super.onIsPlayingChanged(isPlaying)
        eventsChannel.trySend(PlayerEvents.IsPlayingChanged(isPlaying))
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        super.onPlayWhenReadyChanged(playWhenReady, reason)
        Log.d(
            "PlayerVM",
            "PlayWhenReadyChanged: $playWhenReady reason=${playWhenReadyReasonName(reason)} state=${playbackStateName(player.playbackState)} isPlaying=${player.isPlaying} isLoading=${player.isLoading} suppression=${suppressionReasonName(player.playbackSuppressionReason)}",
        )
        eventsChannel.trySend(PlayerEvents.PlayWhenReadyChanged(playWhenReady, reason))
    }

    override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
        super.onPlaybackSuppressionReasonChanged(playbackSuppressionReason)
        Log.d(
            "PlayerVM",
            "PlaybackSuppressionReasonChanged: ${suppressionReasonName(playbackSuppressionReason)} state=${playbackStateName(player.playbackState)} playWhenReady=${player.playWhenReady} isPlaying=${player.isPlaying} isLoading=${player.isLoading}",
        )
    }

    override fun onPlaybackStateChanged(state: Int) {
        super.onPlaybackStateChanged(state)
        Log.d(
            "PlayerVM",
            "PlaybackStateChanged: ${playbackStateName(state)} playWhenReady=${player.playWhenReady} isPlaying=${player.isPlaying} isLoading=${player.isLoading} suppression=${suppressionReasonName(player.playbackSuppressionReason)}",
        )

        if (state == Player.STATE_READY) {
            _uiState.update { it.copy(fileLoaded = true) }
        }
        if (state == Player.STATE_ENDED) {
            eventsChannel.trySend(PlayerEvents.NavigateBack)
        }
    }

    override fun onIsLoadingChanged(isLoading: Boolean) {
        super.onIsLoadingChanged(isLoading)
        Log.d(
            "PlayerVM",
            "IsLoadingChanged: $isLoading state=${playbackStateName(player.playbackState)} playWhenReady=${player.playWhenReady} isPlaying=${player.isPlaying}",
        )
    }

    private fun playbackStateName(state: Int): String {
        return when (state) {
            Player.STATE_IDLE -> "IDLE"
            Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY -> "READY"
            Player.STATE_ENDED -> "ENDED"
            else -> state.toString()
        }
    }

    private fun playWhenReadyReasonName(reason: Int): String {
        return when (reason) {
            Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "USER_REQUEST"
            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "AUDIO_FOCUS_LOSS"
            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "AUDIO_BECOMING_NOISY"
            Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> "REMOTE"
            Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> "END_OF_MEDIA_ITEM"
            Player.PLAY_WHEN_READY_CHANGE_REASON_SUPPRESSED_TOO_LONG -> "SUPPRESSED_TOO_LONG"
            else -> reason.toString()
        }
    }

    private fun suppressionReasonName(reason: Int): String {
        return when (reason) {
            Player.PLAYBACK_SUPPRESSION_REASON_NONE -> "NONE"
            Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS -> "TRANSIENT_AUDIO_FOCUS_LOSS"
            Player.PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT -> "UNSUITABLE_AUDIO_OUTPUT"
            else -> reason.toString()
        }
    }

    override fun onCleared() {
        super.onCleared()
        player.removeListener(this)
        player.release()
    }
}

sealed interface PlayerEvents {
    data object NavigateBack : PlayerEvents

    data class IsPlayingChanged(val isPlaying: Boolean) : PlayerEvents

    data class PlayWhenReadyChanged(val playWhenReady: Boolean, val reason: Int) : PlayerEvents
}

data class SelectableTrack(
    val label: String,
    val formatId: String?,
    val trackGroup: TrackGroup,
    val trackIndex: Int,
    val isSelected: Boolean,
    val isSupported: Boolean,
)
