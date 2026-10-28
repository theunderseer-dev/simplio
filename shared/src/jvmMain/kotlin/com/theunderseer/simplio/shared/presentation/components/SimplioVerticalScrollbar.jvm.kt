package com.theunderseer.simplio.shared.presentation.components

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
actual fun SimplioVerticalScrollbar(state: LazyListState, modifier: Modifier) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(state),
        modifier = modifier,
        style = defaultScrollbarStyle().copy(
            thickness = 6.dp,
            unhoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            hoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        ),
    )
}
