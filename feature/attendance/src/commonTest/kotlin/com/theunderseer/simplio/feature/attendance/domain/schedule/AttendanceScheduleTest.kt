package com.theunderseer.simplio.feature.attendance.domain.schedule

import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.currentDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.secondClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.thirdClassDate
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class AttendanceScheduleTest {
    @Test
    fun `monday is the latest scheduled class on monday`() {
        assertEquals(
            secondClassDate,
            latestScheduledClassDate(
                today = secondClassDate,
            ),
        )
    }

    @Test
    fun `monday is the latest scheduled class between monday and friday`() {
        // Given: Tuesday, Wednesday and Thursday
        val dates = listOf(
            LocalDate(2026, 10, 6),
            LocalDate(2026, 10, 7),
            currentDate,
        )

        // When / Then
        dates.forEach { date ->
            assertEquals(
                secondClassDate,
                latestScheduledClassDate(today = date),
                message = "Expected Monday to be the latest class on $date",
            )
        }
    }

    @Test
    fun `friday is the latest scheduled class on friday`() {
        assertEquals(
            thirdClassDate,
            latestScheduledClassDate(
                today = thirdClassDate,
            ),
        )
    }

    @Test
    fun `friday is the latest scheduled class during weekend`() {
        // Given: Saturday and Sunday
        val dates = listOf(
            LocalDate(2026, 10, 10),
            LocalDate(2026, 10, 11),
        )

        // When / Then
        dates.forEach { date ->
            assertEquals(
                thirdClassDate,
                latestScheduledClassDate(today = date),
                message = "Expected Friday to be the latest class on $date",
            )
        }
    }
}
