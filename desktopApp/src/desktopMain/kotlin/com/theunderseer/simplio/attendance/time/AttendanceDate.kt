package com.theunderseer.simplio.attendance.time

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

private val attendanceTimeZone = TimeZone.of("Asia/Bangkok")

internal fun attendanceDateAt(instant: Instant): LocalDate = instant.toLocalDateTime(attendanceTimeZone).date

internal fun attendanceDates(clock: Clock): Flow<LocalDate> = flow {
    while (currentCoroutineContext().isActive) {
        val currentDate =
            attendanceDateAt(
                instant = clock.now(),
            )

        emit(currentDate)

        val nextDayStart =
            currentDate
                .plus(
                    value = 1,
                    unit = DateTimeUnit.DAY,
                ).atStartOfDayIn(attendanceTimeZone)

        delay(nextDayStart - clock.now())
    }
}.distinctUntilChanged()
