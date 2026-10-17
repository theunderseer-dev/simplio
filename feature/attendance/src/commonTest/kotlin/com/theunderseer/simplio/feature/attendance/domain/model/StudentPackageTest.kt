package com.theunderseer.simplio.feature.attendance.domain.model

import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.duoSpecialPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.firstClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.fourClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.fourthClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.registrationDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.secondClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.thirdClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentPackage
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StudentPackageTest {
    @Test
    fun `used classes reflect visits in package attendance history`() {
        // Given
        val studentPackage = createStudentPackage(
            visitsByDate = mapOf(
                firstClassDate to 2,
                secondClassDate to 1,
            ),
        )

        // When
        val usedClasses = studentPackage.usedClasses

        // Then
        assertEquals(3, usedClasses)
    }

    @Test
    fun `package becomes completed when visits reach class limit`() {
        // Given
        val studentPackage = createStudentPackage(
            visitsByDate = mapOf(
                firstClassDate to 2,
                secondClassDate to 2,
                thirdClassDate to 2,
                fourthClassDate to 1,
            ),
        )

        // Then: seven visits do not complete the package
        assertFalse(studentPackage.isCompleted)

        // When: the history contains eight visits across four dates
        val completedPackage = studentPackage.copy(
            attendanceHistory = PackageAttendanceHistory(
                visitsByDate =
                studentPackage.attendanceHistory.visitsByDate +
                    (fourthClassDate to 2),
            ),
        )

        // Then
        assertTrue(completedPackage.isCompleted)
    }

    @Test
    fun `duo package allows only one visit when one class remains`() {
        // Given
        val studentPackage = createStudentPackage(
            visitsByDate = mapOf(
                firstClassDate to 2,
                secondClassDate to 2,
                thirdClassDate to 2,
                fourthClassDate to 1,
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
        val studentPackage = createStudentPackage(
            visitsByDate = mapOf(
                firstClassDate to 2,
                secondClassDate to 2,
                thirdClassDate to 2,
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
        val studentPackage = createStudentPackage(
            visitsByDate = mapOf(
                firstClassDate to 2,
                secondClassDate to 2,
                thirdClassDate to 2,
                fourthClassDate to 2,
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
        val studentPackage = createStudentPackage(
            packageType = fourClassPackage,
        )

        // Then
        assertEquals(3, studentPackage.remainingClasses)
        assertTrue(studentPackage.allowsVisitCount(1))
        assertFalse(studentPackage.allowsVisitCount(2))
    }

    @Test
    fun `purchased package without attendance has full balance and no expiration`() {
        // Given
        val studentPackage = testStudentPackage(
            startedOn = null,
        )

        // Then
        assertEquals(registrationDate, studentPackage.purchasedOn)
        assertNull(studentPackage.startedOn)
        assertNull(studentPackage.expiresOn)
        assertTrue(studentPackage.attendanceDates.isEmpty())
        assertEquals(0, studentPackage.usedClasses)
        assertEquals(4, studentPackage.remainingClasses)
    }

    @Test
    fun `package validity starts from activation rather than purchase`() {
        // Given
        val studentPackage = testStudentPackage(
            purchasedOn = registrationDate,
            startedOn = secondClassDate,
        )

        // Then
        assertEquals(
            LocalDate(2026, 11, 2),
            studentPackage.expiresOn,
        )
    }

    private fun createStudentPackage(
        packageType: PackageType = duoSpecialPackage,
        visitsByDate: Map<LocalDate, Int> = mapOf(firstClassDate to 1),
    ): StudentPackage = testStudentPackage(
        packageType = packageType,
        visitsByDate = visitsByDate,
    )
}
