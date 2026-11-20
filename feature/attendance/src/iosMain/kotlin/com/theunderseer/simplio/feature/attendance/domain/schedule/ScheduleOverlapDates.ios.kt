package com.theunderseer.simplio.feature.attendance.domain.schedule

import com.theunderseer.simplio.feature.attendance.domain.model.ClassSchedule
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.timeZoneWithName
import kotlin.time.Instant

internal actual fun scheduleOverlapProbeDates(first: ClassSchedule, second: ClassSchedule): Sequence<LocalDate> =
    sequence {
        val firstZone = TimeZone.of(first.timeZoneId)
        val secondZone = TimeZone.of(second.timeZoneId)
        val zones = listOf(first, second).map { it.timeZoneId }.distinct().map { id ->
            checkNotNull(NSTimeZone.timeZoneWithName(id))
        }
        val historyStart = LocalDate(1800, 1, 1).atStartOfDayIn(TimeZone.UTC)
        val lower = maxOf(
            first.startsOn?.atStartOfDayIn(firstZone) ?: historyStart,
            second.startsOn?.atStartOfDayIn(secondZone) ?: historyStart,
        )
        // Probe through a complete Gregorian calendar cycle after modern time-zone rules are in effect.
        val cycleEnd = LocalDate(maxOf(2100, lower.toLocalDateTime(TimeZone.UTC).year + 1) + 400, 1, 1)
            .atStartOfDayIn(TimeZone.UTC)
        val upper = listOfNotNull(
            first.endsOn?.plus(DatePeriod(days = 1))?.atStartOfDayIn(firstZone),
            second.endsOn?.plus(DatePeriod(days = 1))?.atStartOfDayIn(secondZone),
            cycleEnd,
        ).min()
        yieldAll(
            scheduleProbeWindows(firstZone, lower, upper) { cursor ->
                val after = NSDate.dateWithTimeIntervalSince1970(cursor.epochSeconds.toDouble())
                zones.mapNotNull { zone ->
                    zone.nextDaylightSavingTimeTransitionAfterDate(after)?.timeIntervalSince1970?.toLong()
                        ?.let { Instant.fromEpochSeconds(it) }
                }.minOrNull()
            },
        )
    }
