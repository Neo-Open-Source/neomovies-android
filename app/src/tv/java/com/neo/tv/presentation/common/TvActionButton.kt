package com.neo.tv.presentation.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

@Composable
fun TvActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
) {
    Surface(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = 120.dp),
        shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium),
        enabled = enabled,
        colors = ClickableSurfaceDefaults.colors(
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f, disabledScale = 1f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(contentPadding),
        )
    }
}
