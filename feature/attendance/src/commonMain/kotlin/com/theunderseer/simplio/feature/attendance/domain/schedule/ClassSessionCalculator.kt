package com.theunderseer.simplio.feature.attendance.domain.schedule

import com.theunderseer.simplio.feature.attendance.domain.model.ClassSchedule
import com.theunderseer.simplio.feature.attendance.domain.model.ClassSession
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** Bounded projection, never an unbounded table of future events. Dates belong to the schedule's time zone. */
fun ClassSchedule.sessionsBetween(from: LocalDate, through: LocalDate): List<ClassSession> {
    require(from <= through)
    require(through.toEpochDays() - from.toEpochDays() <= 3660) { "Request at most ten years at once" }
    val first = maxOf(from, startsOn ?: from)
    val last = minOf(through, endsOn ?: through)
    if (first > last) return emptyList()
    val zone = TimeZone.of(timeZoneId)
    val sessions = mutableListOf<ClassSession>()
    var date = first
    while (true) {
        slots.filter { it.dayOfWeek == date.dayOfWeek }.forEach { slot ->
            val localStart = LocalDateTime(date, slot.startsAt)
            val localEnd = LocalDateTime(date, slot.endsAt)
            val start = localStart.toInstant(zone)
            val end = localEnd.toInstant(zone)
            // Nonexistent wall times during a DST gap do not silently become a different class time.
            // Overlaps use kotlinx-datetime's earlier offset consistently on every device.
            if (start.toLocalDateTime(zone) == localStart && end.toLocalDateTime(zone) == localEnd && start < end) {
                sessions += ClassSession(id, slot.id, date, start, end)
            }
        }
        if (date == last) break
        date = date.plus(DatePeriod(days = 1))
    }
    return sessions.sortedWith(compareBy<ClassSession> { it.startsAt }.thenBy { it.id })
}
