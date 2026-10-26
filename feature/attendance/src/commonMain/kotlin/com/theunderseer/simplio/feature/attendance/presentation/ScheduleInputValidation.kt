package com.theunderseer.simplio.feature.attendance.presentation

import kotlinx.datetime.LocalTime

internal fun parseScheduleTime(text: String): LocalTime? =
    if (text.length == 5 && text[2] == ':' && text.filterIndexed { index, _ -> index != 2 }.all { it in '0'..'9' }) {
        runCatching { LocalTime(text.take(2).toInt(), text.takeLast(2).toInt()) }.getOrNull()
    } else {
        null
    }
