package com.theunderseer.simplio.shared.presentation.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Desktop scrollbar kept behind a platform boundary for common screen code. */
@Composable
expect fun SimplioVerticalScrollbar(state: LazyListState, modifier: Modifier = Modifier)
