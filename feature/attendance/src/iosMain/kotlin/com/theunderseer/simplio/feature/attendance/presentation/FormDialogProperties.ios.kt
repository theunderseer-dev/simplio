package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun formDialogProperties(usePlatformDefaultWidth: Boolean): DialogProperties =
    // Close the outgoing focusable scene immediately when switching between the form and its picker.
    DialogProperties(usePlatformDefaultWidth = usePlatformDefaultWidth, animateTransition = false)
