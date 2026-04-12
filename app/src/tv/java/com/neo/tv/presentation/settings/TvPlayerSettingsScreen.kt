package com.neo.tv.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neo.neomovies.R
import com.neo.neomovies.ui.settings.PlayerEngineManager
import com.neo.neomovies.ui.settings.PlayerEngineMode
import com.neo.tv.presentation.common.TvScreenScaffold
import com.neo.tv.presentation.common.TvSelectableItem

@Composable
fun TvPlayerSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(PlayerEngineManager.getMode(context)) }

    fun select(newMode: PlayerEngineMode) {
        mode = newMode
        PlayerEngineManager.setMode(context, newMode)
    }

    TvScreenScaffold(
        title = stringResource(R.string.settings_player),
        onBack = onBack,
        contentPadding = PaddingValues(horizontal = 72.dp, vertical = 16.dp),
    ) { padding ->
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxWidth().padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TvSelectableItem(title = stringResource(R.string.settings_player_exoplayer), selected = mode == PlayerEngineMode.EXO, onSelect = { select(PlayerEngineMode.EXO) })
            TvSelectableItem(title = stringResource(R.string.settings_player_mpv_experimental), selected = mode == PlayerEngineMode.MPV, onSelect = { select(PlayerEngineMode.MPV) })
        }
    }
}
