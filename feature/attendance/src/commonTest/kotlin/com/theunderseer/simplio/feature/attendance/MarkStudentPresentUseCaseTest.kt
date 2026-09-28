package com.theunderseer.simplio.feature.attendance

import com.theunderseer.simplio.attendance.domain.usecase.MarkStudentPresentUseCase
import com.theunderseer.simplio.attendance.domain.usecase.result.MarkStudentPresentResult
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MarkStudentPresentUseCaseTest {
    private val studentId = 1
    private val packageType =
        PackageType(
            name = "5-class package",
            totalClasses = 5,
            validityWeeks = 6,
            price = 1700,
        )

    @Test
    fun `marking a student as present adds the date to attendance`() {
        // Given
        // Anna has an active 5-class package
        // And
        // she has already attended one class
        val firstAttendance = LocalDate(2026, 9, 25)
        val studentPackage =
            StudentPackage(
                studentId = studentId,
                packageType = packageType,
                startedOn = firstAttendance,
                attendanceDates = setOf(firstAttendance),
            )

        // When
        // I mark Anna present on a new date
        val nextAttendanceDate = LocalDate(2026, 9, 27)
        val markStudentPresentUseCase = MarkStudentPresentUseCase()
        val result =
            markStudentPresentUseCase(
                studentPackage = studentPackage,
                attendanceDate = nextAttendanceDate,
            )

        // Then
        val success = assertIs<MarkStudentPresentResult.Success>(result)
        // the new attendance date is recorded
        assertEquals(
            setOf(firstAttendance, nextAttendanceDate),
            success.studentPackage.attendanceDates,
        )
        // And
        // used classes increase from 1 to 2
        assertEquals(2, success.studentPackage.usedClasses)
    }

    @Test
    fun `marking a student as present on the same date does not duplicate attendance`() {
        // Given
        // Anna has an active 5-class package
        // And
        // she has already attended one class
        val firstAttendance = LocalDate(2026, 9, 25)
        val studentPackage =
            StudentPackage(
                studentId = studentId,
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
        val success = assertIs<MarkStudentPresentResult.Success>(result)
        // the attendance date is not duplicated
        assertEquals(setOf(firstAttendance), success.studentPackage.attendanceDates)
        // And
        // used classes remain at 1
        assertEquals(1, success.studentPackage.usedClasses)
    }

    @Test
    fun `marking the final attendance marks the package as completed`() {
        // Given
        // Anna has a 5-class package
        // And
        // she has already used 4 classes
        val studentPackage =
            StudentPackage(
                studentId = studentId,
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
        val success = assertIs<MarkStudentPresentResult.Success>(result)
        // the new attendance date is recorded
        assertEquals(
            setOf(
                LocalDate(2026, 9, 20),
                LocalDate(2026, 9, 22),
                LocalDate(2026, 9, 24),
                LocalDate(2026, 9, 26),
                finalAttendanceDate,
            ),
            success.studentPackage.attendanceDates,
        )
        // And
        // used classes become 5 of 5
        assertEquals(5, success.studentPackage.usedClasses)
        // And
        // the package is marked as completed
        assertTrue(success.studentPackage.isCompleted)
        // And
        // Anna remains associated with the package history
        assertEquals(studentId, success.studentPackage.studentId)
    }

    @Test
    fun `marking attendance on a completed package is rejected`() {
        // Given
        // Anna has a completed 5-class package
        // And
        // all 5 classes have already been used
        val studentPackage =
            StudentPackage(
                studentId = studentId,
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
        // the operation reports that the package is completed
        val completedResult = assertIs<MarkStudentPresentResult.PackageCompleted>(result)
        // And
        // the attendance must not be added
        assertFalse(completedResult.studentPackage.attendanceDates.contains(nextAttendanceDate))
        // And
        // used classes must remain 5
        assertEquals(5, completedResult.studentPackage.usedClasses)
        // And
        // the operation must report that the package is completed
        assertTrue(completedResult.studentPackage.isCompleted)
    }

    @Test
    fun `marking attendance on an expired package is rejected`() {
        // Given
        // Anna has a 5-class package
        // And
        // the package still has unused classes
        // And
        // the package has expired
        val studentPackage =
            StudentPackage(
                studentId = studentId,
                packageType = packageType,
                startedOn = LocalDate(2026, 8, 1),
                attendanceDates =
                    setOf(
                        LocalDate(2026, 8, 1),
                        LocalDate(2026, 8, 8),
                    ),
            )

        val attendanceDate = LocalDate(2026, 9, 20)

        // When
        // I try to mark Anna present on a later date
        val markStudentPresentUseCase = MarkStudentPresentUseCase()
        val result =
            markStudentPresentUseCase(
                studentPackage = studentPackage,
                attendanceDate = attendanceDate,
            )

        // Then
        // the package is already expired
        val expiredResult = assertIs<MarkStudentPresentResult.PackageExpired>(result)
        // the attendance must not be added
        assertFalse(expiredResult.studentPackage.attendanceDates.contains(attendanceDate))
        // And
        // used classes must remain unchanged
        assertEquals(2, expiredResult.studentPackage.usedClasses)
    }
}
