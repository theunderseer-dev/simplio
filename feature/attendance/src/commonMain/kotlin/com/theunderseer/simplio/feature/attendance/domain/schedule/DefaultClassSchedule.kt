package com.theunderseer.simplio.feature.attendance.domain.schedule

import com.theunderseer.simplio.feature.attendance.domain.model.ClassSchedule
import com.theunderseer.simplio.feature.attendance.domain.model.WeeklyClassSlot
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/** Compatibility template for accounts created before schedules were configurable. */
object DefaultClassSchedule {
    const val ID = "legacy-mon-fri"
    val schedule = ClassSchedule(
        id = ID,
        name = "Monday and Friday",
        timeZoneId = "Asia/Bangkok",
        slots = listOf(
            WeeklyClassSlot("monday", DayOfWeek.MONDAY, LocalTime(9, 0), LocalTime(10, 15)),
            WeeklyClassSlot("friday", DayOfWeek.FRIDAY, LocalTime(9, 0), LocalTime(10, 15)),
        ),
    )
}
