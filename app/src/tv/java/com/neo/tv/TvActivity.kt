package com.neo.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import com.neo.neomovies.ui.settings.LanguageManager
import com.neo.tv.presentation.TvApp
import com.neo.tv.presentation.theme.NeoMoviesTvTheme

class TvActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        LanguageManager.apply(this)

        setContent {
            NeoMoviesTvTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    CompositionLocalProvider(
                        LocalContentColor provides MaterialTheme.colorScheme.onSurface
                    ) {
                        TvApp(
                            onBackPressed = onBackPressedDispatcher::onBackPressed,
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        LanguageManager.apply(this)
    }
}
