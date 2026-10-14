package com.theunderseer.simplio.feature.attendance.domain.model

import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.firstClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.secondClassDate
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
                    firstClassDate to 2,
                    secondClassDate to 1,
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
                    firstClassDate to 2,
                    secondClassDate to 0,
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
                    firstClassDate to 2,
                    secondClassDate to -1,
                ),
            )
        }
    }

    @Test
    fun `preserves package attendance history when source map changes`() {
        // Given
        val source =
            mutableMapOf(
                firstClassDate to 2,
                secondClassDate to 1,
            )

        val history =
            PackageAttendanceHistory(
                visitsByDate = source,
            )

        // When
        source[firstClassDate] = 0

        // Then
        assertEquals(
            mapOf(
                firstClassDate to 2,
                secondClassDate to 1,
            ),
            history.visitsByDate,
        )

        assertEquals(
            3,
            history.usedClasses,
        )
    }
}
