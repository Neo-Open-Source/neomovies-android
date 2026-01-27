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
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import com.neo.player.mpv.MPVPlayer
import java.net.URLDecoder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.os.Bundle
import android.util.Log

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
            MPVPlayer.Builder(getApplication())
                .setAudioAttributes(audioAttributes, true)
                .setSeekBackIncrementMs(10_000)
                .setSeekForwardIncrementMs(10_000)
                .setPauseAtEndOfMediaItems(true)
                .build()
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

            val dataSourceFactory = DefaultHttpDataSource.Factory()
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)

            @Suppress("WrongConstant")
            val renderersFactory: RenderersFactory =
                DefaultRenderersFactory(getApplication())
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

            ExoPlayer.Builder(getApplication(), renderersFactory)
                .setTrackSelector(trackSelector)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .apply {
                    setAudioAttributes(audioAttributes, true)
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

        Log.d(
            "PlayerVM",
            "initializePlayer urls=${urls.size} names=${names?.size} startIndex=$startIndex nameAtIndex=${names?.getOrNull(startIndex)}",
        )

        val mediaItems =
            urls.mapIndexed { index, url ->
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

        val currentUrl = urls.getOrNull(startIndex) ?: urls.firstOrNull().orEmpty()
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

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        super.onMediaItemTransition(mediaItem, reason)
        _uiState.update { it.copy(currentItemTitle = buildDisplayTitle(mediaItem)) }
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
                val displayLabel =
                    when {
                        !label.isNullOrBlank() -> label
                        !language.isNullOrBlank() && language != "und" -> language
                        else -> fallback
                    }

                result +=
                    SelectableTrack(
                        label = displayLabel,
                        trackGroup = trackGroup,
                        trackIndex = trackIndex,
                        isSelected = group.isTrackSelected(trackIndex),
                        isSupported = group.isTrackSupported(trackIndex),
                    )
            }
        }
        return result
    }

    fun switchToTrack(trackType: @C.TrackType Int, index: Int) {
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

    override fun onPlaybackStateChanged(state: Int) {
        super.onPlaybackStateChanged(state)
        if (state == Player.STATE_READY) {
            _uiState.update { it.copy(fileLoaded = true) }
        }
        if (state == Player.STATE_ENDED) {
            eventsChannel.trySend(PlayerEvents.NavigateBack)
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
}

data class SelectableTrack(
    val label: String,
    val trackGroup: TrackGroup,
    val trackIndex: Int,
    val isSelected: Boolean,
    val isSupported: Boolean,
)
