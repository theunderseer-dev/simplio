package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.ui.window.DialogProperties

internal actual fun formDialogProperties(usePlatformDefaultWidth: Boolean): DialogProperties =
    DialogProperties(usePlatformDefaultWidth = usePlatformDefaultWidth)
