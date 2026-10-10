package com.theunderseer.simplio.feature.attendance.domain.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PackageAttendanceHistoryTest {
    @Test
    fun `used classes count visits rather than attendance dates`() {
        // Given
        val history =
            PackageAttendanceHistory(
                visitsByDate =
                mapOf(
                    LocalDate(2026, 10, 5) to 2,
                    LocalDate(2026, 10, 9) to 1,
                ),
            )

        // When
        val usedClasses = history.usedClasses

        // Then
        assertEquals(
            3,
            usedClasses,
        )
    }

    @Test
    fun `rejects zero visits for an attendance date`() {
        // When / Then
        assertFailsWith<IllegalArgumentException> {
            PackageAttendanceHistory(
                visitsByDate =
                mapOf(
                    LocalDate(2026, 10, 5) to 2,
                    LocalDate(2026, 10, 9) to 0,
                ),
            )
        }
    }

    @Test
    fun `rejects negative visits for an attendance date`() {
        // When / Then
        assertFailsWith<IllegalArgumentException> {
            PackageAttendanceHistory(
                visitsByDate =
                mapOf(
                    LocalDate(2026, 10, 5) to 2,
                    LocalDate(2026, 10, 9) to -1,
                ),
            )
        }
    }

    @Test
    fun `preserves package attendance history when source map changes`() {
        // Given
        val firstDate = LocalDate(2026, 10, 5)
        val secondDate = LocalDate(2026, 10, 9)
        val source =
            mutableMapOf(
                firstDate to 2,
                secondDate to 1,
            )

        val history =
            PackageAttendanceHistory(
                visitsByDate = source,
            )

        // When
        source[firstDate] = 0

        // Then
        assertEquals(
            mapOf(
                firstDate to 2,
                secondDate to 1,
            ),
            history.visitsByDate,
        )

        assertEquals(
            3,
            history.usedClasses,
        )
    }
}
