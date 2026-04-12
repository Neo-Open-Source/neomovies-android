package com.neo.tv.presentation.settings

import android.app.Activity
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
import com.neo.neomovies.ui.settings.LanguageManager
import com.neo.neomovies.ui.settings.LanguageMode
import com.neo.tv.presentation.common.TvScreenScaffold
import com.neo.tv.presentation.common.TvSelectableItem

@Composable
fun TvLanguageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(LanguageManager.getMode(context)) }

    fun select(newMode: LanguageMode) {
        mode = newMode
        LanguageManager.setMode(context, newMode)
        (context as? Activity)?.recreate()
    }

    TvScreenScaffold(
        title = stringResource(R.string.settings_language),
        onBack = onBack,
        contentPadding = PaddingValues(horizontal = 72.dp, vertical = 16.dp),
    ) { padding ->
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxWidth().padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TvSelectableItem(title = stringResource(R.string.settings_language_system), selected = mode == LanguageMode.SYSTEM, onSelect = { select(LanguageMode.SYSTEM) })
            TvSelectableItem(title = stringResource(R.string.settings_language_ru), selected = mode == LanguageMode.RU, onSelect = { select(LanguageMode.RU) })
            TvSelectableItem(title = stringResource(R.string.settings_language_en), selected = mode == LanguageMode.EN, onSelect = { select(LanguageMode.EN) })
            TvSelectableItem(title = "Українська", selected = mode == LanguageMode.UK, onSelect = { select(LanguageMode.UK) })
            TvSelectableItem(title = "Беларуская", selected = mode == LanguageMode.BE, onSelect = { select(LanguageMode.BE) })
            TvSelectableItem(title = "Română", selected = mode == LanguageMode.RO, onSelect = { select(LanguageMode.RO) })
        }
    }
}
