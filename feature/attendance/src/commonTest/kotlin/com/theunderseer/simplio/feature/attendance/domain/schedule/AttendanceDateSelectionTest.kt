package com.theunderseer.simplio.feature.attendance.domain.schedule

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttendanceDateSelectionTest {
    @Test
    fun `allows past Mondays and Fridays`() {
        // Given
        val today = LocalDate(2026, 10, 8)
        val dates =
            listOf(
                LocalDate(2026, 10, 2),
                LocalDate(2026, 10, 5),
            )

        // When / Then
        dates.forEach { date ->
            assertTrue(
                isAttendanceDateSelectable(
                    date = date,
                    today = today,
                ),
                message = "Expected $date to be selectable on $today",
            )
        }
    }

    @Test
    fun `allows today when it is Monday or Friday`() {
        // Given
        val dates =
            listOf(
                LocalDate(2026, 10, 5),
                LocalDate(2026, 10, 9),
            )

        // When / Then
        dates.forEach { today ->
            assertTrue(
                isAttendanceDateSelectable(
                    date = today,
                    today = today,
                ),
                message = "Expected $today to be selectable on the same day",
            )
        }
    }

    @Test
    fun `rejects dates outside the class schedule`() {
        // Given
        val today = LocalDate(2026, 10, 9)
        val dates =
            listOf(
                LocalDate(2026, 10, 3),
                LocalDate(2026, 10, 4),
                LocalDate(2026, 10, 6),
                LocalDate(2026, 10, 7),
                LocalDate(2026, 10, 8),
            )

        // When / Then
        dates.forEach { date ->
            assertFalse(
                isAttendanceDateSelectable(
                    date = date,
                    today = today,
                ),
                message = "Expected $date to be unavailable on $today",
            )
        }
    }

    @Test
    fun `rejects future Mondays and Fridays`() {
        // Given
        val today = LocalDate(2026, 10, 9)
        val dates =
            listOf(
                LocalDate(2026, 10, 12),
                LocalDate(2026, 10, 16),
            )

        // When / Then
        dates.forEach { date ->
            assertFalse(
                isAttendanceDateSelectable(
                    date = date,
                    today = today,
                ),
                message = "Expected future date $date to be unavailable on $today",
            )
        }
    }
}
