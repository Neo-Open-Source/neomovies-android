package com.neo.tv.presentation.player

import android.app.Application
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import com.neo.player.PlayerViewModel
import com.neo.tv.presentation.player.components.TvVideoPlayerControls
import com.neo.tv.presentation.player.components.TvVideoPlayerOverlay
import com.neo.tv.presentation.player.components.TvVideoPlayerPulse
import com.neo.tv.presentation.player.components.rememberTvVideoPlayerPulseState
import com.neo.tv.presentation.player.components.rememberTvVideoPlayerState

@Composable
@UnstableApi
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

    val owner = LocalViewModelStoreOwner.current
    val savedStateOwner = owner as SavedStateRegistryOwner
    val defaultArgs = remember(args.useExo, args.useCollapsHeaders) {
        Bundle().apply {
            putBoolean(com.neo.player.PlayerActivity.EXTRA_USE_EXO, args.useExo)
            putBoolean(com.neo.player.PlayerActivity.EXTRA_USE_COLLAPS_HEADERS, args.useCollapsHeaders)
        }
    }
    val viewModel: PlayerViewModel = viewModel(
        viewModelStoreOwner = owner!!,
        factory = SavedStateViewModelFactory(
            (context.applicationContext as Application),
            savedStateOwner,
            defaultArgs,
        )
    )

    val playerState = rememberTvVideoPlayerState(hideSeconds = 4)
    val pulseState = rememberTvVideoPlayerPulseState()

    BackHandler(onBack = onBack)

    // Инициализация плеера ПЕРЕД рендерингом UI - исправление race condition
    LaunchedEffect(Unit) {
        viewModel.initializePlayer(
            urls = urls,
            names = args.names,
            startIndex = args.startIndex,
            title = args.title,
            startFromBeginning = false,
        )
    }

    Box(modifier = Modifier.fillMaxSize().focusable()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    useController = false
                    // TV специфичные настройки для правильной инициализации Surface
                    shutterBackgroundColor = android.graphics.Color.BLACK
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                // Проверка на null перед присваиванием плеера
                if (viewModel.player != null) {
                    view.player = viewModel.player
                }
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
                    title = args.title,
                    onShowControls = { playerState.showControls(viewModel.player.isPlaying) },
                )
            },
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            TvPlayerArgs.clear()
        }
    }
}