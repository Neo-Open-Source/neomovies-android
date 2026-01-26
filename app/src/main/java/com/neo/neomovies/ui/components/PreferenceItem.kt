package com.neo.neomovies.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun PreferenceItem(
    title: String,
    description: String? = null,
    icon: ImageVector? = null,
    painter: Painter? = null,
    onClick: (() -> Unit)? = null,
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable { onClick() }
    } else {
        Modifier
    }

    ListItem(
        headlineContent = { Text(title) },
        supportingContent =
            if (!description.isNullOrBlank()) {
                { Text(description, style = MaterialTheme.typography.bodyMedium) }
            } else {
                null
            },
        leadingContent =
            if (icon != null) {
                { Icon(icon, contentDescription = null) }
            } else if (painter != null) {
                { Icon(painter, contentDescription = null) }
            } else {
                null
            },
        modifier =
            Modifier
                .fillMaxWidth()
                .then(clickableModifier)
    )
}
