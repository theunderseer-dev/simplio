package com.theunderseer.simplio.feature.attendance.domain.schedule

import com.theunderseer.simplio.feature.attendance.domain.model.ClassSchedule
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.LocalDate as JavaDate
import kotlin.time.Instant as KotlinInstant

internal actual fun scheduleOverlapProbeDates(first: ClassSchedule, second: ClassSchedule): Sequence<LocalDate> =
    sequence {
        val firstZone = ZoneId.of(first.timeZoneId)
        val secondZone = ZoneId.of(second.timeZoneId)
        val zones = listOf(firstZone, secondZone).distinct()
        val defined = zones.flatMap { it.rules.transitions }
        // Before the first known transition offsets are fixed: one complete week represents the unbounded past.
        val boundYears = listOfNotNull(first.startsOn, first.endsOn, second.startsOn, second.endsOn).map { it.year }
        val fallbackYear = ((defined.map { it.dateTimeBefore.year } + boundYears).minOrNull() ?: 2000) - 1
        val fallback = JavaDate.of(fallbackYear, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
        fun ClassSchedule.begin(zone: ZoneId): Instant = startsOn?.let {
            JavaDate.ofEpochDay(it.toEpochDays()).atStartOfDay(zone).toInstant()
        } ?: fallback
        fun ClassSchedule.end(zone: ZoneId): Instant? = endsOn?.let {
            JavaDate.ofEpochDay(it.toEpochDays()).plusDays(1).atStartOfDay(zone).toInstant()
        }
        val lower = maxOf(first.begin(firstZone), second.begin(secondZone))
        val lastDefined = defined.maxOfOrNull { it.instant } ?: lower
        val repeatingYear = maxOf(lower.atZone(ZoneOffset.UTC).year + 1, lastDefined.atZone(ZoneOffset.UTC).year + 1)
        // Beyond fully defined transitions, ZoneRules use annual Gregorian transition rules.
        // Their calendar repeats after 400 years; this is a complete cycle, not a rolling search horizon.
        val completeCycleEnd = if (zones.any { it.rules.transitionRules.isNotEmpty() }) {
            JavaDate.of(repeatingYear + 400, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
        } else {
            maxOf(lower, lastDefined).plusSeconds(15L * 86400)
        }
        val upper = listOfNotNull(first.end(firstZone), second.end(secondZone), completeCycleEnd).min()
        yieldAll(
            scheduleProbeWindows(
                TimeZone.of(first.timeZoneId),
                lower.toKotlinInstant(),
                upper.toKotlinInstant(),
            ) { cursor ->
                val after = Instant.ofEpochSecond(cursor.epochSeconds, cursor.nanosecondsOfSecond.toLong())
                zones.mapNotNull { it.rules.nextTransition(after)?.instant }.minOrNull()?.toKotlinInstant()
            },
        )
    }

private fun Instant.toKotlinInstant(): KotlinInstant = KotlinInstant.fromEpochSeconds(epochSecond, nano)
