package com.theunderseer.simplio.feature.attendance

import com.theunderseer.simplio.attendance.domain.usecase.MarkStudentPresentUseCase
import com.theunderseer.simplio.attendance.domain.usecase.result.MarkStudentPresentResult
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MarkStudentPresentUseCaseTest {
    @Test
    fun `marking a student as present adds the date to attendance`() {
        // Given
        // Anna has an active 5-class package
        // And
        // she has already attended one class
        val studentName = "Anna Smith"
        val packageType =
            PackageType(
                name = "5-class package",
                totalClasses = 5,
                validityWeeks = 6,
                price = 1700,
            )
        val firstAttendance = LocalDate(2026, 9, 25)
        val studentPackage =
            StudentPackage(
                student = Student(studentName),
                packageType = packageType,
                startedOn = firstAttendance,
                attendanceDates = setOf(firstAttendance),
            )

        // When
        // I mark Anna present on a new date
        val markStudentPresentUseCase = MarkStudentPresentUseCase()
        val nextAttendanceDate = LocalDate(2026, 9, 27)
        val result =
            markStudentPresentUseCase(
                studentPackage = studentPackage,
                attendanceDate = nextAttendanceDate,
            )

        // Then
        // the new attendance date is recorded
        // And
        // used classes increase from 1 to 2
        assertIs<MarkStudentPresentResult.Success>(result)
        assertEquals(
            setOf(firstAttendance, nextAttendanceDate),
            result.studentPackage.attendanceDates,
        )
        assertEquals(2, result.studentPackage.usedClasses)
    }

    @Test
    fun `marking a student as present on the same date does not duplicate attendance`() {
        // Given
        // Anna has an active 5-class package
        // And
        // she has already attended one class
        val studentName = "Anna Smith"
        val packageType =
            PackageType(
                name = "5-class package",
                totalClasses = 5,
                validityWeeks = 6,
                price = 1700,
            )
        val firstAttendance = LocalDate(2026, 9, 25)
        val studentPackage =
            StudentPackage(
                student = Student(studentName),
                packageType = packageType,
                startedOn = firstAttendance,
                attendanceDates = setOf(firstAttendance),
            )

        // When
        // I mark Anna present on the same date again
        val markStudentPresentUseCase = MarkStudentPresentUseCase()
        val result =
            markStudentPresentUseCase(
                studentPackage = studentPackage,
                attendanceDate = firstAttendance,
            )

        // Then
        // the attendance date is not duplicated
        // And
        // used classes remain at 1
        assertIs<MarkStudentPresentResult.Success>(result)
        assertEquals(setOf(firstAttendance), result.studentPackage.attendanceDates)
        assertEquals(1, result.studentPackage.usedClasses)
    }

    @Test
    fun `marking the final attendance marks the package as completed`() {
        // Given
        // Anna has a 5-class package
        // And
        // she has already used 4 classes
        val studentName = "Anna Smith"
        val packageType =
            PackageType(
                name = "5-class package",
                totalClasses = 5,
                validityWeeks = 6,
                price = 1700,
            )
        val studentPackage =
            StudentPackage(
                student = Student(studentName),
                packageType = packageType,
                startedOn = LocalDate(2026, 9, 20),
                attendanceDates =
                    setOf(
                        LocalDate(2026, 9, 20),
                        LocalDate(2026, 9, 22),
                        LocalDate(2026, 9, 24),
                        LocalDate(2026, 9, 26),
                    ),
            )

        val finalAttendanceDate = LocalDate(2026, 9, 28)

        // When
        // I mark Anna present on a new date
        val markStudentPresentUseCase = MarkStudentPresentUseCase()
        val result =
            markStudentPresentUseCase(
                studentPackage = studentPackage,
                attendanceDate = finalAttendanceDate,
            )

        // Then
        // the new attendance date is recorded
        assertIs<MarkStudentPresentResult.Success>(result)
        assertEquals(
            setOf(
                LocalDate(2026, 9, 20),
                LocalDate(2026, 9, 22),
                LocalDate(2026, 9, 24),
                LocalDate(2026, 9, 26),
                finalAttendanceDate,
            ),
            result.studentPackage.attendanceDates,
        )
        // And
        // used classes become 5 of 5
        assertEquals(5, result.studentPackage.usedClasses)
        // And
        // the package is marked as completed
        assertTrue(result.studentPackage.isCompleted)
        // And
        // Anna remains associated with the package history
        assertEquals(studentName, result.studentPackage.student.fullName)
    }

    @Test
    fun `marking attendance on a completed package is rejected`() {
        // Given
        // Anna has a completed 5-class package
        // And
        // all 5 classes have already been used
        val packageType =
            PackageType(
                name = "5-class package",
                totalClasses = 5,
                validityWeeks = 6,
                price = 1700,
            )

        val studentPackage =
            StudentPackage(
                student = Student("Anna Smith"),
                packageType = packageType,
                startedOn = LocalDate(2026, 9, 20),
                attendanceDates =
                    setOf(
                        LocalDate(2026, 9, 20),
                        LocalDate(2026, 9, 22),
                        LocalDate(2026, 9, 24),
                        LocalDate(2026, 9, 26),
                        LocalDate(2026, 9, 28),
                    ),
            )

        val nextAttendanceDate = LocalDate(2026, 9, 30)

        // When
        // I try to mark Anna present on another date
        val markStudentPresentUseCase = MarkStudentPresentUseCase()
        val result =
            markStudentPresentUseCase(
                studentPackage = studentPackage,
                attendanceDate = nextAttendanceDate,
            )

        // Then
        // the attendance must not be added
        assertIs<MarkStudentPresentResult.PackageCompleted>(result)
        assertFalse(result.studentPackage.attendanceDates.contains(nextAttendanceDate))
        // And
        // used classes must remain 5
        assertEquals(5, result.studentPackage.usedClasses)
        // And
        // the operation must report that the package is completed
        assertTrue(result.studentPackage.isCompleted)
    }
}
