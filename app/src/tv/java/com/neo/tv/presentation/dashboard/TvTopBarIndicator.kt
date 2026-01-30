package com.neo.tv.presentation.dashboard

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

@Composable
fun TvTopBarIndicator(
    anyTabFocused: Boolean,
) {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .alpha(if (anyTabFocused) 1f else 0f)
            .then(Modifier)
    )
}
