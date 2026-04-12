package com.neo.player

import android.app.AppOpsManager
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.util.Rational
import android.view.SurfaceView
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Space
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import com.neo.neomovies.R
import com.neo.neomovies.databinding.ActivityPlayerBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

var isControlsLocked: Boolean = false

class PlayerActivity : BasePlayerActivity() {

    lateinit var binding: ActivityPlayerBinding

    private val handler = Handler(Looper.getMainLooper())
    private var allohaForwardingPlayer: androidx.media3.common.ForwardingPlayer? = null

    override val viewModel: PlayerViewModel by viewModels()

    private val isPipSupported by lazy {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            return@lazy false
        }
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager?
        appOps?.checkOpNoThrow(
            AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
            Process.myUid(),
            packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra(EXTRA_URL) ?: ""
        val urls = intent.getStringArrayListExtra(EXTRA_URLS)
        val names = intent.getStringArrayListExtra(EXTRA_NAMES)
        val startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)
        val title = intent.getStringExtra(EXTRA_TITLE)
        val startFromBeginning = intent.getBooleanExtra(EXTRA_START_FROM_BEGINNING, false)
        val useExo = intent.getBooleanExtra(EXTRA_USE_EXO, false)
        val kinopoiskId = intent.getIntExtra(EXTRA_KINOPOISK_ID, -1).takeIf { it > 0 }

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        viewModel.setEngine(useExo)
        binding.playerView.player = viewModel.player
        binding.playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                if (visibility == View.GONE) {
                    hideSystemUI()
                }
            }
        )

        val playerControls = binding.playerView.findViewById<View>(R.id.player_controls)
        val lockedControls = binding.playerView.findViewById<View>(R.id.locked_player_view)

        val overlay = binding.playerView.findViewById<FrameLayout?>(androidx.media3.ui.R.id.exo_overlay)

        val playPauseButton = binding.playerView.findViewById<ImageButton>(R.id.exo_play_pause)

        val rippleFfwd = binding.imageFfwdAnimationRipple
        val rippleRewind = binding.imageRewindAnimationRipple
        val ripplePlayback = binding.imagePlaybackAnimationRipple
        val doubleTapDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean {
                    return true
                }

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    if (isControlsLocked || viewModel.isInPictureInPictureMode) return false
                    if (binding.playerView.isControllerFullyVisible) {
                        binding.playerView.hideController()
                    } else {
                        binding.playerView.showController()
                    }
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (isControlsLocked || viewModel.isInPictureInPictureMode) return false
                    val w = overlay?.width?.takeIf { it > 0 } ?: return false

                    val areaWidth = w / 5
                    val leftBoundary = areaWidth * 2
                    val rightBoundary = areaWidth * 3

                    val seekMs = 5_000L
                    when (e.x.toInt()) {
                        in 0 until leftBoundary -> {
                            val player = viewModel.player
                            val newPos = (player.currentPosition - seekMs).coerceAtLeast(0L)
                            player.seekTo(newPos)
                            animateRipple(rippleRewind)
                        }
                        in leftBoundary until rightBoundary -> {
                            val player = viewModel.player
                            if (player.isPlaying) player.pause() else player.play()
                            animateRipple(ripplePlayback)
                        }
                        else -> {
                            val player = viewModel.player
                            val dur = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                            val newPos = (player.currentPosition + seekMs).coerceAtMost(dur)
                            player.seekTo(newPos)
                            animateRipple(rippleFfwd)
                        }
                    }
                    return true
                }
            },
        )

        overlay?.setOnTouchListener { _, event -> doubleTapDetector.onTouchEvent(event) }

        isControlsLocked = false

        configureInsets(playerControls)
        configureInsets(lockedControls)

        binding.playerView.findViewById<View>(R.id.back_button).setOnClickListener {
            finishPlayback()
        }

        val useCollapsHeaders = intent.getBooleanExtra(EXTRA_USE_COLLAPS_HEADERS, false)
        val isAllohaSource = intent.getBooleanExtra(EXTRA_IS_ALLOHA, false)

        // For Alloha: wrap player so exo_prev/exo_next switch episodes natively via ForwardingPlayer
        if (isAllohaSource) {
            allohaForwardingPlayer = object : androidx.media3.common.ForwardingPlayer(viewModel.player) {
                private fun hasPrev() = com.neo.neomovies.data.alloha.AllohaSessionHolder.currentEpisodeIndex > 0
                private fun hasNext() = com.neo.neomovies.data.alloha.AllohaSessionHolder.currentEpisodeIndex <
                    com.neo.neomovies.data.alloha.AllohaSessionHolder.episodeIframeUrls.size - 1

                override fun hasPreviousMediaItem() = hasPrev()
                override fun hasNextMediaItem() = hasNext()
                // PlayerControlView calls seekToNext/seekToPrevious (not MediaItem variants)
                override fun seekToPrevious() { switchAllohaEpisode(-1) }
                override fun seekToNext() { switchAllohaEpisode(+1) }
                override fun seekToPreviousMediaItem() { switchAllohaEpisode(-1) }
                override fun seekToNextMediaItem() { switchAllohaEpisode(+1) }

                override fun getAvailableCommands(): androidx.media3.common.Player.Commands {
                    return super.getAvailableCommands().buildUpon()
                        .apply {
                            if (hasPrev()) {
                                add(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS)
                                add(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                            } else {
                                remove(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS)
                                remove(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                            }
                            if (hasNext()) {
                                add(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT)
                                add(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                            } else {
                                remove(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT)
                                remove(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                            }
                        }
                        .build()
                }

                override fun isCommandAvailable(command: Int): Boolean =
                    getAvailableCommands().contains(command)
            }
            binding.playerView.player = allohaForwardingPlayer
        }

        val videoNameTextView = binding.playerView.findViewById<TextView>(R.id.video_name)
        val audioButton = binding.playerView.findViewById<ImageButton>(R.id.btn_audio_track)
        val subtitleButton = binding.playerView.findViewById<ImageButton>(R.id.btn_subtitle)
        val speedButton = binding.playerView.findViewById<ImageButton>(R.id.btn_speed)
        val qualityButton = binding.playerView.findViewById<ImageButton>(R.id.btn_quality)
        val aspectRatioButton = binding.playerView.findViewById<ImageButton>(R.id.btn_aspect_ratio)

        // Quality selection: available for both Collaps (DASH/HLS) and Alloha (HLS proxy)
        qualityButton.isVisible = useCollapsHeaders || isAllohaSource

        audioButton.isEnabled = false
        audioButton.imageAlpha = 75
        subtitleButton.isEnabled = false
        subtitleButton.imageAlpha = 75

        speedButton.isEnabled = false
        speedButton.imageAlpha = 75

        qualityButton.isEnabled = false
        qualityButton.imageAlpha = 75

        // For Alloha: enable audio button immediately for translation switching
        if (isAllohaSource) {
            val holder = com.neo.neomovies.data.alloha.AllohaSessionHolder
            if (holder.translationNames.size > 1) {
                audioButton.isEnabled = true
                audioButton.imageAlpha = 255
            }
        }

        audioButton.setOnClickListener {
            if (isAllohaSource) {
                showAllohaTranslationPicker()
                return@setOnClickListener
            }
            TrackSelectionDialogFragment
                .newInstance(C.TRACK_TYPE_AUDIO)
                .show(supportFragmentManager, "trackselectiondialog")
        }

        subtitleButton.setOnClickListener {
            TrackSelectionDialogFragment
                .newInstance(C.TRACK_TYPE_TEXT)
                .show(supportFragmentManager, "trackselectiondialog")
        }

        qualityButton.setOnClickListener {
            if (isAllohaSource) {
                showAllohaQualityPicker()
                return@setOnClickListener
            }
            TrackSelectionDialogFragment
                .newInstance(C.TRACK_TYPE_VIDEO)
                .show(supportFragmentManager, "trackselectiondialog")
        }

        speedButton.setOnClickListener {
            SpeedSelectionDialogFragment
                .newInstance()
                .show(supportFragmentManager, "speedselectiondialog")
        }

        aspectRatioButton.setOnClickListener {
            binding.playerView.resizeMode =
                if (binding.playerView.resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                    AspectRatioFrameLayout.RESIZE_MODE_FILL
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
        }

        val pipButton = binding.playerView.findViewById<ImageButton>(R.id.btn_pip)
        pipButton.setOnClickListener {
            pictureInPicture()
        }

        playPauseButton.setOnClickListener {
            if (viewModel.player.playWhenReady) {
                viewModel.playWhenReady = false
                viewModel.player.pause()
            } else {
                viewModel.playWhenReady = true
                viewModel.player.play()
            }
        }

        // Set marker color
        val timeBar = binding.playerView.findViewById<DefaultTimeBar>(R.id.exo_progress)
        timeBar.setAdMarkerColor(Color.WHITE)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { uiState ->
                        videoNameTextView.text = uiState.currentItemTitle

                        if (uiState.fileLoaded) {
                            audioButton.isEnabled = true
                            audioButton.imageAlpha = 255
                            subtitleButton.isEnabled = true
                            subtitleButton.imageAlpha = 255
                            speedButton.isEnabled = true
                            speedButton.imageAlpha = 255
                            if (useCollapsHeaders || isAllohaSource) {
                                qualityButton.isEnabled = true
                                qualityButton.imageAlpha = 255
                            }
                            // Show PiP button if supported
                            val pipButton = binding.playerView.findViewById<ImageButton>(R.id.btn_pip)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isPipSupported) {
                                pipButton.visibility = View.VISIBLE
                                pipButton.isEnabled = true
                                pipButton.imageAlpha = 255
                            }
                        }
                    }
                }

                launch {
                    viewModel.eventsChannelFlow.collect { event ->
                        when (event) {
                            is PlayerEvents.NavigateBack -> finishPlayback()
                            is PlayerEvents.IsPlayingChanged -> {
                                val shouldShowPause = viewModel.player.playWhenReady
                                playPauseButton.setImageResource(
                                    if (shouldShowPause) R.drawable.ic_pause else R.drawable.ic_play
                                )

                                if (shouldShowPause) {
                                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                } else {
                                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                }

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    runCatching { setPictureInPictureParams(pipParams(shouldShowPause)) }
                                }
                            }

                            is PlayerEvents.PlayWhenReadyChanged -> {
                                playPauseButton.setImageResource(
                                    if (event.playWhenReady) R.drawable.ic_pause else R.drawable.ic_play
                                )

                                if (event.playWhenReady) {
                                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                } else {
                                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                }

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    runCatching { setPictureInPictureParams(pipParams(event.playWhenReady)) }
                                }
                            }
                        }
                    }
                }

                launch {
                    while (true) {
                        viewModel.updatePlaybackProgress()
                        delay(5000L)
                    }
                }

                // Skip button: show when position is within a skipTime range
                if (isAllohaSource) {
                    launch {
                        val skipBtn = binding.btnSkip
                        skipBtn.setOnClickListener {
                            val ranges = com.neo.neomovies.data.alloha.AllohaSessionHolder.skipRanges
                            val pos = viewModel.player.currentPosition
                            val range = ranges.firstOrNull { pos in it } ?: return@setOnClickListener
                            viewModel.player.seekTo(range.last)
                        }
                        while (true) {
                            val ranges = com.neo.neomovies.data.alloha.AllohaSessionHolder.skipRanges
                            val pos = viewModel.player.currentPosition
                            val inRange = ranges.any { pos in it }
                            skipBtn.visibility = if (inRange) View.VISIBLE else View.GONE
                            delay(500L)
                        }
                    }
                }
            }
        }

        // Use PlayerControlView to connect next/prev to chapters if needed later.
        findViewById<PlayerControlView>(R.id.exo_controller)

        viewModel.initializePlayer(
            urls = urls ?: arrayListOf(url),
            names = names,
            startIndex = startIndex,
            title = title,
            startFromBeginning = startFromBeginning,
            kinopoiskId = kinopoiskId,
        )
        hideSystemUI()

        // Default landscape like Findroid
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    private fun animateRipple(image: ImageView) {
        image.animate().cancel()
        image.alpha = 0f
        image.scaleX = 1f
        image.scaleY = 1f

        val rippleImageHeight = image.height.takeIf { it > 0 } ?: return
        val playerViewHeight = binding.playerView.height.toFloat().takeIf { it > 0f } ?: return
        val playerViewWidth = binding.playerView.width.toFloat().takeIf { it > 0f } ?: return
        val scaleDifference = playerViewHeight / rippleImageHeight
        val playerViewAspectRatio = playerViewWidth / playerViewHeight
        val scaleValue = scaleDifference * playerViewAspectRatio

        image
            .animate()
            .alpha(1f)
            .scaleX(scaleValue)
            .scaleY(scaleValue)
            .setDuration(180)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                image
                    .animate()
                    .alpha(0f)
                    .setDuration(150)
                    .setInterpolator(AccelerateInterpolator())
                    .withEndAction {
                        image.scaleX = 1f
                        image.scaleY = 1f
                    }
                    .start()
            }
            .start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val url = intent.getStringExtra(EXTRA_URL) ?: ""
        val urls = intent.getStringArrayListExtra(EXTRA_URLS)
        val names = intent.getStringArrayListExtra(EXTRA_NAMES)
        val startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)
        val title = intent.getStringExtra(EXTRA_TITLE)
        val startFromBeginning = intent.getBooleanExtra(EXTRA_START_FROM_BEGINNING, false)
        val useExo = intent.getBooleanExtra(EXTRA_USE_EXO, false)
        val kinopoiskId = intent.getIntExtra(EXTRA_KINOPOISK_ID, -1).takeIf { it > 0 }

        viewModel.setEngine(useExo)
        binding.playerView.player = allohaForwardingPlayer ?: viewModel.player
        viewModel.initializePlayer(
            urls = urls ?: listOf(url),
            names = names,
            startIndex = startIndex,
            title = title,
            startFromBeginning = startFromBeginning,
            kinopoiskId = kinopoiskId,
            episodeProgressCallback = null, // Will be implemented with proper callback mechanism
        )
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            viewModel.player.isPlaying &&
            !isControlsLocked &&
            isPipSupported
        ) {
            pictureInPicture()
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.updatePlaybackProgress()
    }

    override fun onStop() {
        super.onStop()
        viewModel.updatePlaybackProgress()
    }

    private fun finishPlayback() {
        viewModel.updatePlaybackProgress()
        runCatching {
            viewModel.player.clearVideoSurfaceView(binding.playerView.videoSurfaceView as SurfaceView)
        }
        handler.removeCallbacksAndMessages(null)
        finish()
    }

    private fun pipParams(
        enableAutoEnter: Boolean = viewModel.player.isPlaying
    ): PictureInPictureParams {
        val viewW = binding.playerView.width
        val viewH = binding.playerView.height
        val displayAspectRatio =
            if (viewW > 0 && viewH > 0) {
                Rational(viewW, viewH)
            } else {
                Rational(16, 9)
            }

        val aspectRatio =
            binding.playerView.player?.videoSize?.let {
                if (it.width > 0 && it.height > 0) {
                    Rational(
                        it.width.coerceAtMost((it.height * 2.39f).toInt()),
                        it.height.coerceAtMost((it.width * 2.39f).toInt()),
                    )
                } else {
                    null
                }
            } ?: Rational(16, 9)

        val sourceRectHint =
            if (viewW <= 0 || viewH <= 0) {
                null
            } else if (displayAspectRatio < aspectRatio) {
                val space = ((viewH - (viewW.toFloat() / aspectRatio.toFloat())) / 2).toInt()
                Rect(
                    0,
                    space,
                    viewW,
                    (viewW.toFloat() / aspectRatio.toFloat()).toInt() + space,
                )
            } else {
                val space = ((viewW - (viewH.toFloat() * aspectRatio.toFloat())) / 2).toInt()
                Rect(
                    space,
                    0,
                    (viewH.toFloat() * aspectRatio.toFloat()).toInt() + space,
                    viewH,
                )
            }

        val builder =
            PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)

        sourceRectHint?.let { builder.setSourceRectHint(it) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(enableAutoEnter)
        }

        return builder.build()
    }

    private fun pictureInPicture() {
        if (!isPipSupported || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val entered = runCatching { enterPictureInPictureMode(pipParams()) }
            .onFailure { t ->
                Log.e("PlayerActivity", "Failed to enter Picture-in-Picture", t)
            }
            .getOrDefault(false)
        isEnteringPip = entered
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isEnteringPip = false
        viewModel.isInPictureInPictureMode = isInPictureInPictureMode

        binding.playerView.useController = !isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            binding.playerView.hideController()
        }
    }

    private fun switchAllohaEpisode(delta: Int) {
        val holder = com.neo.neomovies.data.alloha.AllohaSessionHolder
        val newIdx = holder.currentEpisodeIndex + delta
        if (newIdx < 0 || newIdx >= holder.episodeIframeUrls.size) return
        val iframeUrl = holder.episodeIframeUrls[newIdx].takeIf { it.isNotBlank() } ?: return
        val session = holder.session ?: return

        // Save progress for current episode before switching
        viewModel.updatePlaybackProgress()

        holder.currentEpisodeIndex = newIdx
        val episodeName = holder.episodeNames.getOrNull(newIdx) ?: ""

        val videoNameTextView = binding.playerView.findViewById<android.widget.TextView>(R.id.video_name)
        // Preserve show title: "Игра престолов • S01E02"
        val currentTitle = videoNameTextView.text.toString()
        val baseTitle = if (currentTitle.contains(" • ")) currentTitle.substringBefore(" • ") else currentTitle
        val displayTitle = if (baseTitle.isNotBlank() && baseTitle != getString(com.neo.neomovies.R.string.alloha_parsing_stream)) "$baseTitle • $episodeName" else episodeName

        session.onStreamReady = { _, _ -> }
        session.onM3u8Updated = { _ ->
            runOnUiThread {
                videoNameTextView.text = displayTitle
                viewModel.resetAudioOverride()
                viewModel.clearEpisodeProgress(newIdx)
                viewModel.player.stop()
                viewModel.player.seekTo(0)
                viewModel.player.prepare()
                viewModel.player.playWhenReady = true
                binding.playerView.player = allohaForwardingPlayer
            }
        }
        session.onError = { error ->
            runOnUiThread {
                android.widget.Toast.makeText(this, "Error: $error", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        session.startSession(iframeUrl)
    }

    private fun showAllohaQualityPicker() {
        val holder = com.neo.neomovies.data.alloha.AllohaSessionHolder
        val session = holder.session ?: return
        val qualityMap = session.lastQualityMap
        if (qualityMap.isEmpty()) return

        val orderedKeys = listOf("2160", "1440", "1080", "720", "480", "360")
            .filter { qualityMap.containsKey(it) }
        if (orderedKeys.isEmpty()) return

        val autoLabel = getString(com.neo.neomovies.R.string.quality_auto)
        val allKeys = listOf("") + orderedKeys  // "" = auto
        val labels = (listOf(autoLabel) + orderedKeys.map { "${it}p" }).toTypedArray()
        val currentIdx = if (holder.isAutoQuality) 0 else allKeys.indexOf(holder.currentQuality).coerceAtLeast(0)

        android.app.AlertDialog.Builder(this)
            .setTitle(getString(com.neo.neomovies.R.string.lumex_select_quality))
            .setSingleChoiceItems(labels, currentIdx) { dialog, which ->
                dialog.dismiss()
                val newKey = allKeys[which]
                if (newKey == holder.currentQuality && holder.isAutoQuality == (which == 0)) return@setSingleChoiceItems

                if (newKey.isBlank()) {
                    holder.isAutoQuality = true
                    holder.currentQuality = ""
                    val bestKey = com.neo.neomovies.data.alloha.AllohaSessionManager
                        .pickBestQualityPublic(this, qualityMap)
                    session.switchQuality(bestKey)
                } else {
                    holder.isAutoQuality = false
                    holder.currentQuality = newKey
                    session.switchQuality(newKey)
                }

                viewModel.resetAudioOverride()
                viewModel.player.stop()
                viewModel.player.prepare()
                viewModel.player.playWhenReady = true
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAllohaTranslationPicker() {
        val holder = com.neo.neomovies.data.alloha.AllohaSessionHolder
        val names = holder.translationNames
        val urls = holder.translationUrls
        if (names.isEmpty()) return

        val currentIdx = names.indexOf(holder.currentTranslation).coerceAtLeast(0)

        android.app.AlertDialog.Builder(this)
            .setTitle(getString(com.neo.neomovies.R.string.lumex_select_voiceover))
            .setSingleChoiceItems(names.toTypedArray(), currentIdx) { dialog, which ->
                dialog.dismiss()
                val newName = names[which]
                val newIframeUrl = urls.getOrNull(which) ?: return@setSingleChoiceItems
                if (newName == holder.currentTranslation) return@setSingleChoiceItems

                switchAllohaTranslation(newName, newIframeUrl)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun switchAllohaTranslation(translationName: String, iframeUrl: String) {
        val session = com.neo.neomovies.data.alloha.AllohaSessionHolder.session ?: return
        val holder = com.neo.neomovies.data.alloha.AllohaSessionHolder

        // Save current position so we know the user was at this point
        viewModel.updatePlaybackProgress()
        val wasPlaying = viewModel.player.playWhenReady

        val videoNameTextView = binding.playerView.findViewById<android.widget.TextView>(R.id.video_name)
        val originalTitle = videoNameTextView.text

        session.onStreamReady = { _, _ ->
            // Don't re-prepare yet: CDN auth (config_update) hasn't arrived.
            // Wait for onM3u8Updated which fires after config_update + proxy URL refresh.
            holder.currentTranslation = translationName

            // Save the translation preference
            getSharedPreferences("alloha_translation", MODE_PRIVATE)
                .edit()
                .putString("last_translation_name", translationName)
                .apply()
        }

        session.onM3u8Updated = { _ ->
            runOnUiThread {
                videoNameTextView.text = originalTitle
                // Reset audio override so Russian track is selected on new translation
                viewModel.resetAudioOverride()
                // Force reload: stop() invalidates cached manifest, prepare() re-fetches
                viewModel.player.stop()
                viewModel.player.prepare()
                viewModel.player.playWhenReady = wasPlaying
            }
        }

        session.onError = { error ->
            runOnUiThread {
                videoNameTextView.text = originalTitle
                android.widget.Toast.makeText(this, "Error: $error", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        session.startSession(iframeUrl)
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_URLS = "urls"
        const val EXTRA_NAMES = "names"
        const val EXTRA_START_INDEX = "startIndex"
        const val EXTRA_TITLE = "title"
        const val EXTRA_USE_EXO = "use_exo"
        const val EXTRA_USE_COLLAPS_HEADERS = "use_collaps_headers"
        const val EXTRA_START_FROM_BEGINNING = "start_from_beginning"
        const val EXTRA_KINOPOISK_ID = "kinopoisk_id"
        const val EXTRA_IS_ALLOHA = "is_alloha"

        fun intent(context: android.content.Context, url: String, title: String? = null, startFromBeginning: Boolean = false): Intent {
            return Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_START_FROM_BEGINNING, startFromBeginning)
                putExtra(EXTRA_USE_EXO, false)
            }
        }

        fun intentExo(context: android.content.Context, url: String, title: String? = null, startFromBeginning: Boolean = false): Intent {
            return Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_START_FROM_BEGINNING, startFromBeginning)
                putExtra(EXTRA_USE_EXO, true)
            }
        }

        fun intent(
            context: android.content.Context,
            urls: List<String>,
            names: List<String>? = null,
            startIndex: Int = 0,
            title: String? = null,
            startFromBeginning: Boolean = false,
            useExo: Boolean = false,
            useCollapsHeaders: Boolean = false,
            isAlloha: Boolean = false,
            kinopoiskId: Int? = null,
            episodeProgressCallback: ((Int, Int, Int, Long, Long) -> Unit)? = null,
        ): Intent {
            return Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_URL, urls.firstOrNull().orEmpty())
                putStringArrayListExtra(EXTRA_URLS, ArrayList(urls))
                putStringArrayListExtra(EXTRA_NAMES, ArrayList(names.orEmpty()))
                putExtra(EXTRA_START_INDEX, startIndex)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_START_FROM_BEGINNING, startFromBeginning)
                putExtra(EXTRA_USE_EXO, useExo)
                putExtra(EXTRA_USE_COLLAPS_HEADERS, useCollapsHeaders)
                putExtra(EXTRA_IS_ALLOHA, isAlloha)
                putExtra(EXTRA_KINOPOISK_ID, kinopoiskId)
            }
        }

        fun intentExo(
            context: android.content.Context,
            urls: List<String>,
            names: List<String>? = null,
            startIndex: Int = 0,
            title: String? = null,
            startFromBeginning: Boolean = false,
            useCollapsHeaders: Boolean = false,
            isAlloha: Boolean = false,
            kinopoiskId: Int? = null,
            episodeProgressCallback: ((Int, Int, Int, Long, Long) -> Unit)? = null,
        ): Intent {
            return Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_URL, urls.firstOrNull().orEmpty())
                putStringArrayListExtra(EXTRA_URLS, ArrayList(urls))
                putStringArrayListExtra(EXTRA_NAMES, ArrayList(names.orEmpty()))
                putExtra(EXTRA_START_INDEX, startIndex)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_START_FROM_BEGINNING, startFromBeginning)
                putExtra(EXTRA_USE_EXO, true)
                putExtra(EXTRA_USE_COLLAPS_HEADERS, useCollapsHeaders)
                putExtra(EXTRA_IS_ALLOHA, isAlloha)
                putExtra(EXTRA_KINOPOISK_ID, kinopoiskId)
            }
        }
    }
}
