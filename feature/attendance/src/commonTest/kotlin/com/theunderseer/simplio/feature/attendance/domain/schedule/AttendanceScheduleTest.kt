package com.theunderseer.simplio.feature.attendance.domain.schedule

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class AttendanceScheduleTest {
    @Test
    fun `monday is the latest scheduled class on monday`() {
        assertEquals(
            LocalDate(2026, 10, 5),
            latestScheduledClassDate(
                today = LocalDate(2026, 10, 5),
            ),
        )
    }

    @Test
    fun `monday is the latest scheduled class between monday and friday`() {
        assertEquals(
            LocalDate(2026, 10, 5),
            latestScheduledClassDate(
                today = LocalDate(2026, 10, 6),
            ),
        )

        assertEquals(
            LocalDate(2026, 10, 5),
            latestScheduledClassDate(
                today = LocalDate(2026, 10, 8),
            ),
        )
    }

    @Test
    fun `friday is the latest scheduled class on friday`() {
        assertEquals(
            LocalDate(2026, 10, 9),
            latestScheduledClassDate(
                today = LocalDate(2026, 10, 9),
            ),
        )
    }

    @Test
    fun `friday is the latest scheduled class during weekend`() {
        assertEquals(
            LocalDate(2026, 10, 9),
            latestScheduledClassDate(
                today = LocalDate(2026, 10, 10),
            ),
        )

        assertEquals(
            LocalDate(2026, 10, 9),
            latestScheduledClassDate(
                today = LocalDate(2026, 10, 11),
            ),
        )
    }
}
