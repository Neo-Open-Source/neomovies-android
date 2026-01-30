package com.neo.tv.presentation.search

import android.view.KeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.neo.neomovies.R
import com.neo.neomovies.ui.home.collectAsStateWithLifecycleCompat
import com.neo.neomovies.ui.search.SearchViewModel
import com.neo.tv.presentation.common.TvMovieCard
import com.neo.tv.presentation.common.TvScreenScaffold
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvSearchScreen(
    onBack: () -> Unit,
    onOpenDetails: (String) -> Unit,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycleCompat()
    val listState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val focusManager = LocalFocusManager.current
    val textFieldRequester = remember { FocusRequester() }
    val textFieldInteraction = remember { MutableInteractionSource() }
    val isTextFieldFocused by textFieldInteraction.collectIsFocusedAsState()
    val textFieldBorderColor by animateColorAsState(
        targetValue = if (isTextFieldFocused) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.border,
        label = "search_focus_border",
    )

    val reachedBottom: Boolean by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem?.index != 0 && lastVisibleItem?.index == listState.layoutInfo.totalItemsCount - 1
        }
    }

    LaunchedEffect(reachedBottom) {
        if (reachedBottom) viewModel.loadNextPage()
    }

    TvScreenScaffold(
        title = stringResource(R.string.search_title),
        onBack = onBack,
    ) { padding ->
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Surface(
                shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.inverseOnSurface,
                    focusedContainerColor = MaterialTheme.colorScheme.inverseOnSurface,
                    pressedContainerColor = MaterialTheme.colorScheme.inverseOnSurface,
                    focusedContentColor = MaterialTheme.colorScheme.onSurface,
                    pressedContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                border = ClickableSurfaceDefaults.border(
                    focusedBorder = Border(
                        border = BorderStroke(width = 2.dp, color = textFieldBorderColor),
                        shape = MaterialTheme.shapes.medium,
                    ),
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                modifier = Modifier.fillMaxWidth(),
                onClick = { textFieldRequester.requestFocus() },
            ) {
                BasicTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .focusRequester(textFieldRequester)
                        .onKeyEvent {
                            if (it.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                                when (it.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_DOWN -> focusManager.moveFocus(FocusDirection.Down)
                                    KeyEvent.KEYCODE_DPAD_UP -> focusManager.moveFocus(FocusDirection.Up)
                                    KeyEvent.KEYCODE_BACK -> focusManager.moveFocus(FocusDirection.Exit)
                                }
                            }
                            true
                        },
                    interactionSource = textFieldInteraction,
                    cursorBrush = Brush.verticalGradient(
                        colors = listOf(MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.onSurface),
                    ),
                    textStyle = MaterialTheme.typography.titleSmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.setQuery(state.query) }),
                    maxLines = 1,
                    decorationBox = { inner ->
                        androidx.compose.foundation.layout.Box {
                            inner()
                            if (state.query.isBlank()) {
                                Text(
                                    text = stringResource(R.string.search_hint),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.graphicsLayer { alpha = 0.6f },
                                )
                            }
                        }
                    },
                )
            }

            when {
                state.isLoading -> {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { Text(text = stringResource(R.string.credits_loading)) }
                }
                state.error != null -> {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { Text(text = state.error ?: stringResource(R.string.common_error)) }
                }
                state.query.isBlank() -> {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { Text(text = stringResource(R.string.search_start_typing)) }
                }
                state.items.isEmpty() -> {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { Text(text = stringResource(R.string.search_nothing_found)) }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 180.dp),
                        modifier = Modifier.weight(1f),
                        state = listState,
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        items(state.items) { item ->
                            val rawId = when (val v = item.id) {
                                is Number -> v.toLong().toString()
                                else -> v?.toString()
                            }
                            val sourceId = rawId?.let { if (it.contains("_")) it else "kp_$it" }
                            TvMovieCard(
                                item = item,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { if (sourceId != null) onOpenDetails(sourceId) },
                            )
                        }
                    }
                }
            }
        }
    }
}
