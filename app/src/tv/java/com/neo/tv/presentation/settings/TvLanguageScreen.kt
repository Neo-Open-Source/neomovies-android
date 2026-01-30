package com.neo.tv.presentation.settings

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.neo.neomovies.R
import com.neo.neomovies.ui.settings.LanguageManager
import com.neo.neomovies.ui.settings.LanguageMode
import com.neo.tv.presentation.common.TvActionButton
import com.neo.tv.presentation.common.TvScreenScaffold

@Composable
fun TvLanguageScreen(
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var mode by remember { mutableStateOf(LanguageManager.getMode(context)) }

    fun select(newMode: LanguageMode) {
        mode = newMode
        LanguageManager.setMode(context, newMode)
        (context as? Activity)?.recreate()
    }

    TvScreenScaffold(
        title = stringResource(R.string.settings_language),
        onBack = onBack,
    ) { padding ->
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxWidth().padding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LanguageRow(
                title = stringResource(R.string.settings_language_system),
                selected = mode == LanguageMode.SYSTEM,
                onSelect = { select(LanguageMode.SYSTEM) },
            )
            LanguageRow(
                title = stringResource(R.string.settings_language_ru),
                selected = mode == LanguageMode.RU,
                onSelect = { select(LanguageMode.RU) },
            )
            LanguageRow(
                title = stringResource(R.string.settings_language_en),
                selected = mode == LanguageMode.EN,
                onSelect = { select(LanguageMode.EN) },
            )
        }
    }
}

@Composable
private fun LanguageRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    ListItem(
        modifier = Modifier.fillMaxWidth(),
        selected = selected,
        onClick = onSelect,
        headlineContent = { Text(text = title, style = MaterialTheme.typography.titleMedium) },
        trailingContent = {
            Text(
                text = if (selected) stringResource(R.string.action_selected) else stringResource(R.string.action_more),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
        ),
        shape = ListItemDefaults.shape(shape = MaterialTheme.shapes.medium),
    )
}
