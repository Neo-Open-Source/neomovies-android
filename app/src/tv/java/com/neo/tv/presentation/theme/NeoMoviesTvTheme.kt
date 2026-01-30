package com.neo.tv.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.neo.neomovies.R

private val tvColorScheme @Composable get() = darkColorScheme(
    primary = colorResource(R.color.tv_primary),
    onPrimary = colorResource(R.color.tv_onPrimary),
    primaryContainer = colorResource(R.color.tv_primaryContainer),
    onPrimaryContainer = colorResource(R.color.tv_onPrimaryContainer),
    secondary = colorResource(R.color.tv_secondary),
    onSecondary = colorResource(R.color.tv_onSecondary),
    secondaryContainer = colorResource(R.color.tv_secondaryContainer),
    onSecondaryContainer = colorResource(R.color.tv_onSecondaryContainer),
    tertiary = colorResource(R.color.tv_tertiary),
    onTertiary = colorResource(R.color.tv_onTertiary),
    tertiaryContainer = colorResource(R.color.tv_tertiaryContainer),
    onTertiaryContainer = colorResource(R.color.tv_onTertiaryContainer),
    background = colorResource(R.color.tv_background),
    onBackground = colorResource(R.color.tv_onSurface),
    surface = colorResource(R.color.tv_surface),
    onSurface = colorResource(R.color.tv_onSurface),
    surfaceVariant = colorResource(R.color.tv_surfaceVariant),
    onSurfaceVariant = colorResource(R.color.tv_onSurfaceVariant),
    error = colorResource(R.color.tv_error),
    onError = colorResource(R.color.tv_onError),
    errorContainer = colorResource(R.color.tv_errorContainer),
    onErrorContainer = colorResource(R.color.tv_onErrorContainer),
    border = colorResource(R.color.tv_border),
)

@Composable
fun NeoMoviesTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = tvColorScheme,
        content = content,
    )
}
