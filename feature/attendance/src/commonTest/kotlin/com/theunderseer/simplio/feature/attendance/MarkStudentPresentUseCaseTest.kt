package com.theunderseer.simplio.feature.attendance

import com.theunderseer.simplio.attendance.domain.usecase.MarkStudentPresentUseCase
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

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
        val updatedStudentPackage =
            markStudentPresentUseCase(
                studentPackage = studentPackage,
                attendanceDate = nextAttendanceDate,
            )

        // Then
        // the new attendance date is recorded
        // And
        // used classes increase from 1 to 2
        assertEquals(
            setOf(firstAttendance, nextAttendanceDate),
            updatedStudentPackage.attendanceDates,
        )
        assertEquals(2, updatedStudentPackage.usedClasses)
    }
}
