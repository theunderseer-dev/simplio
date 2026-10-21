package com.theunderseer.simplio.feature.attendance.fixtures

import com.theunderseer.simplio.feature.attendance.domain.model.ClassSchedule
import com.theunderseer.simplio.feature.attendance.domain.model.WeeklyClassSlot
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

internal fun testClassSchedule(
    id: String = "morning-classes",
    name: String = "Morning classes",
    timeZoneId: String = "Asia/Bangkok",
    startsOn: LocalDate? = LocalDate(2026, 10, 2),
    endsOn: LocalDate? = null,
    slots: List<WeeklyClassSlot> = listOf(
        WeeklyClassSlot("friday", DayOfWeek.FRIDAY, LocalTime(9, 0), LocalTime(10, 15)),
    ),
): ClassSchedule = ClassSchedule(id, name, timeZoneId, slots, startsOn, endsOn)
