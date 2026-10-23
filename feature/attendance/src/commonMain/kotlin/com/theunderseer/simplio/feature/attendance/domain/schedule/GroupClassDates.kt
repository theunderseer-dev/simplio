package com.theunderseer.simplio.feature.attendance.domain.schedule

import com.theunderseer.simplio.feature.attendance.domain.model.ClassSchedule
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

fun List<ClassSchedule>.isScheduledClassDate(date: LocalDate): Boolean = any {
    it.sessionsBetween(date, date).isNotEmpty()
}

fun List<ClassSchedule>.latestClassDate(today: LocalDate): LocalDate? = mapNotNull { schedule ->
    val upper = minOf(today, schedule.endsOn ?: today)
    (0..13).map { upper.minus(DatePeriod(days = it)) }
        .firstOrNull { listOf(schedule).isScheduledClassDate(it) }
}.maxOrNull()

// A package can be entered retrospectively, before the schedule was created.
// Keep the end boundary and time-zone/DST rules; persistence extends the start when saving.
fun List<ClassSchedule>.isRegistrationClassDate(date: LocalDate): Boolean =
    map { it.copy(startsOn = null) }.isScheduledClassDate(date)

fun List<ClassSchedule>.latestRegistrationClassDate(today: LocalDate): LocalDate? =
    map { it.copy(startsOn = null) }.latestClassDate(today)
