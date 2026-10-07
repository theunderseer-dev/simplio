package com.theunderseer.simplio.attendance.time

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class AttendanceDateTest {
    @Test
    fun `returns current attendance date before Bangkok midnight`() {
        // Given
        val instant = Instant.parse("2026-10-08T16:59:59Z")
        val expectedDate = LocalDate(2026, 10, 8)

        // When
        val actualDate = attendanceDateAt(
            instant = instant,
        )

        // Then
        assertEquals(
            expectedDate,
            actualDate,
        )
    }

    @Test
    fun `returns next attendance date at Bangkok midnight`() {
        // Given
        val instant = Instant.parse("2026-10-08T17:00:00Z")
        val expectedDate = LocalDate(2026, 10, 9)

        // When
        val actualDate = attendanceDateAt(
            instant = instant,
        )

        // Then
        assertEquals(
            expectedDate,
            actualDate,
        )
    }
}
