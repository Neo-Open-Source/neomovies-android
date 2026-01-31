package com.neo.player

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import com.neo.neomovies.ui.settings.PlayerEngineManager
import com.neo.neomovies.ui.settings.PlayerEngineMode
import com.neo.neomovies.ui.settings.SourceManager
import com.neo.neomovies.ui.settings.SourceMode
import com.neo.player.mpv.MPVPlayer
import java.io.File
import java.net.URLDecoder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

class PlayerViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application), Player.Listener {

    var player: Player
        private set

    private var useExo: Boolean = false
    var playbackSpeed: Float = 1f
    var isInPictureInPictureMode: Boolean = false
    private var baseTitle: String = ""

    private val useCollapsHeaders: Boolean by lazy {
        savedStateHandle.get<Boolean>(PlayerActivity.EXTRA_USE_COLLAPS_HEADERS)
            ?: (SourceManager.getMode(getApplication()) == SourceMode.COLLAPS)
    }
    
    private val forceFirstAudioTrack: Boolean by lazy { useCollapsHeaders }
    private var appliedFirstAudioOverride: Boolean = false

    private val _uiState = MutableStateFlow(UiState(currentItemTitle = "", fileLoaded = false))
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

    // Shared AudioAttributes to avoid duplication
    private val audioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .setUsage(C.USAGE_MEDIA)
        .build()

    init {
        useExo = savedStateHandle.get<Boolean>(PlayerActivity.EXTRA_USE_EXO)
            ?: (PlayerEngineManager.getMode(getApplication()) == PlayerEngineMode.EXO)
        
        Log.d("PlayerVM", "init useExo=$useExo")
        player = createPlayer(useExo)
        player.addListener(this)
    }

    fun setEngine(useExo: Boolean) {
        if (this.useExo == useExo) return
        this.useExo = useExo

        player.removeListener(this)
        player.release()

        player = createPlayer(useExo)
        player.addListener(this)
        
        // Note: You might need to re-initialize the playlist here 
        // if engine is switched during playback.
    }

    private fun createPlayer(useExo: Boolean): Player {
        return if (!useExo) {
            val builder = MPVPlayer.Builder(getApplication())
                .setAudioAttributes(audioAttributes, true)
                .setSeekBackIncrementMs(10_000)
                .setSeekForwardIncrementMs(10_000)
                .setPauseAtEndOfMediaItems(true)

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
            val trackSelector = DefaultTrackSelector(getApplication()).apply {
                parameters = buildUponParameters()
                    .setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)
                    .build()
            }

            val extractorsFactory = DefaultExtractorsFactory()
                .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS)
                .setTsExtractorTimestampSearchBytes(1500 * TsExtractor.TS_PACKET_SIZE)

            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
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

            val extensionMode = if (isEmulator()) {
                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
            } else {
                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            }

            val renderersFactory = DefaultRenderersFactory(getApplication())
                .setExtensionRendererMode(extensionMode)
                .setEnableDecoderFallback(true)

            ExoPlayer.Builder(getApplication(), renderersFactory)
                .setTrackSelector(trackSelector)
                .setMediaSourceFactory(mediaSourceFactory)
                .build().apply {
                    setAudioAttributes(this@PlayerViewModel.audioAttributes, true)
                    addAnalyticsListener(createAnalyticsListener())
                }
        }
    }

    private fun isEmulator(): Boolean {
        return Build.FINGERPRINT.contains("generic") ||
                Build.MODEL.contains("emulator") ||
                Build.MODEL.contains("sdk_gphone") ||
                Build.MANUFACTURER.contains("genymotion")
    }

    private fun createAnalyticsListener() = object : AnalyticsListener {
        override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
            Log.d("PlayerVM", "VideoDecoder: $decoderName")
        }
        override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {
            Log.e("PlayerVM", "Error: ${error.errorCodeName}", error)
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

        val resolvedUrls = if (!useExo) urls.map { resolveMpvUri(it) } else urls

        val mediaItems = resolvedUrls.mapIndexed { index, url ->
            val displayName = names?.getOrNull(index).orEmpty()
            val extras = Bundle().apply { putString("display_name", displayName) }
            MediaItem.Builder()
                .setMediaId(url)
                .setUri(url)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(baseTitle)
                        .setExtras(extras)
                        .build()
                )
                .build()
        }

        val currentUrl = resolvedUrls.getOrNull(startIndex) ?: resolvedUrls.firstOrNull().orEmpty()
        val startPosition = if (startFromBeginning) 0L else prefs.getLong("pos_$currentUrl", 0L)

        player.setMediaItems(mediaItems, startIndex, startPosition)
        player.prepare()
        player.playWhenReady = true
    }

    private fun resolveMpvUri(url: String): String {
        val authority = getApplication<Application>().packageName + ".fileprovider"
        val prefix = "content://$authority/cache/collaps/"
        if (!url.startsWith(prefix)) return url

        val fileName = url.removePrefix(prefix).substringBefore('?').substringBefore('#')
        val f = File(getApplication<Application>().cacheDir, "collaps/$fileName")
        
        if (!f.exists()) return url

        if (f.extension.equals("mpd", ignoreCase = true)) {
            val hls = File(f.parentFile, f.nameWithoutExtension + ".m3u8")
            if (hls.exists()) return hls.absolutePath
        }
        return f.absolutePath
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        _uiState.update { it.copy(currentItemTitle = buildDisplayTitle(mediaItem)) }
        appliedFirstAudioOverride = false
    }

    override fun onTracksChanged(tracks: Tracks) {
        if (!useExo || !forceFirstAudioTrack || appliedFirstAudioOverride) return

        val group = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO } ?: return
        val trackGroup = group.mediaTrackGroup
        
        if (trackGroup.length > 0) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .setOverrideForType(TrackSelectionOverride(trackGroup, listOf(0)))
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .build()
            appliedFirstAudioOverride = true
        }
    }

    private fun buildDisplayTitle(mediaItem: MediaItem?): String {
        val displayName = mediaItem?.mediaMetadata?.extras?.getString("display_name").orEmpty()
        val rawName = displayName.ifBlank {
            val url = mediaItem?.localConfiguration?.uri?.toString().orEmpty()
            url.substringAfterLast('/').substringAfterLast('\\')
        }
        val fileName = runCatching { URLDecoder.decode(rawName, "UTF-8") }.getOrDefault(rawName)
        val se = parseSeasonEpisode(fileName)

        return when {
            se != null && baseTitle.isNotBlank() -> "$baseTitle • $se"
            baseTitle.isNotBlank() -> baseTitle
            else -> fileName
        }
    }

    private fun parseSeasonEpisode(name: String): String? {
        val patterns = listOf(
            "(?i)\\bS(\\d{1,2})\\s*[._-]?\\s*E(\\d{1,3})\\b",
            "(?i)\\b(\\d{1,2})\\s*[xX]\\s*(\\d{1,3})\\b",
            "(?i)season\\s*(\\d{1,2}).*episode\\s*(\\d{1,3})"
        )
        
        for (pattern in patterns) {
            Regex(pattern).find(name)?.let { m ->
                val s = m.groupValues[1].toIntOrNull()
                val e = m.groupValues[2].toIntOrNull()
                if (s != null && e != null) {
                    return if (pattern.contains("x")) "%dx%02d".format(s, e) else "S%02dE%02d".format(s, e)
                }
            }
        }
        return null
    }

    fun updatePlaybackProgress() {
        val mediaId = player.currentMediaItem?.mediaId ?: return
        val position = player.currentPosition
        prefs.edit().putLong("pos_$mediaId", position).apply()
        savedStateHandle["position"] = position
    }

    fun getSelectableTracks(trackType: @C.TrackType Int): List<SelectableTrack> {
        val groups = player.currentTracks.groups.filter { it.type == trackType }
        val result = ArrayList<SelectableTrack>()
        var displayIndex = 1

        for (group in groups) {
            val trackGroup = group.mediaTrackGroup
            for (i in 0 until trackGroup.length) {
                val format = trackGroup.getFormat(i)
                val label = format.label
                val language = format.language
                
                val displayLabel = when {
                    trackType == C.TRACK_TYPE_VIDEO && format.height > 0 -> "${format.height}p"
                    !label.isNullOrBlank() -> label
                    !language.isNullOrBlank() && language != "und" -> language
                    else -> "Track ${displayIndex++}"
                }

                result += SelectableTrack(
                    label = displayLabel,
                    formatId = format.id,
                    trackGroup = trackGroup,
                    trackIndex = i,
                    isSelected = group.isTrackSelected(i),
                    isSupported = group.isTrackSupported(i),
                    height = format.height
                )
            }
        }

        if (trackType == C.TRACK_TYPE_VIDEO) {
            result.sortByDescending { it.height }
        }
        return result
    }

    fun switchToTrack(trackType: @C.TrackType Int, index: Int) {
        if (!useExo) {
            val mpv = (player as? MPVPlayer) ?: return
            val track = if (index == -1) null else getSelectableTracks(trackType).getOrNull(index)
            mpv.selectTrack(trackType, track?.formatId)
            return
        }

        val builder = player.trackSelectionParameters.buildUpon()
        if (index == -1) {
            builder.clearOverridesOfType(trackType)
                   .setTrackTypeDisabled(trackType, trackType == C.TRACK_TYPE_TEXT)
        } else {
            val track = getSelectableTracks(trackType).getOrNull(index) ?: return
            builder.clearOverridesOfType(trackType)
                   .setOverrideForType(TrackSelectionOverride(track.trackGroup, listOf(track.trackIndex)))
                   .setTrackTypeDisabled(trackType, false)
        }
        player.trackSelectionParameters = builder.build()
    }

    fun selectSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        playbackSpeed = speed
    }

    override fun onPlaybackStateChanged(state: Int) {
        if (state == Player.STATE_READY) _uiState.update { it.copy(fileLoaded = true) }
        if (state == Player.STATE_ENDED) eventsChannel.trySend(PlayerEvents.NavigateBack)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        eventsChannel.trySend(PlayerEvents.IsPlayingChanged(isPlaying))
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
    val height: Int = 0 // Added for easier sorting
)