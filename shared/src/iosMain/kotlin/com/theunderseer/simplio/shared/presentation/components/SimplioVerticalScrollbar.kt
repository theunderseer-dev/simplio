package com.theunderseer.simplio.shared.presentation.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The desktop scrollbar is a mouse control. Touch lists scroll through their own gestures. */
@Composable
actual fun SimplioVerticalScrollbar(state: LazyListState, modifier: Modifier) = Unit
