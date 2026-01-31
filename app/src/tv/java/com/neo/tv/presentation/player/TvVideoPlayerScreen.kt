package com.neo.tv.presentation.player

import android.app.Application
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import com.neo.neomovies.ui.settings.PlayerEngineManager
import com.neo.neomovies.ui.settings.PlayerEngineMode
import com.neo.neomovies.ui.settings.SourceManager
import com.neo.neomovies.ui.settings.SourceMode
import com.neo.player.PlayerActivity
import com.neo.player.PlayerViewModel
import com.neo.tv.presentation.player.components.TvVideoPlayerControls
import com.neo.tv.presentation.player.components.TvVideoPlayerOverlay
import com.neo.tv.presentation.player.components.TvVideoPlayerPulse
import com.neo.tv.presentation.player.components.TvVideoPlayerPulseState
import com.neo.tv.presentation.player.components.rememberTvVideoPlayerPulseState
import com.neo.tv.presentation.player.components.rememberTvVideoPlayerState

@Composable
fun TvVideoPlayerScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val args = TvPlayerArgs
    val urls = args.urls

    if (urls == null || urls.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val owner = LocalViewModelStoreOwner.current ?: return
    val savedStateOwner = LocalSavedStateRegistryOwner.current

    val effectiveUseExo = remember(args.useExo) {
        args.useExo || PlayerEngineManager.getMode(context) == PlayerEngineMode.EXO
    }
    val effectiveUseCollapsHeaders =
        args.useCollapsHeaders || SourceManager.getMode(context) == SourceMode.COLLAPS

    val defaultArgs = remember(effectiveUseExo, effectiveUseCollapsHeaders) {
        Bundle().apply {
            putBoolean(PlayerActivity.EXTRA_USE_EXO, effectiveUseExo)
            putBoolean(PlayerActivity.EXTRA_USE_COLLAPS_HEADERS, effectiveUseCollapsHeaders)
        }
    }

    val viewModelKey = remember(urls, effectiveUseExo, effectiveUseCollapsHeaders, args.startIndex, args.title) {
        "tv_player_${effectiveUseExo}_${effectiveUseCollapsHeaders}_${args.startIndex}_${args.title}_${urls.hashCode()}"
    }

    val viewModel: PlayerViewModel = viewModel(
        viewModelStoreOwner = owner,
        key = viewModelKey,
        factory = SavedStateViewModelFactory(
            context.applicationContext as Application,
            savedStateOwner,
            defaultArgs,
        )
    )

    val playerState = rememberTvVideoPlayerState(hideSeconds = 4)
    val pulseState = rememberTvVideoPlayerPulseState()

    // --- Логика двойного нажатия "Назад" ---
    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    val handleBackPress = {
        if (playerState.isControlsVisible) {
            playerState.hideControls()
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressTime < 2000) {
                // Если прошло меньше 2 секунд с последнего нажатия -> Выходим
                onBack()
            } else {
                // Иначе показываем тост и обновляем время
                lastBackPressTime = currentTime
                Toast.makeText(context, "Нажмите еще раз для выхода", Toast.LENGTH_SHORT).show()
                // Опционально: можно показать контроллы, чтобы пользователь видел, что плеер жив
                playerState.showControls(viewModel.player.isPlaying)
            }
        }
    }

    BackHandler {
        handleBackPress()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusable()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_UP) return@onKeyEvent false

                val keyCode = event.nativeKeyEvent.keyCode
                
                // Если контроллы скрыты, обрабатываем быструю перемотку стрелками
                if (!playerState.isControlsVisible) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            viewModel.player.seekBack()
                            pulseState.displayPulse(TvVideoPlayerPulseState.Type.BACK)
                            playerState.showControls(viewModel.player.isPlaying)
                            return@onKeyEvent true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            viewModel.player.seekForward()
                            pulseState.displayPulse(TvVideoPlayerPulseState.Type.FORWARD)
                            playerState.showControls(viewModel.player.isPlaying)
                            return@onKeyEvent true
                        }
                    }
                }

                when (keyCode) {
                    KeyEvent.KEYCODE_BACK -> {
                        handleBackPress()
                        return@onKeyEvent true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER -> {
                        if (playerState.isControlsVisible) {
                            // Если меню открыто, даем обработать клик самому UI (или ставим паузу)
                             if (viewModel.player.isPlaying) viewModel.player.pause() else viewModel.player.play()
                        } else {
                            playerState.showControls(viewModel.player.isPlaying)
                        }
                        return@onKeyEvent true
                    }
                    // Любые нажатия стрелок пробуждают интерфейс
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (!playerState.isControlsVisible) {
                            playerState.showControls(viewModel.player.isPlaying)
                            // Не возвращаем true, чтобы фокус мог переместиться на элементы управления
                            return@onKeyEvent false 
                        }
                    }
                }
                false
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.player = viewModel.player
            }
        )

        TvVideoPlayerOverlay(
            modifier = Modifier.align(Alignment.BottomCenter),
            isPlaying = viewModel.player.isPlaying,
            isControlsVisible = playerState.isControlsVisible,
            showControls = { playerState.showControls(viewModel.player.isPlaying) },
            centerButton = { TvVideoPlayerPulse(pulseState) },
            controls = {
                TvVideoPlayerControls(
                    player = viewModel.player,
                    viewModel = viewModel,
                    title = args.title,
                    useCollapsHeaders = effectiveUseCollapsHeaders,
                    isControlsVisible = playerState.isControlsVisible,
                    onShowControls = { playerState.showControls(viewModel.player.isPlaying) },
                )
            },
        )
    }

    DisposableEffect(urls, args.startIndex) {
        viewModel.initializePlayer(
            urls = urls,
            names = args.names,
            startIndex = args.startIndex,
            title = args.title,
            startFromBeginning = false,
        )
        onDispose {
            TvPlayerArgs.clear()
        }
    }
}