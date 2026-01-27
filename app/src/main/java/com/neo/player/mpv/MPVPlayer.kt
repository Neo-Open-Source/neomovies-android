package com.neo.player.mpv

import android.app.Application
import android.content.Context
import android.content.res.AssetManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.core.content.getSystemService
import androidx.media3.common.AudioAttributes
import androidx.media3.common.BasePlayer
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.FlagSet
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Player.Commands
import androidx.media3.common.Timeline
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.audio.AudioFocusRequestCompat
import androidx.media3.common.audio.AudioManagerCompat
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.Clock
import androidx.media3.common.util.ListenerSet
import androidx.media3.common.util.Size
import androidx.media3.common.util.Util
import dev.jdtech.mpv.MPVLib
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArraySet
import org.json.JSONArray
import org.json.JSONException

class MPVPlayer(
    context: Context,
    private var audioAttributes: AudioAttributes = AudioAttributes.DEFAULT,
    private var handleAudioFocus: Boolean = true,
    private var trackSelectionParameters: TrackSelectionParameters = TrackSelectionParameters.DEFAULT,
    private val seekBackIncrement: Long = C.DEFAULT_SEEK_BACK_INCREMENT_MS,
    private val seekForwardIncrement: Long = C.DEFAULT_SEEK_FORWARD_INCREMENT_MS,
    private val pauseAtEndOfMediaItems: Boolean = false,
    private val videoOutput: String = "gpu",
    private val audioOutput: String = "audiotrack",
    private val hwDec: String = "mediacodec",
) : BasePlayer(), MPVLib.EventObserver, AudioManager.OnAudioFocusChangeListener {

    private val audioManager: AudioManager by lazy { context.getSystemService()!! }
    private val handler = Handler(context.mainLooper)

    private var audioFocusCallback: () -> Unit = {}
    private var audioFocusRequest: AudioFocusRequestCompat? = null

    private val listeners: ListenerSet<Player.Listener> =
        ListenerSet(context.mainLooper, Clock.DEFAULT) { listener: Player.Listener, flags: FlagSet ->
            listener.onEvents(this, Player.Events(flags))
        }

    private val videoListeners = CopyOnWriteArraySet<Player.Listener>()

    private var internalMediaItems = mutableListOf<MediaItem>()
    private var currentMediaItemIndex: Int = 0

    @Player.State private var playbackState: Int = STATE_IDLE
    private var playWhenReadyInternal: Boolean = false

    private var isPlayerReady: Boolean = false
    private var isSeekableInternal: Boolean = false

    private var currentPositionMs: Long = C.TIME_UNSET
    private var currentDurationMs: Long = C.TIME_UNSET
    private var bufferedPositionMs: Long = C.TIME_UNSET

    private var playbackParametersInternal: PlaybackParameters = PlaybackParameters.DEFAULT
    private var currentTracks: Tracks = Tracks.EMPTY

    private var deviceMuted: Boolean = false

    private var currentSurfaceHolder: SurfaceHolder? = null
    private var pendingLoadUri: String? = null
    private var pendingSeekToSec: Long = 0L

    private val surfaceHolderCallback: SurfaceHolder.Callback =
        object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                currentSurfaceHolder = holder
                runCatching { MPVLib.attachSurface(holder.surface) }
                runCatching { MPVLib.setPropertyString("force-window", "yes") }
                runCatching { MPVLib.setPropertyString("vo", videoOutput) }

                val uri = pendingLoadUri
                if (uri != null) {
                    pendingLoadUri = null
                    MPVLib.command(arrayOf("loadfile", uri, "replace"))
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                runCatching { MPVLib.setPropertyString("android-surface-size", "${width}x$height") }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                if (currentSurfaceHolder === holder) currentSurfaceHolder = null
                runCatching { MPVLib.setPropertyString("vo", "null") }
                runCatching { MPVLib.setPropertyString("force-window", "no") }
                runCatching { MPVLib.detachSurface() }
            }
        }

    private fun hasSurface(): Boolean {
        val holder = currentSurfaceHolder
        return holder?.surface?.isValid == true
    }

    private fun startLoadIfPossible(uri: String) {
        pendingLoadUri = uri
        if (hasSurface()) {
            val toLoad = pendingLoadUri
            if (toLoad != null) {
                pendingLoadUri = null
                MPVLib.command(arrayOf("loadfile", toLoad, "replace"))
            }
        }
    }

    private constructor(builder: Builder) : this(
        context = builder.context,
        audioAttributes = builder.audioAttributes,
        handleAudioFocus = builder.handleAudioFocus,
        trackSelectionParameters = builder.trackSelectionParameters,
        seekBackIncrement = builder.seekBackIncrementMs,
        seekForwardIncrement = builder.seekForwardIncrementMs,
        pauseAtEndOfMediaItems = builder.pauseAtEndOfMediaItems,
        videoOutput = builder.videoOutput,
        audioOutput = builder.audioOutput,
        hwDec = builder.hwDec,
    )

    class Builder(val context: Context) {
        var audioAttributes: AudioAttributes = AudioAttributes.DEFAULT
            private set

        var handleAudioFocus: Boolean = true
            private set

        var trackSelectionParameters: TrackSelectionParameters = TrackSelectionParameters.DEFAULT
            private set

        var seekBackIncrementMs: Long = C.DEFAULT_SEEK_BACK_INCREMENT_MS
            private set

        var seekForwardIncrementMs: Long = C.DEFAULT_SEEK_FORWARD_INCREMENT_MS
            private set

        var pauseAtEndOfMediaItems: Boolean = false
            private set

        var videoOutput: String = "gpu"
            private set

        var audioOutput: String = "audiotrack"
            private set

        var hwDec: String = "mediacodec"
            private set

        fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) = apply {
            this.audioAttributes = audioAttributes
            this.handleAudioFocus = handleAudioFocus
        }

        fun setTrackSelectionParameters(trackSelectionParameters: TrackSelectionParameters) = apply {
            this.trackSelectionParameters = trackSelectionParameters
        }

        fun setSeekBackIncrementMs(seekBackIncrementMs: Long) = apply {
            this.seekBackIncrementMs = seekBackIncrementMs
        }

        fun setSeekForwardIncrementMs(seekForwardIncrementMs: Long) = apply {
            this.seekForwardIncrementMs = seekForwardIncrementMs
        }

        fun setPauseAtEndOfMediaItems(pauseAtEndOfMediaItems: Boolean) = apply {
            this.pauseAtEndOfMediaItems = pauseAtEndOfMediaItems
        }

        fun setVideoOutput(videoOutput: String) = apply { this.videoOutput = videoOutput }

        fun setAudioOutput(audioOutput: String) = apply { this.audioOutput = audioOutput }

        fun setHwDec(hwDec: String) = apply { this.hwDec = hwDec }

        fun build() = MPVPlayer(this)
    }

    init {
        require(context is Application)

        val mpvDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "mpv")
        if (!mpvDir.exists()) mpvDir.mkdirs()

        arrayOf("mpv.conf", "subfont.ttf").forEach { fileName ->
            val file = File(mpvDir, fileName)
            if (file.exists()) return@forEach
            runCatching {
                context.assets.open(fileName, AssetManager.ACCESS_STREAMING)
                    .copyTo(FileOutputStream(file))
            }
        }

        MPVLib.create(context)

        MPVLib.setOptionString("config", "yes")
        MPVLib.setOptionString("config-dir", mpvDir.path)
        MPVLib.setOptionString("profile", "fast")
        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.setOptionString("opengl-es", "yes")
        MPVLib.setOptionString("ao", audioOutput)

        val isEmulator =
            Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
                Build.MODEL.contains("Emulator", ignoreCase = true) ||
                Build.MODEL.contains("Android SDK", ignoreCase = true) ||
                Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
                Build.HARDWARE.contains("ranchu", ignoreCase = true)

        val effectiveHwDec =
            if (isEmulator) {
                "no"
            } else {
                if (hwDec == "mediacodec") "auto-safe" else hwDec
            }

        MPVLib.setOptionString("hwdec", effectiveHwDec)

        MPVLib.setOptionString("force-window", "no")
        MPVLib.setOptionString("keep-open", "always")
        MPVLib.setOptionString("idle", "once")
        MPVLib.setOptionString("save-position-on-quit", "no")

        MPVLib.init()
        MPVLib.addObserver(this)

        data class Property(val name: String, @param:MPVLib.Format val format: Int)
        arrayOf(
            Property("track-list", MPVLib.MPV_FORMAT_STRING),
            Property("paused-for-cache", MPVLib.MPV_FORMAT_FLAG),
            Property("eof-reached", MPVLib.MPV_FORMAT_FLAG),
            Property("seekable", MPVLib.MPV_FORMAT_FLAG),
            Property("time-pos", MPVLib.MPV_FORMAT_INT64),
            Property("duration", MPVLib.MPV_FORMAT_INT64),
            Property("demuxer-cache-time", MPVLib.MPV_FORMAT_INT64),
            Property("speed", MPVLib.MPV_FORMAT_DOUBLE),
        ).forEach { (name, format) -> MPVLib.observeProperty(name, format) }

        if (handleAudioFocus) {
            audioFocusRequest =
                AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(audioAttributes)
                    .setOnAudioFocusChangeListener(this)
                    .build()
            audioFocusRequest?.let { req ->
                val res = AudioManagerCompat.requestAudioFocus(audioManager, req)
                if (res != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    MPVLib.setPropertyBoolean("pause", true)
                }
            }
        }
    }

    override fun eventProperty(property: String) {}

    override fun eventProperty(property: String, value: String) {
        handler.post {
            when (property) {
                "track-list" -> {
                    currentTracks = getTracks(value)
                    listeners.sendEvent(EVENT_TRACKS_CHANGED) { listener ->
                        listener.onTracksChanged(currentTracks)
                    }
                }
            }
        }
    }

    override fun eventProperty(property: String, value: Boolean) {
        handler.post {
            when (property) {
                "paused-for-cache" -> {
                    if (isPlayerReady) {
                        playbackState = if (value) STATE_BUFFERING else STATE_READY
                        listeners.sendEvent(EVENT_PLAYBACK_STATE_CHANGED) { listener ->
                            listener.onPlaybackStateChanged(playbackState)
                        }
                    }
                }
                "seekable" -> isSeekableInternal = value
                "eof-reached" -> {
                    if (value && isPlayerReady) {
                        if (currentMediaItemIndex < internalMediaItems.size - 1) {
                            if (pauseAtEndOfMediaItems) {
                                setPlayWhenReady(false)
                                playbackState = STATE_READY
                            } else {
                                playAtIndex(currentMediaItemIndex + 1, 0L)
                            }
                        } else {
                            setPlayWhenReady(false)
                            playbackState = STATE_ENDED
                        }
                        listeners.sendEvent(EVENT_PLAYBACK_STATE_CHANGED) { listener ->
                            listener.onPlaybackStateChanged(playbackState)
                        }
                    }
                }
            }
        }
    }

    override fun eventProperty(property: String, value: Long) {
        handler.post {
            when (property) {
                "time-pos" -> currentPositionMs = value * C.MILLIS_PER_SECOND
                "duration" -> currentDurationMs = value * C.MILLIS_PER_SECOND
                "demuxer-cache-time" -> bufferedPositionMs = value * C.MILLIS_PER_SECOND
            }
        }
    }

    override fun eventProperty(property: String, value: Double) {
        handler.post {
            when (property) {
                "speed" -> {
                    playbackParametersInternal = playbackParametersInternal.withSpeed(value.toFloat())
                    listeners.sendEvent(EVENT_PLAYBACK_PARAMETERS_CHANGED) { listener ->
                        listener.onPlaybackParametersChanged(playbackParametersInternal)
                    }
                }
            }
        }
    }

    override fun event(@MPVLib.Event eventId: Int) {
        handler.post {
            when (eventId) {
                MPVLib.MPV_EVENT_FILE_LOADED -> {
                    val seekable = MPVLib.getPropertyBoolean("seekable") == true
                    isSeekableInternal = seekable
                    if (pendingSeekToSec != 0L && seekable) {
                        val seekTo = pendingSeekToSec
                        pendingSeekToSec = 0L
                        runCatching { MPVLib.command(arrayOf("seek", seekTo.toString(), "absolute")) }
                    }
                }
                MPVLib.MPV_EVENT_PLAYBACK_RESTART -> {
                    if (!isPlayerReady) {
                        isPlayerReady = true
                        playbackState = STATE_READY
                        listeners.sendEvent(EVENT_PLAYBACK_STATE_CHANGED) { listener ->
                            listener.onPlaybackStateChanged(playbackState)
                        }
                        for (videoListener in videoListeners) {
                            videoListener.onRenderedFirstFrame()
                        }
                    } else {
                        playbackState = STATE_READY
                        listeners.sendEvent(EVENT_PLAYBACK_STATE_CHANGED) { listener ->
                            listener.onPlaybackStateChanged(playbackState)
                        }
                    }
                }
            }
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                val old = audioFocusCallback
                val wasPlaying = isPlaying
                MPVLib.setPropertyBoolean("pause", true)
                setPlayWhenReady(false)
                audioFocusCallback = {
                    old()
                    if (wasPlaying) MPVLib.setPropertyBoolean("pause", false)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                MPVLib.command(arrayOf("multiply", "volume", "$AUDIO_FOCUS_DUCKING"))
                audioFocusCallback = {
                    MPVLib.command(arrayOf("multiply", "volume", "${1f / AUDIO_FOCUS_DUCKING}"))
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                audioFocusCallback()
                audioFocusCallback = {}
            }
        }
    }

    override fun getApplicationLooper(): Looper = handler.looper

    override fun addListener(listener: Player.Listener) {
        listeners.add(listener)
        videoListeners.add(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)
        videoListeners.remove(listener)
    }

    override fun getAvailableCommands(): Commands {
        return Commands.Builder()
            .addAll(permanentAvailableCommands)
            .addIf(COMMAND_SEEK_TO_DEFAULT_POSITION, !isPlayingAd)
            .addIf(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, isCurrentMediaItemSeekable && !isPlayingAd)
            .addIf(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, hasPreviousMediaItem() && !isPlayingAd)
            .addIf(
                COMMAND_SEEK_TO_PREVIOUS,
                !currentTimeline.isEmpty &&
                    (hasPreviousMediaItem() || !isCurrentMediaItemLive || isCurrentMediaItemSeekable) &&
                    !isPlayingAd,
            )
            .addIf(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, hasNextMediaItem() && !isPlayingAd)
            .addIf(
                COMMAND_SEEK_TO_NEXT,
                !currentTimeline.isEmpty &&
                    (hasNextMediaItem() || (isCurrentMediaItemLive && isCurrentMediaItemDynamic)) &&
                    !isPlayingAd,
            )
            .addIf(COMMAND_SEEK_TO_MEDIA_ITEM, !isPlayingAd)
            .addIf(COMMAND_SEEK_BACK, isCurrentMediaItemSeekable && !isPlayingAd)
            .addIf(COMMAND_SEEK_FORWARD, isCurrentMediaItemSeekable && !isPlayingAd)
            .build()
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
        internalMediaItems = mediaItems
        if (resetPosition) {
            currentMediaItemIndex = 0
            pendingSeekToSec = 0L
        }
        listeners.sendEvent(EVENT_TIMELINE_CHANGED) { listener ->
            listener.onTimelineChanged(timeline, TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
        }
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, startWindowIndex: Int, startPositionMs: Long) {
        internalMediaItems = mediaItems
        currentMediaItemIndex = startWindowIndex
        pendingSeekToSec = startPositionMs / 1000
        listeners.sendEvent(EVENT_TIMELINE_CHANGED) { listener ->
            listener.onTimelineChanged(timeline, TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
        }
    }

    override fun addMediaItems(index: Int, mediaItems: MutableList<MediaItem>) {
        internalMediaItems.addAll(index, mediaItems)
        listeners.sendEvent(EVENT_TIMELINE_CHANGED) { listener ->
            listener.onTimelineChanged(timeline, TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
        }
    }

    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {
        throw UnsupportedOperationException()
    }

    override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: MutableList<MediaItem>) {
        throw UnsupportedOperationException()
    }

    override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
        throw UnsupportedOperationException()
    }

    override fun prepare() {
        if (internalMediaItems.isNotEmpty()) {
            playAtIndex(currentMediaItemIndex, pendingSeekToSec)
        }
    }

    private fun playAtIndex(index: Int, seekToSec: Long) {
        val uri = internalMediaItems.getOrNull(index)?.localConfiguration?.uri ?: return
        currentMediaItemIndex = index
        pendingSeekToSec = seekToSec

        listeners.sendEvent(EVENT_MEDIA_ITEM_TRANSITION) { listener ->
            listener.onMediaItemTransition(currentMediaItem, MEDIA_ITEM_TRANSITION_REASON_SEEK)
        }

        playbackState = STATE_BUFFERING
        listeners.sendEvent(EVENT_PLAYBACK_STATE_CHANGED) { listener ->
            listener.onPlaybackStateChanged(playbackState)
        }

        startLoadIfPossible(uri.toString())
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReadyInternal == playWhenReady) return
        playWhenReadyInternal = playWhenReady
        MPVLib.setPropertyBoolean("pause", !playWhenReady)
        listeners.sendEvent(EVENT_PLAY_WHEN_READY_CHANGED) { listener ->
            listener.onPlayWhenReadyChanged(playWhenReady, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        }
        listeners.sendEvent(EVENT_IS_PLAYING_CHANGED) { listener ->
            listener.onIsPlayingChanged(isPlaying)
        }
    }

    override fun getPlayWhenReady(): Boolean = playWhenReadyInternal

    override fun getPlaybackState(): Int = playbackState

    override fun seekTo(mediaItemIndex: Int, positionMs: Long, @Player.Command seekCommand: Int, isRepeatingCurrentItem: Boolean) {
        if (mediaItemIndex == currentMediaItemIndex) {
            val seekTo = if (positionMs != C.TIME_UNSET) positionMs / 1000 else 0L
            if (isPlayerReady && isCurrentMediaItemSeekable) {
                MPVLib.command(arrayOf("seek", seekTo.toString(), "absolute"))
            } else {
                pendingSeekToSec = seekTo
            }
        } else {
            playAtIndex(mediaItemIndex, if (positionMs != C.TIME_UNSET) positionMs / 1000 else 0L)
            setPlayWhenReady(true)
        }
    }

    override fun stop() {
        MPVLib.command(arrayOf("stop"))
        playbackState = STATE_IDLE
        isPlayerReady = false
        listeners.sendEvent(EVENT_PLAYBACK_STATE_CHANGED) { listener ->
            listener.onPlaybackStateChanged(playbackState)
        }
    }

    override fun release() {
        audioFocusRequest?.let { AudioManagerCompat.abandonAudioFocusRequest(audioManager, it) }
        audioFocusRequest = null
        MPVLib.removeObserver(this)
        MPVLib.destroy()
    }

    override fun getSeekBackIncrement(): Long = seekBackIncrement

    override fun getSeekForwardIncrement(): Long = seekForwardIncrement

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        if (playbackParametersInternal.speed != playbackParameters.speed) {
            MPVLib.setPropertyDouble("speed", playbackParameters.speed.toDouble())
        }
    }

    override fun getPlaybackParameters(): PlaybackParameters = playbackParametersInternal

    override fun getCurrentTracks(): Tracks = currentTracks

    override fun getTrackSelectionParameters(): TrackSelectionParameters = trackSelectionParameters

    override fun setTrackSelectionParameters(parameters: TrackSelectionParameters) {
        trackSelectionParameters = parameters
    }

    override fun getMediaMetadata(): MediaMetadata = MediaMetadata.EMPTY

    override fun getPlaylistMetadata(): MediaMetadata = MediaMetadata.EMPTY

    override fun setPlaylistMetadata(mediaMetadata: MediaMetadata) {
        throw UnsupportedOperationException()
    }

    override fun getCurrentTimeline(): Timeline = timeline

    override fun getCurrentPeriodIndex(): Int = currentMediaItemIndex

    override fun getCurrentMediaItemIndex(): Int = currentMediaItemIndex

    override fun getDuration(): Long = currentDurationMs

    override fun getCurrentPosition(): Long = currentPositionMs

    override fun getBufferedPosition(): Long = if (bufferedPositionMs != C.TIME_UNSET) bufferedPositionMs else currentPosition

    override fun getTotalBufferedDuration(): Long = bufferedPosition

    override fun isPlayingAd(): Boolean = false

    override fun getCurrentAdGroupIndex(): Int = C.INDEX_UNSET

    override fun getCurrentAdIndexInAdGroup(): Int = C.INDEX_UNSET

    override fun getContentPosition(): Long = currentPosition

    override fun getContentBufferedPosition(): Long = bufferedPosition

    override fun getAudioAttributes(): AudioAttributes = audioAttributes

    override fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) {
        this.audioAttributes = audioAttributes
        this.handleAudioFocus = handleAudioFocus
    }

    override fun setVolume(audioVolume: Float) {
        MPVLib.setPropertyInt("volume", (audioVolume * 100).toInt())
    }

    override fun getVolume(): Float = (MPVLib.getPropertyInt("volume") ?: 100) / 100F

    override fun clearVideoSurface() {
        runCatching { MPVLib.detachSurface() }
    }

    override fun clearVideoSurface(surface: Surface?) {
        runCatching { MPVLib.detachSurface() }
    }

    override fun setVideoSurface(surface: Surface?) {
        if (surface == null) {
            runCatching { MPVLib.detachSurface() }
        } else {
            runCatching { MPVLib.attachSurface(surface) }
        }
    }

    override fun setVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {
        setVideoSurface(surfaceHolder?.surface)
    }

    override fun clearVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {
        runCatching { MPVLib.detachSurface() }
    }

    override fun setVideoSurfaceView(surfaceView: SurfaceView?) {
        val holder = surfaceView?.holder
        currentSurfaceHolder = holder
        holder?.addCallback(surfaceHolderCallback)
        if (holder?.surface?.isValid == true) {
            surfaceHolderCallback.surfaceCreated(holder)
        }
    }

    override fun clearVideoSurfaceView(surfaceView: SurfaceView?) {
        surfaceView?.holder?.removeCallback(surfaceHolderCallback)
        if (currentSurfaceHolder === surfaceView?.holder) currentSurfaceHolder = null
        runCatching { MPVLib.detachSurface() }
    }

    override fun setVideoTextureView(textureView: TextureView?) {
        setVideoSurface(textureView?.surfaceTexture?.let { Surface(it) })
    }

    override fun clearVideoTextureView(textureView: TextureView?) {
        runCatching { MPVLib.detachSurface() }
    }

    override fun getVideoSize(): VideoSize = VideoSize.UNKNOWN

    override fun getSurfaceSize(): Size = Size.UNKNOWN

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        throw UnsupportedOperationException()
    }

    override fun getShuffleModeEnabled(): Boolean = false

    override fun setRepeatMode(repeatMode: Int) {
        throw UnsupportedOperationException()
    }

    override fun getRepeatMode(): Int = REPEAT_MODE_OFF

    @Suppress("OVERRIDE_DEPRECATION")
    override fun setDeviceVolume(deviceVolume: Int) {}

    @Suppress("OVERRIDE_DEPRECATION")
    override fun setDeviceVolume(deviceVolume: Int, flags: Int) {}

    @Suppress("OVERRIDE_DEPRECATION")
    override fun increaseDeviceVolume() {}

    @Suppress("OVERRIDE_DEPRECATION")
    override fun increaseDeviceVolume(flags: Int) {}

    @Suppress("OVERRIDE_DEPRECATION")
    override fun decreaseDeviceVolume() {}

    @Suppress("OVERRIDE_DEPRECATION")
    override fun decreaseDeviceVolume(flags: Int) {}

    @Suppress("OVERRIDE_DEPRECATION")
    override fun setDeviceMuted(muted: Boolean) {
        deviceMuted = muted
        runCatching { MPVLib.setPropertyBoolean("mute", muted) }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun setDeviceMuted(muted: Boolean, flags: Int) {
        setDeviceMuted(muted)
    }

    override fun getDeviceVolume(): Int = C.VOLUME_FLAG_PLAY_SOUND

    override fun isDeviceMuted(): Boolean = deviceMuted

    override fun mute() {
        setDeviceMuted(true)
    }

    override fun unmute() {
        setDeviceMuted(false)
    }

    override fun isLoading(): Boolean = playbackState == STATE_BUFFERING

    override fun getAudioSessionId(): Int = C.AUDIO_SESSION_ID_UNSET

    override fun getDeviceInfo(): DeviceInfo = DeviceInfo.UNKNOWN

    override fun getMaxSeekToPreviousPosition(): Long = C.TIME_UNSET

    override fun getCurrentCues(): CueGroup = CueGroup.EMPTY_TIME_ZERO

    override fun getPlayerError(): PlaybackException? = null

    override fun getPlaybackSuppressionReason(): Int = PLAYBACK_SUPPRESSION_REASON_NONE

    private val timeline: Timeline =
        object : Timeline() {
            override fun getWindowCount(): Int = internalMediaItems.size

            override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
                val item = internalMediaItems.getOrNull(windowIndex) ?: MediaItem.Builder().build()
                return window.set(
                    windowIndex,
                    item,
                    null,
                    C.TIME_UNSET,
                    C.TIME_UNSET,
                    C.TIME_UNSET,
                    isSeekableInternal,
                    false,
                    item.liveConfiguration,
                    0,
                    Util.msToUs(currentDurationMs),
                    windowIndex,
                    windowIndex,
                    0,
                )
            }

            override fun getPeriodCount(): Int = internalMediaItems.size

            override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
                return period.set(
                    periodIndex,
                    periodIndex,
                    periodIndex,
                    Util.msToUs(currentDurationMs),
                    0,
                )
            }

            override fun getIndexOfPeriod(uid: Any): Int = uid as Int

            override fun getUidOfPeriod(periodIndex: Int): Any = periodIndex
        }

    private fun getTracks(tracks: String): Tracks {
        val parsedTracks = mutableListOf<Tracks.Group>()
        try {
            val jsonArray = JSONArray(tracks)
            for (i in 0 until jsonArray.length()) {
                val jsonTrack = jsonArray.getJSONObject(i)
                val trackType =
                    MPVTrackType.entries.firstOrNull { it.type == jsonTrack.optString("type") }
                        ?: continue

                val codec = jsonTrack.optString("codec")
                val mimeType =
                    when (trackType) {
                        MPVTrackType.VIDEO ->
                            when (codec) {
                                "h264" -> MimeTypes.VIDEO_H264
                                "hevc" -> MimeTypes.VIDEO_H265
                                "mpeg2video" -> MimeTypes.VIDEO_MPEG2
                                "vp9" -> MimeTypes.VIDEO_VP9
                                "av1" -> MimeTypes.VIDEO_AV1
                                else -> null
                            }
                        MPVTrackType.AUDIO ->
                            when (codec) {
                                "aac" -> MimeTypes.AUDIO_AAC
                                "mp3" -> MimeTypes.AUDIO_MPEG
                                "ac3" -> MimeTypes.AUDIO_AC3
                                "eac3" -> MimeTypes.AUDIO_E_AC3
                                "dts" -> MimeTypes.AUDIO_DTS
                                "flac" -> MimeTypes.AUDIO_FLAC
                                "opus" -> MimeTypes.AUDIO_OPUS
                                "vorbis" -> MimeTypes.AUDIO_VORBIS
                                else -> null
                            }
                        MPVTrackType.SUBTITLE ->
                            when (codec) {
                                "subrip", "srt" -> MimeTypes.APPLICATION_SUBRIP
                                "ass", "ssa" -> MimeTypes.TEXT_SSA
                                "webvtt" -> MimeTypes.TEXT_VTT
                                else -> MimeTypes.TEXT_UNKNOWN
                            }
                    }

                val track =
                    when (trackType) {
                        MPVTrackType.VIDEO -> {
                            Format.Builder()
                                .setId(jsonTrack.optString("id"))
                                .setLabel(jsonTrack.optString("title"))
                                .setCodecs(codec)
                                .setWidth(jsonTrack.optInt("demux-w"))
                                .setHeight(jsonTrack.optInt("demux-h"))
                                .setFrameRate(jsonTrack.optDouble("fps").toFloat())
                                .setAverageBitrate(jsonTrack.optInt("demux-bitrate"))
                                .setSampleMimeType(mimeType)
                                .setContainerMimeType("video")
                                .build()
                        }
                        MPVTrackType.AUDIO -> {
                            Format.Builder()
                                .setId(jsonTrack.optString("id"))
                                .setLabel(jsonTrack.optString("title"))
                                .setLanguage(jsonTrack.optString("lang"))
                                .setCodecs(codec)
                                .setAverageBitrate(jsonTrack.optInt("demux-bitrate"))
                                .setSampleMimeType(mimeType)
                                .setContainerMimeType("audio")
                                .build()
                        }
                        MPVTrackType.SUBTITLE -> {
                            Format.Builder()
                                .setId(jsonTrack.optString("id"))
                                .setLabel(jsonTrack.optString("title"))
                                .setLanguage(jsonTrack.optString("lang"))
                                .setCodecs(codec)
                                .setSampleMimeType(mimeType)
                                .setContainerMimeType("text")
                                .build()
                        }
                    }

                val trackGroup = TrackGroup(track)
                parsedTracks.add(
                    Tracks.Group(
                        trackGroup,
                        false,
                        IntArray(trackGroup.length) { C.FORMAT_HANDLED },
                        BooleanArray(trackGroup.length) { idx -> idx == 0 && jsonTrack.optBoolean("selected") },
                    )
                )
            }
        } catch (_: JSONException) {}
        return if (parsedTracks.isEmpty()) Tracks.EMPTY else Tracks(parsedTracks)
    }

    companion object {
        private const val AUDIO_FOCUS_DUCKING = 0.1f

        private val permanentAvailableCommands: Commands =
            Commands.Builder()
                .addAll(
                    COMMAND_PLAY_PAUSE,
                    COMMAND_SET_SPEED_AND_PITCH,
                    COMMAND_GET_CURRENT_MEDIA_ITEM,
                    COMMAND_GET_METADATA,
                    COMMAND_CHANGE_MEDIA_ITEMS,
                    COMMAND_SET_VIDEO_SURFACE,
                    COMMAND_GET_TRACKS,
                    COMMAND_SET_TRACK_SELECTION_PARAMETERS,
                )
                .build()

        private fun Commands.Builder.addIf(@Player.Command command: Int, condition: Boolean): Commands.Builder {
            if (condition) add(command)
            return this
        }
    }
}
