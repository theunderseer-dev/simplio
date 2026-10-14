package com.theunderseer.simplio.feature.attendance.domain.schedule

import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.currentDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.firstClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.secondClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.thirdClassDate
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttendanceDateSelectionTest {
    @Test
    fun `allows past Mondays and Fridays`() {
        // Given
        val dates = listOf(
            firstClassDate,
            secondClassDate,
        )

        // When / Then
        dates.forEach { date ->
            assertTrue(
                isAttendanceDateSelectable(
                    date = date,
                    today = currentDate,
                ),
                message = "Expected $date to be selectable on $currentDate",
            )
        }
    }

    @Test
    fun `allows today when it is Monday or Friday`() {
        // Given
        val dates = listOf(
            secondClassDate,
            thirdClassDate,
        )

        // When / Then
        dates.forEach { date ->
            assertTrue(
                isAttendanceDateSelectable(
                    date = date,
                    today = date,
                ),
                message = "Expected $date to be selectable on the same day",
            )
        }
    }

    @Test
    fun `rejects dates outside the class schedule`() {
        // Given
        val dates = listOf(
            LocalDate(2026, 10, 3),
            LocalDate(2026, 10, 4),
            LocalDate(2026, 10, 6),
            LocalDate(2026, 10, 7),
            currentDate,
        )

        // When / Then
        dates.forEach { date ->
            assertFalse(
                isAttendanceDateSelectable(
                    date = date,
                    today = thirdClassDate,
                ),
                message = "Expected $date to be unavailable on $thirdClassDate",
            )
        }
    }

    @Test
    fun `rejects future Mondays and Fridays`() {
        // Given
        val dates = listOf(
            LocalDate(2026, 10, 12),
            LocalDate(2026, 10, 16),
        )

        // When / Then
        dates.forEach { date ->
            assertFalse(
                isAttendanceDateSelectable(
                    date = date,
                    today = thirdClassDate,
                ),
                message = "Expected future date $date to be unavailable on $thirdClassDate",
            )
        }
    }
}
