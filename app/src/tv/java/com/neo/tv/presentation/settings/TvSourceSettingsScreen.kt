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
import com.neo.neomovies.ui.settings.SourceManager
import com.neo.neomovies.ui.settings.SourceMode
import com.neo.tv.presentation.common.TvScreenScaffold
import com.neo.tv.presentation.common.TvSelectableItem

@Composable
fun TvSourceSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(SourceManager.getMode(context)) }

    fun select(newMode: SourceMode) {
        mode = newMode
        SourceManager.setMode(context, newMode)
    }

    TvScreenScaffold(
        title = stringResource(R.string.settings_source),
        onBack = onBack,
        contentPadding = PaddingValues(horizontal = 72.dp, vertical = 16.dp),
    ) { padding ->
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxWidth().padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TvSelectableItem(title = stringResource(R.string.settings_source_collaps), selected = mode == SourceMode.COLLAPS, onSelect = { select(SourceMode.COLLAPS) })
            TvSelectableItem(title = stringResource(R.string.settings_source_torrents), selected = mode == SourceMode.TORRENTS, onSelect = { select(SourceMode.TORRENTS) })
            TvSelectableItem(title = stringResource(R.string.settings_source_alloha), selected = mode == SourceMode.ALLOHA, onSelect = { select(SourceMode.ALLOHA) })
        }
    }
}
