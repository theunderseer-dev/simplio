package com.theunderseer.simplio.feature.attendance.domain.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** A calculated occurrence; group membership is deliberately absent from its identity. */
data class ClassSession(
    val scheduleId: String,
    val slotId: String,
    val date: LocalDate,
    val startsAt: Instant,
    val endsAt: Instant,
) {
    val id: String get() = "$scheduleId:$slotId:${date.toEpochDays()}"
}
