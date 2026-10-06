package com.theunderseer.simplio.feature.attendance.domain.schedule

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class AttendanceCheckStateTest {
    @Test
    fun `attendance is pending when latest scheduled class is not recorded`() {
        val state =
            attendanceCheckState(
                attendanceDates =
                setOf(
                    LocalDate(2026, 9, 28),
                    LocalDate(2026, 10, 2),
                ),
                latestScheduledClassDate = LocalDate(2026, 10, 5),
            )

        assertEquals(
            AttendanceCheckState.Pending,
            state,
        )
    }

    @Test
    fun `attendance is checked when latest scheduled class is recorded`() {
        val state =
            attendanceCheckState(
                attendanceDates =
                setOf(
                    LocalDate(2026, 9, 28),
                    LocalDate(2026, 10, 2),
                    LocalDate(2026, 10, 5),
                ),
                latestScheduledClassDate = LocalDate(2026, 10, 5),
            )

        assertEquals(
            AttendanceCheckState.Checked,
            state,
        )
    }

    @Test
    fun `older missing classes do not prevent latest class from being checked`() {
        val state =
            attendanceCheckState(
                attendanceDates =
                setOf(
                    LocalDate(2026, 9, 28),
                    // Oct 2 was skipped
                    LocalDate(2026, 10, 5),
                ),
                latestScheduledClassDate = LocalDate(2026, 10, 5),
            )

        assertEquals(
            AttendanceCheckState.Checked,
            state,
        )
    }
}
