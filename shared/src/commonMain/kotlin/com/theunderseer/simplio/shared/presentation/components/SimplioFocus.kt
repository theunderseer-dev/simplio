package com.theunderseer.simplio.shared.presentation.components

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager

/** Controls focus styling only; the actual focus and traversal order remain owned by Compose. */
@Composable
fun InteractionSource.collectIsKeyboardFocusedAsState(): State<Boolean> {
    val focused by collectIsFocusedAsState()
    val inputMode = LocalInputModeManager.current.inputMode
    return rememberUpdatedState(focused && inputMode == InputMode.Keyboard)
}
