package com.theunderseer.simplio.feature.attendance.domain.schedule

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

fun latestScheduledClassDate(today: LocalDate): LocalDate = when (today.dayOfWeek) {
    DayOfWeek.MONDAY -> today

    DayOfWeek.TUESDAY ->
        today.minus(
            DatePeriod(days = 1),
        )

    DayOfWeek.WEDNESDAY ->
        today.minus(
            DatePeriod(days = 2),
        )

    DayOfWeek.THURSDAY ->
        today.minus(
            DatePeriod(days = 3),
        )

    DayOfWeek.FRIDAY -> today

    DayOfWeek.SATURDAY ->
        today.minus(
            DatePeriod(days = 1),
        )

    DayOfWeek.SUNDAY ->
        today.minus(
            DatePeriod(days = 2),
        )
}
