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
import android.util.Log
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
import org.json.JSONObject

class MPVPlayerBackup(
    context: Context,
    private var audioAttributes: AudioAttributes = AudioAttributes.DEFAULT,
    private var handleAudioFocus: Boolean = true,
    private var trackSelectionParameters: TrackSelectionParameters =
        TrackSelectionParameters.DEFAULT,
    private val seekBackIncrement: Long = C.DEFAULT_SEEK_BACK_INCREMENT_MS,
    private val seekForwardIncrement: Long = C.DEFAULT_SEEK_FORWARD_INCREMENT_MS,
    private val pauseAtEndOfMediaItems: Boolean = false,
    private val videoOutput: String = "gpu",
    private val audioOutput: String = "audiotrack",
    private val hwDec: String = "mediacodec",
) : BasePlayer(), MPVLib.EventObserver, AudioManager.OnAudioFocusChangeListener {

    private val audioManager: AudioManager by lazy { context.getSystemService()!! }
    private var audioFocusCallback: () -> Unit = {}
    private lateinit var audioFocusRequest: AudioFocusRequestCompat
    private val handler = Handler(context.mainLooper)

    private var currentSurfaceHolder: SurfaceHolder? = null

    private var pendingLoadUri: String? = null
    private var pendingLoadMode: String = "replace"

    private val surfaceHolderCallback: SurfaceHolder.Callback =
        object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                currentSurfaceHolder = holder
                runCatching { MPVLib.attachSurface(holder.surface) }
                runCatching { MPVLib.setOptionString("force-window", "yes") }
                runCatching { MPVLib.setOptionString("vo", videoOutput) }

                val uri = pendingLoadUri
                if (uri != null) {
                    pendingLoadUri = null
                    MPVLib.command(arrayOf("loadfile", uri, pendingLoadMode))
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                runCatching { MPVLib.setPropertyString("android-surface-size", "${width}x$height") }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                if (currentSurfaceHolder === holder) currentSurfaceHolder = null
                runCatching { MPVLib.setOptionString("vo", "null") }
                runCatching { MPVLib.setOptionString("force-window", "no") }
                runCatching { MPVLib.detachSurface() }
            }
        }

    private fun ensureSurfaceAttached() {
        val holder = currentSurfaceHolder ?: return
        if (!holder.surface.isValid) return
        runCatching { MPVLib.attachSurface(holder.surface) }
        runCatching { MPVLib.setOptionString("force-window", "yes") }
        runCatching { MPVLib.setOptionString("vo", videoOutput) }
        val w = holder.surfaceFrame?.width() ?: 0
        val h = holder.surfaceFrame?.height() ?: 0
        if (w > 0 && h > 0) {
            runCatching { MPVLib.setPropertyString("android-surface-size", "${w}x$h") }
        }
    }

    private constructor(
        builder: Builder
    ) : this(
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

        fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) =
            apply {
                this.audioAttributes = audioAttributes
                this.handleAudioFocus = handleAudioFocus
            }

        fun setTrackSelectionParameters(trackSelectionParameters: TrackSelectionParameters) =
            apply {
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

        fun build() = MPVPlayerBackup(this)
    }

    init {
        require(context is Application)
        val mpvDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "mpv")
        Log.d(TAG, "mpv config dir: $mpvDir")
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
        MPVLib.setOptionString("vo", videoOutput)
        MPVLib.setOptionString("ao", audioOutput)
        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.setOptionString("opengl-es", "yes")

        val isEmulator =
            Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
                Build.MODEL.contains("Emulator", ignoreCase = true) ||
                Build.MODEL.contains("Android SDK", ignoreCase = true) ||
                Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
                Build.HARDWARE.contains("ranchu", ignoreCase = true)

        val effectiveHwDec =
            if (isEmulator) {
                // MediaCodec on emulator/goldfish is unstable and can lead to audio-only playback.
                "no"
            } else {
                // Safer default than forcing mediacodec.
                if (hwDec == "mediacodec") "auto-safe" else hwDec
            }

        MPVLib.setOptionString("hwdec", effectiveHwDec)
        MPVLib.setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")

        MPVLib.setOptionString("tls-verify", "no")

        MPVLib.setOptionString("cache", "yes")
        MPVLib.setOptionString("cache-pause-initial", "yes")
        MPVLib.setOptionString("demuxer-max-bytes", "32MiB")
        MPVLib.setOptionString("demuxer-max-back-bytes", "32MiB")

        MPVLib.setOptionString("sub-scale-with-window", "yes")
        MPVLib.setOptionString("sub-use-margins", "no")

        trackSelectionParameters.preferredAudioLanguages.firstOrNull()?.let {
            MPVLib.setOptionString("alang", it.split("-").last())
        }
        trackSelectionParameters.preferredTextLanguages.firstOrNull()?.let {
            MPVLib.setOptionString("slang", it.split("-").last())
        }

        MPVLib.setOptionString("force-window", "no")
        MPVLib.setOptionString("keep-open", "always")
        MPVLib.setOptionString("idle", "once")
        MPVLib.setOptionString("save-position-on-quit", "no")
        MPVLib.setOptionString("sub-font-provider", "none")
        MPVLib.setOptionString("ytdl", "no")

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
            Property("playlist-count", MPVLib.MPV_FORMAT_INT64),
            Property("playlist-current-pos", MPVLib.MPV_FORMAT_INT64),
        ).forEach { (name, format) -> MPVLib.observeProperty(name, format) }

        if (handleAudioFocus) {
            audioFocusRequest =
                AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(audioAttributes)
                    .setOnAudioFocusChangeListener(this)
                    .build()
            val res = AudioManagerCompat.requestAudioFocus(audioManager, audioFocusRequest)
            if (res != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                MPVLib.setPropertyBoolean("pause", true)
            }
        }
    }

    private val listeners: ListenerSet<Player.Listener> =
        ListenerSet(context.mainLooper, Clock.DEFAULT) { listener: Player.Listener, flags: FlagSet ->
            listener.onEvents(this, Player.Events(flags))
        }
    private val videoListeners = CopyOnWriteArraySet<Player.Listener>()

    private var internalMediaItems = mutableListOf<MediaItem>()

    @Player.State private var playbackState: Int = STATE_IDLE
    private var currentPlayWhenReady: Boolean = false

    private var deviceMuted: Boolean = false

    @Player.RepeatMode private val repeatMode: Int = REPEAT_MODE_OFF
    private var currentTracks: Tracks = Tracks.EMPTY
    private var playbackParameters: PlaybackParameters = PlaybackParameters.DEFAULT

    private var isPlayerReady: Boolean = false
    private var isSeekable: Boolean = false
    private var currentMediaItemIndex: Int = 0
    private var currentPositionMs: Long? = null
    private var currentDurationMs: Long? = null
    private var currentCacheDurationMs: Long? = null
    private var initialCommands = mutableListOf<Array<String>>()
    private var initialIndex: Int = 0
    private var initialSeekTo: Long = 0L
    private var oldMediaItem: MediaItem? = null

    private var pendingVideoReinitAfterFileLoaded: Boolean = false

    private var waitingForVideoReconfig: Boolean = false
    private var loadToken: Long = 0L

    private var pendingSeekToSec: Long = 0L

    override fun eventProperty(property: String) {}

    override fun eventProperty(property: String, value: String) {
        handler.post {
            when (property) {
                "track-list" -> {
                    val newTracks = getTracks(value)
                    currentTracks = newTracks
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
                "eof-reached" -> {
                    if (value && isPlayerReady) {
                        if (currentMediaItemIndex < (internalMediaItems.size - 1)) {
                            if (pauseAtEndOfMediaItems) {
                                setPlayerStateAndNotifyIfChanged(
                                    playWhenReady = false,
                                    playWhenReadyChangeReason =
                                        PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM,
                                    playbackState = STATE_READY,
                                )
                            } else {
                                prepareMediaItem(currentMediaItemIndex + 1)
                                play()
                            }
                        } else {
                            setPlayerStateAndNotifyIfChanged(
                                playWhenReady = false,
                                playWhenReadyChangeReason =
                                    PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM,
                                playbackState = STATE_ENDED,
                            )
                            resetInternalState()
                        }
                    }
                }
                "paused-for-cache" -> {
                    if (isPlayerReady) {
                        if (value) {
                            setPlayerStateAndNotifyIfChanged(playbackState = STATE_BUFFERING)
                        } else {
                            setPlayerStateAndNotifyIfChanged(playbackState = STATE_READY)
                        }
                    }
                }
                "seekable" -> {
                    if (isSeekable != value) {
                        isSeekable = value
                    }
                }
            }
        }
    }

    override fun eventProperty(property: String, value: Long) {
        handler.post {
            when (property) {
                "time-pos" -> currentPositionMs = value * C.MILLIS_PER_SECOND
                "duration" -> {
                    val newDuration = value * C.MILLIS_PER_SECOND
                    if (currentDurationMs != newDuration) {
                        currentDurationMs = newDuration
                    }
                }
                "demuxer-cache-time" -> currentCacheDurationMs = value * C.MILLIS_PER_SECOND
                "playlist-count" -> {
                    listeners.sendEvent(EVENT_TIMELINE_CHANGED) { listener ->
                        listener.onTimelineChanged(
                            timeline,
                            TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
                        )
                    }
                }
                "playlist-current-pos" -> {
                    if (value < 0) {
                        return@post
                    }
                    currentMediaItemIndex = value.toInt()
                    val newMediaItem = currentMediaItem
                    if (oldMediaItem?.mediaId != newMediaItem?.mediaId) {
                        oldMediaItem = newMediaItem
                        listeners.sendEvent(EVENT_MEDIA_ITEM_TRANSITION) { listener ->
                            listener.onMediaItemTransition(
                                newMediaItem,
                                MEDIA_ITEM_TRANSITION_REASON_AUTO,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun eventProperty(property: String, value: Double) {
        handler.post {
            when (property) {
                "speed" -> {
                    playbackParameters = playbackParameters.withSpeed(value.toFloat())
                    listeners.sendEvent(EVENT_PLAYBACK_PARAMETERS_CHANGED) { listener ->
                        listener.onPlaybackParametersChanged(playbackParameters)
                    }
                }
            }
        }
    }

    override fun event(@MPVLib.Event eventId: Int) {
        handler.post {
            when (eventId) {
                MPVLib.MPV_EVENT_START_FILE -> {
                    if (!isPlayerReady) {
                        for (command in initialCommands) {
                            MPVLib.command(command)
                        }
                    }
                }
                MPVLib.MPV_EVENT_FILE_LOADED -> {
                    isSeekable = MPVLib.getPropertyBoolean("seekable")
                    currentDurationMs =
                        (MPVLib.getPropertyDouble("duration") * C.MILLIS_PER_SECOND).toLong()

                    // TorrServer streams can fail if we seek too early. Apply resume seek only
                    // after file-loaded and only if the stream reports seekable.
                    if (pendingSeekToSec != 0L && isSeekable) {
                        val seekTo = pendingSeekToSec
                        pendingSeekToSec = 0L
                        runCatching { MPVLib.command(arrayOf("seek", seekTo.toString(), "absolute")) }
                    }

                    if (pendingVideoReinitAfterFileLoaded) {
                        pendingVideoReinitAfterFileLoaded = false
                        // mpv is initialized at this point; wait for a video-reconfig event.
                        // If it doesn't happen soon, do a controlled video reset.
                        val token = loadToken
                        handler.postDelayed({
                            if (token != loadToken) return@postDelayed
                            ensureSurfaceAttached()
                            if (waitingForVideoReconfig) {
                                Log.w(TAG, "No video-reconfig after file-loaded; forcing vo reset + video-reload")
                                waitingForVideoReconfig = false
                                runCatching { MPVLib.setOptionString("vo", "null") }
                                runCatching { MPVLib.detachSurface() }
                                ensureSurfaceAttached()
                                runCatching { MPVLib.setOptionString("vo", videoOutput) }
                                runCatching { MPVLib.command(arrayOf("video-reload")) }
                            }
                        }, 800)
                    }
                }
                MPVLib.MPV_EVENT_VIDEO_RECONFIG -> {
                    waitingForVideoReconfig = false
                }
                MPVLib.MPV_EVENT_SEEK -> {
                    setPlayerStateAndNotifyIfChanged(playbackState = STATE_BUFFERING)
                    listeners.sendEvent(EVENT_POSITION_DISCONTINUITY) { listener ->
                        @Suppress("DEPRECATION")
                        listener.onPositionDiscontinuity(DISCONTINUITY_REASON_SEEK)
                    }
                }
                MPVLib.MPV_EVENT_PLAYBACK_RESTART -> {
                    if (!isPlayerReady) {
                        isPlayerReady = true
                        seekTo(C.TIME_UNSET)
                        if (playWhenReady) {
                            Log.d(TAG, "Starting playback...")
                            MPVLib.setPropertyBoolean("pause", false)
                        }
                        for (videoListener in videoListeners) {
                            videoListener.onRenderedFirstFrame()
                        }
                    } else {
                        setPlayerStateAndNotifyIfChanged(playbackState = STATE_READY)
                    }
                }
                else -> Unit
            }
        }
    }

    private fun setPlayerStateAndNotifyIfChanged(
        playWhenReady: Boolean = getPlayWhenReady(),
        @Player.PlayWhenReadyChangeReason
        playWhenReadyChangeReason: Int = PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        @Player.State playbackState: Int = getPlaybackState(),
    ) {
        var playerStateChanged = false
        val wasPlaying = isPlaying
        if (playbackState != getPlaybackState()) {
            this.playbackState = playbackState
            listeners.queueEvent(EVENT_PLAYBACK_STATE_CHANGED) { listener ->
                listener.onPlaybackStateChanged(playbackState)
            }
            playerStateChanged = true
        }
        if (playWhenReady != getPlayWhenReady()) {
            this.currentPlayWhenReady = playWhenReady
            listeners.queueEvent(EVENT_PLAY_WHEN_READY_CHANGED) { listener ->
                listener.onPlayWhenReadyChanged(playWhenReady, playWhenReadyChangeReason)
            }
            playerStateChanged = true
        }
        if (playerStateChanged) {
            listeners.queueEvent(C.INDEX_UNSET) { listener ->
                listener.onPlaybackStateChanged(playbackState)
            }
        }
        if (wasPlaying != isPlaying) {
            listeners.queueEvent(EVENT_IS_PLAYING_CHANGED) { listener ->
                listener.onIsPlayingChanged(isPlaying)
            }
        }
        listeners.flushEvents()
    }

    private fun selectTrack(trackType: MPVTrackType, id: String) {
        MPVLib.setPropertyString(trackType.type, id)
    }

    private val timeline: Timeline =
        object : Timeline() {
            override fun getWindowCount(): Int {
                return internalMediaItems.size
            }

            override fun getWindow(
                windowIndex: Int,
                window: Window,
                defaultPositionProjectionUs: Long,
            ): Window {
                val currentMediaItem =
                    internalMediaItems.getOrNull(windowIndex) ?: MediaItem.Builder().build()
                return window.set(
                    windowIndex,
                    currentMediaItem,
                    null,
                    C.TIME_UNSET,
                    C.TIME_UNSET,
                    C.TIME_UNSET,
                    isSeekable,
                    false,
                    currentMediaItem.liveConfiguration,
                    0,
                    Util.msToUs(currentDurationMs ?: C.TIME_UNSET),
                    windowIndex,
                    windowIndex,
                    0,
                )
            }

            override fun getPeriodCount(): Int {
                return internalMediaItems.size
            }

            override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
                return period.set(
                    periodIndex,
                    periodIndex,
                    periodIndex,
                    Util.msToUs(currentDurationMs ?: C.TIME_UNSET),
                    0,
                )
            }

            override fun getIndexOfPeriod(uid: Any): Int {
                return uid as Int
            }

            override fun getUidOfPeriod(periodIndex: Int): Any {
                return periodIndex
            }
        }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                val oldAudioFocusCallback = audioFocusCallback
                val wasPlaying = isPlaying
                MPVLib.setPropertyBoolean("pause", true)
                setPlayerStateAndNotifyIfChanged(
                    playWhenReady = false,
                    playWhenReadyChangeReason = PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS,
                )
                audioFocusCallback = {
                    oldAudioFocusCallback()
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

    override fun getApplicationLooper(): Looper {
        return handler.looper
    }

    override fun addListener(listener: Player.Listener) {
        listeners.add(listener)
        videoListeners.add(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)
        videoListeners.remove(listener)
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
        MPVLib.command(arrayOf("playlist-clear"))
        MPVLib.command(arrayOf("playlist-remove", "current"))
        internalMediaItems = mediaItems

        internalMediaItems.forEachIndexed { index, mediaItem ->
            val uri = mediaItem.localConfiguration?.uri ?: return@forEachIndexed
            MPVLib.command(
                arrayOf(
                    "loadfile",
                    uri.toString(),
                    if (index == 0) "replace" else "append",
                )
            )
        }

        initialIndex = 0
        initialSeekTo = 0L
    }

    override fun setMediaItems(
        mediaItems: MutableList<MediaItem>,
        startWindowIndex: Int,
        startPositionMs: Long,
    ) {
        MPVLib.command(arrayOf("playlist-clear"))
        MPVLib.command(arrayOf("playlist-remove", "current"))
        internalMediaItems = mediaItems

        internalMediaItems.forEachIndexed { index, mediaItem ->
            val uri = mediaItem.localConfiguration?.uri ?: return@forEachIndexed
            MPVLib.command(
                arrayOf(
                    "loadfile",
                    uri.toString(),
                    if (index == 0) "replace" else "append",
                )
            )
        }

        initialIndex = startWindowIndex
        initialSeekTo = startPositionMs / 1000
        currentMediaItemIndex = startWindowIndex
    }

    override fun addMediaItems(index: Int, mediaItems: MutableList<MediaItem>) {
        internalMediaItems.addAll(index, mediaItems)
        mediaItems.forEach { mediaItem ->
            MPVLib.command(
                arrayOf(
                    "loadfile",
                    "${mediaItem.localConfiguration?.uri}",
                    "insert-at",
                    index.toString(),
                )
            )
        }
    }

    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {
        TODO("Not yet implemented")
    }

    override fun replaceMediaItems(
        fromIndex: Int,
        toIndex: Int,
        mediaItems: MutableList<MediaItem>,
    ) {
        TODO("Not yet implemented")
    }

    override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
        TODO("Not yet implemented")
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
                    (hasPreviousMediaItem() ||
                        !isCurrentMediaItemLive ||
                        isCurrentMediaItemSeekable) &&
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

    private fun resetInternalState() {
        isPlayerReady = false
        isSeekable = false
        playbackState = STATE_IDLE
        currentPlayWhenReady = false
        currentPositionMs = null
        currentDurationMs = null
        currentCacheDurationMs = null
        currentTracks = Tracks.EMPTY
        playbackParameters = PlaybackParameters.DEFAULT
        initialCommands.clear()
    }

    override fun getPlaybackState(): Int {
        return playbackState
    }

    override fun getPlayWhenReady(): Boolean {
        return currentPlayWhenReady
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady != getPlayWhenReady()) {
            currentPlayWhenReady = playWhenReady
            MPVLib.setPropertyBoolean("pause", !playWhenReady)
            listeners.sendEvent(EVENT_PLAY_WHEN_READY_CHANGED) { listener ->
                listener.onPlayWhenReadyChanged(
                    playWhenReady,
                    PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
                )
            }
            listeners.sendEvent(EVENT_IS_PLAYING_CHANGED) { listener ->
                listener.onIsPlayingChanged(isPlaying)
            }
        }
    }

    override fun prepare() {
        if (internalMediaItems.isNotEmpty()) {
            prepareMediaItem(currentMediaItemIndex)
        }
    }

    private fun prepareMediaItem(index: Int) {
        internalMediaItems.getOrNull(index) ?: return

        initialCommands = mutableListOf()

        // Resume position (seconds) will be applied after FILE_LOADED if seekable.
        pendingSeekToSec = initialSeekTo

        val oldIndex = currentMediaItemIndex
        currentMediaItemIndex = index

        // Since we no longer rely on mpv playlist-current-pos, fire MediaItemTransition
        // ourselves so UI title updates correctly.
        if (oldIndex != index) {
            val newMediaItem = currentMediaItem
            oldMediaItem = newMediaItem
            listeners.sendEvent(EVENT_MEDIA_ITEM_TRANSITION) { listener ->
                listener.onMediaItemTransition(
                    newMediaItem,
                    MEDIA_ITEM_TRANSITION_REASON_SEEK,
                )
            }
        }

        setPlayerStateAndNotifyIfChanged(playbackState = STATE_BUFFERING)

        // We'll reattach surface and only force video reset if mpv doesn't emit video-reconfig.
        pendingVideoReinitAfterFileLoaded = true
        waitingForVideoReconfig = true
        loadToken++

        val uri = internalMediaItems[index].localConfiguration?.uri ?: return
        // Restart playback like a fresh start. This avoids mpv internal playlist switching
        // issues on TorrServer streams (EOF/seek failures and black screen).
        pendingLoadUri = uri.toString()
        pendingLoadMode = "replace"
        ensureSurfaceAttached()
        val holder = currentSurfaceHolder
        if (holder?.surface?.isValid == true) {
            val toLoad = pendingLoadUri
            if (toLoad != null) {
                pendingLoadUri = null
                MPVLib.command(arrayOf("loadfile", toLoad, pendingLoadMode))
            }
        }
    }

    override fun seekTo(
        mediaItemIndex: Int,
        positionMs: Long,
        @Player.Command seekCommand: Int,
        isRepeatingCurrentItem: Boolean,
    ) {
        if (mediaItemIndex == currentMediaItemIndex) {
            val seekTo =
                if (positionMs != C.TIME_UNSET) positionMs / C.MILLIS_PER_SECOND else initialSeekTo
            initialSeekTo =
                if (isPlayerReady) {
                    MPVLib.command(arrayOf("seek", "$seekTo", "absolute"))
                    0L
                } else {
                    seekTo
                }
        } else {
            prepareMediaItem(mediaItemIndex)
            setPlayWhenReady(true)
        }
    }

    override fun getSeekBackIncrement(): Long {
        return seekBackIncrement
    }

    override fun getSeekForwardIncrement(): Long {
        return seekForwardIncrement
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        if (getPlaybackParameters().speed != playbackParameters.speed) {
            MPVLib.setPropertyDouble("speed", playbackParameters.speed.toDouble())
        }
    }

    override fun getPlaybackParameters(): PlaybackParameters {
        return playbackParameters
    }

    override fun stop() {
        MPVLib.command(arrayOf("stop", "keep-playlist"))
    }

    override fun release() {
        if (handleAudioFocus) {
            AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequest)
        }
        resetInternalState()
        MPVLib.removeObserver(this)
        MPVLib.destroy()
    }

    override fun getCurrentTracks(): Tracks {
        return currentTracks
    }

    override fun getTrackSelectionParameters(): TrackSelectionParameters {
        return trackSelectionParameters
    }

    override fun setTrackSelectionParameters(parameters: TrackSelectionParameters) {
        trackSelectionParameters = parameters

        val disabledTrackTypes =
            parameters.disabledTrackTypes.map { MPVTrackType.fromMedia3TrackType(it) }

        val notOverriddenTypes =
            mutableSetOf(MPVTrackType.VIDEO, MPVTrackType.AUDIO, MPVTrackType.SUBTITLE)
        for (override in parameters.overrides) {
            val trackType = MPVTrackType.fromMedia3TrackType(override.key.type)
            notOverriddenTypes.remove(trackType)
            val id = override.key.getFormat(0).id ?: continue

            selectTrack(trackType, id)
        }
        for (notOverriddenType in notOverriddenTypes) {
            if (notOverriddenType in disabledTrackTypes) {
                selectTrack(notOverriddenType, "no")
            } else {
                selectTrack(notOverriddenType, "auto")
            }
        }
    }

    override fun getMediaMetadata(): MediaMetadata {
        return MediaMetadata.EMPTY
    }

    override fun getPlaylistMetadata(): MediaMetadata {
        return MediaMetadata.EMPTY
    }

    override fun setPlaylistMetadata(mediaMetadata: MediaMetadata) {
        TODO("Not yet implemented")
    }

    override fun getCurrentTimeline(): Timeline {
        return timeline
    }

    override fun getCurrentPeriodIndex(): Int {
        return currentMediaItemIndex
    }

    override fun getCurrentMediaItemIndex(): Int {
        return currentMediaItemIndex
    }

    override fun getDuration(): Long {
        return timeline.getWindow(currentMediaItemIndex, window).durationMs
    }

    override fun getCurrentPosition(): Long {
        return currentPositionMs ?: C.TIME_UNSET
    }

    override fun getBufferedPosition(): Long {
        return currentCacheDurationMs ?: contentPosition
    }

    override fun getTotalBufferedDuration(): Long {
        return bufferedPosition
    }

    override fun isPlayingAd(): Boolean {
        return false
    }

    override fun getCurrentAdGroupIndex(): Int {
        return C.INDEX_UNSET
    }

    override fun getCurrentAdIndexInAdGroup(): Int {
        return C.INDEX_UNSET
    }

    override fun getContentPosition(): Long {
        return currentPosition
    }

    override fun getContentBufferedPosition(): Long {
        return bufferedPosition
    }

    override fun getAudioAttributes(): AudioAttributes {
        return audioAttributes
    }

    override fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) {
        this.audioAttributes = audioAttributes
        this.handleAudioFocus = handleAudioFocus
    }

    override fun setVolume(audioVolume: Float) {
        TODO("Not yet implemented")
    }

    override fun getVolume(): Float {
        return MPVLib.getPropertyInt("volume") / 100F
    }

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
        if (surfaceHolder == null) {
            runCatching { MPVLib.detachSurface() }
        } else {
            setVideoSurface(surfaceHolder.surface)
        }
    }

    override fun setVideoSurfaceView(surfaceView: SurfaceView?) {
        val holder = surfaceView?.holder
        currentSurfaceHolder = holder
        holder?.addCallback(surfaceHolderCallback)

        // If surface is already created, SurfaceHolder.Callback.surfaceCreated won't fire.
        // Attach immediately to avoid audio-only playback after playlist switches.
        if (holder?.surface?.isValid == true) {
            ensureSurfaceAttached()
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
        if (textureView == null) {
            runCatching { MPVLib.detachSurface() }
        } else {
            setVideoSurface(textureView.surfaceTexture?.let { Surface(it) })
        }
    }

    override fun getVideoSize(): VideoSize {
        return VideoSize.UNKNOWN
    }

    override fun getSurfaceSize(): Size {
        return Size.UNKNOWN
    }

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        TODO("Not yet implemented")
    }

    override fun getShuffleModeEnabled(): Boolean {
        return false
    }

    override fun setRepeatMode(repeatMode: Int) {
        TODO("Not yet implemented")
    }

    override fun getRepeatMode(): Int {
        return repeatMode
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun setDeviceVolume(deviceVolume: Int) {
        // no-op (device volume is handled by the system AudioManager)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun setDeviceVolume(deviceVolume: Int, flags: Int) {
        // no-op
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun increaseDeviceVolume() {
        // no-op
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun increaseDeviceVolume(flags: Int) {
        // no-op
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun decreaseDeviceVolume() {
        // no-op
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun decreaseDeviceVolume(flags: Int) {
        // no-op
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun setDeviceMuted(muted: Boolean) {
        deviceMuted = muted
        runCatching { MPVLib.setPropertyBoolean("mute", muted) }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun setDeviceMuted(muted: Boolean, flags: Int) {
        setDeviceMuted(muted)
    }

    override fun getDeviceVolume(): Int {
        return C.VOLUME_FLAG_PLAY_SOUND
    }

    override fun isDeviceMuted(): Boolean {
        return deviceMuted
    }

    override fun mute() {
        setDeviceMuted(true)
    }

    override fun unmute() {
        setDeviceMuted(false)
    }

    override fun isLoading(): Boolean {
        return playbackState == STATE_BUFFERING
    }

    override fun getAudioSessionId(): Int {
        return C.AUDIO_SESSION_ID_UNSET
    }

    override fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo.UNKNOWN
    }

    override fun getMaxSeekToPreviousPosition(): Long {
        return C.TIME_UNSET
    }

    override fun getCurrentCues(): CueGroup {
        return CueGroup.EMPTY_TIME_ZERO
    }

    override fun getPlayerError(): PlaybackException? {
        return null
    }

    override fun getPlaybackSuppressionReason(): Int {
        return PLAYBACK_SUPPRESSION_REASON_NONE
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

                val track = when (trackType) {
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
        private const val TAG = "MPVPlayer"

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
