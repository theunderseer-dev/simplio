package com.theunderseer.simplio.feature.attendance.domain.schedule

import com.theunderseer.simplio.feature.attendance.domain.model.ClassSchedule
import com.theunderseer.simplio.feature.attendance.domain.model.ClassSession
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Each schedule keeps its own tracking window, including multiple slots on the same date. */
fun ClassSchedule.latestStartedClass(now: Instant): ClassSession? {
    val upper = minOf(
        now.toLocalDateTime(TimeZone.of(timeZoneId)).date,
        endsOn ?: now.toLocalDateTime(
            TimeZone.of(timeZoneId),
        ).date,
    )
    return (0..13).asSequence().flatMap { offset ->
        val date = upper.minus(DatePeriod(days = offset))
        sessionsBetween(date, date).asSequence()
    }.filter { it.startsAt <= now }.maxWithOrNull(compareBy<ClassSession> { it.startsAt }.thenBy { it.id })
}
