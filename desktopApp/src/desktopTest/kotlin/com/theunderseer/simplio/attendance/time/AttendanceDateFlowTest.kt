package com.theunderseer.simplio.attendance.time

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceDateFlowTest {
    @Test
    fun `updates attendance date at Bangkok midnight`() = runTest {
        // Given
        val initialInstant = Instant.parse("2026-10-08T16:59:59Z")
        val currentDate = LocalDate(2026, 10, 8)
        val nextDate = LocalDate(2026, 10, 9)

        val clock =
            object : Clock {
                override fun now(): Instant = initialInstant + testScheduler.currentTime.milliseconds
            }

        val emittedDates = mutableListOf<LocalDate>()

        // When
        backgroundScope.launch {
            attendanceDates(
                clock = clock,
            ).toList(emittedDates)
        }

        runCurrent()

        // Then: current date is available immediately
        assertEquals(
            listOf(currentDate),
            emittedDates,
        )

        // When: Bangkok midnight has not yet arrived
        advanceTimeBy(999.milliseconds)
        runCurrent()

        // Then: the date remains unchanged
        assertEquals(
            listOf(currentDate),
            emittedDates,
        )

        // When: Bangkok midnight arrives
        advanceTimeBy(1.milliseconds)
        runCurrent()

        // Then: the new date is emitted automatically
        assertEquals(
            listOf(currentDate, nextDate),
            emittedDates,
        )
    }
}
