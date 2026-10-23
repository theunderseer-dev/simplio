package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

internal val ColorScheme.warning: Color
    get() = if (surface.luminance() < 0.5f) {
        Color(0xFFFFCA5C)
    } else {
        Color(0xFF8A5700)
    }
