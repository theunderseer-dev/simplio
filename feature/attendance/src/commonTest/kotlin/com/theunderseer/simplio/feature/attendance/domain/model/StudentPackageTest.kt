package com.theunderseer.simplio.feature.attendance.domain.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudentPackageTest {
    @Test
    fun `used classes reflect visits in attendance history`() {
        // Given
        val startedOn = LocalDate(2026, 10, 5)
        val packageType =
            PackageType(
                id = 3,
                name = "Duo 8 · Special rate",
                totalClasses = 8,
                validityWeeks = 4,
                price = 2400,
            )

        val studentPackage =
            StudentPackage(
                id = 1,
                studentId = 7,
                packageType = packageType,
                startedOn = startedOn,
                attendanceHistory =
                AttendanceHistory(
                    visitsByDate =
                    mapOf(
                        startedOn to 2,
                        LocalDate(2026, 10, 9) to 1,
                    ),
                ),
            )

        // When
        val usedClasses = studentPackage.usedClasses

        // Then
        assertEquals(
            3,
            usedClasses,
        )
    }

    @Test
    fun `package becomes completed when visits reach class limit`() {
        // Given
        val startedOn = LocalDate(2026, 10, 5)
        val lastAttendance = LocalDate(2026, 10, 16)
        val packageType =
            PackageType(
                id = 3,
                name = "Duo 8 · Special rate",
                totalClasses = 8,
                validityWeeks = 4,
                price = 2400,
            )
        val studentPackage =
            StudentPackage(
                id = 1,
                studentId = 7,
                packageType = packageType,
                startedOn = startedOn,
                attendanceHistory =
                AttendanceHistory(
                    visitsByDate =
                    mapOf(
                        startedOn to 2,
                        LocalDate(2026, 10, 9) to 2,
                        LocalDate(2026, 10, 12) to 2,
                        lastAttendance to 1,
                    ),
                ),
            )

        // Then: seven visits do not complete the package
        assertFalse(studentPackage.isCompleted)

        // When: the history contains eight visits across four dates
        val completedPackage =
            studentPackage.copy(
                attendanceHistory =
                AttendanceHistory(
                    visitsByDate =
                    studentPackage.attendanceHistory.visitsByDate +
                        (lastAttendance to 2),
                ),
            )

        // Then
        assertTrue(completedPackage.isCompleted)
    }
}
