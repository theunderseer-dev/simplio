package com.theunderseer.simplio.feature.attendance.domain.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudentPackageTest {
    private val duoPackage =
        PackageType(
            id = 3,
            name = "Duo 8 · Special rate",
            totalClasses = 8,
            validityWeeks = 4,
            price = 2400,
            maxVisitsPerAttendance = 2,
        )

    @Test
    fun `used classes reflect visits in package attendance history`() {
        // Given
        val startedOn = LocalDate(2026, 10, 5)

        val studentPackage =
            StudentPackage(
                id = 1,
                studentId = 7,
                packageType = duoPackage,
                startedOn = startedOn,
                attendanceHistory =
                PackageAttendanceHistory(
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
        val studentPackage =
            StudentPackage(
                id = 1,
                studentId = 7,
                packageType = duoPackage,
                startedOn = startedOn,
                attendanceHistory =
                PackageAttendanceHistory(
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
                PackageAttendanceHistory(
                    visitsByDate =
                    studentPackage.attendanceHistory.visitsByDate +
                        (lastAttendance to 2),
                ),
            )

        // Then
        assertTrue(completedPackage.isCompleted)
    }

    @Test
    fun `duo package allows only one visit when one class remains`() {
        // Given
        val startedOn = LocalDate(2026, 10, 5)
        val studentPackage =
            StudentPackage(
                id = 1,
                studentId = 7,
                packageType = duoPackage,
                startedOn = startedOn,
                attendanceHistory =
                PackageAttendanceHistory(
                    visitsByDate =
                    mapOf(
                        startedOn to 2,
                        LocalDate(2026, 10, 9) to 2,
                        LocalDate(2026, 10, 12) to 2,
                        LocalDate(2026, 10, 16) to 1,
                    ),
                ),
            )

        // Then
        assertEquals(1, studentPackage.remainingClasses)
        assertTrue(studentPackage.allowsVisitCount(1))
        assertFalse(studentPackage.allowsVisitCount(2))
    }

    @Test
    fun `duo package allows one or two visits when two classes remain`() {
        // Given
        val startedOn = LocalDate(2026, 10, 5)
        val studentPackage =
            StudentPackage(
                id = 1,
                studentId = 7,
                packageType = duoPackage,
                startedOn = startedOn,
                attendanceHistory =
                PackageAttendanceHistory(
                    visitsByDate =
                    mapOf(
                        startedOn to 2,
                        LocalDate(2026, 10, 9) to 2,
                        LocalDate(2026, 10, 12) to 2,
                    ),
                ),
            )

        // Then
        assertEquals(2, studentPackage.remainingClasses)
        assertTrue(studentPackage.allowsVisitCount(1))
        assertTrue(studentPackage.allowsVisitCount(2))
    }

    @Test
    fun `completed package does not allow further visits`() {
        // Given
        val startedOn = LocalDate(2026, 10, 5)
        val studentPackage =
            StudentPackage(
                id = 1,
                studentId = 7,
                packageType = duoPackage,
                startedOn = startedOn,
                attendanceHistory =
                PackageAttendanceHistory(
                    visitsByDate =
                    mapOf(
                        startedOn to 2,
                        LocalDate(2026, 10, 9) to 2,
                        LocalDate(2026, 10, 12) to 2,
                        LocalDate(2026, 10, 16) to 2,
                    ),
                ),
            )

        // Then
        assertEquals(0, studentPackage.remainingClasses)
        assertFalse(studentPackage.allowsVisitCount(1))
        assertFalse(studentPackage.allowsVisitCount(2))
        assertFalse(studentPackage.allowsVisitCount(0))
    }

    @Test
    fun `individual package rejects two visits even when enough classes remain`() {
        // Given
        val packageType =
            PackageType(
                id = 1,
                name = "4-class package",
                totalClasses = 4,
                validityWeeks = 4,
                price = 1400,
            )
        val studentPackage =
            StudentPackage(
                id = 1,
                studentId = 7,
                packageType = packageType,
                startedOn = LocalDate(2026, 10, 5),
            )

        // Then
        assertEquals(3, studentPackage.remainingClasses)
        assertTrue(studentPackage.allowsVisitCount(1))
        assertFalse(studentPackage.allowsVisitCount(2))
    }
}
