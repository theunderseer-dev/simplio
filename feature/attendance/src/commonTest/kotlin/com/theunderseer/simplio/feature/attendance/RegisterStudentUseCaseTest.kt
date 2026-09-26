package com.theunderseer.simplio.feature.attendance

import com.theunderseer.simplio.attendance.domain.usecase.RegisterStudentUseCase
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class RegisterStudentUseCaseTest {
    @Test
    fun `registering a student with a package records first attendance automatically`() {
        // Given
        // a 5-class package is available
        val studentName = "Anna Smith"
        val packageType =
            PackageType(
                name = "5-class package",
                totalClasses = 5,
                validityWeeks = 6,
                price = 1700,
            )
        // and registration is happening today
        val today = LocalDate(2026, 9, 25)
        val registerStudentUseCase = RegisterStudentUseCase()

        // When
        // Anna Smith is registered with that package
        val result =
            registerStudentUseCase(
                fullName = studentName,
                packageType = packageType,
                registeredOn = today,
            )

        // Then
        // Anna is registered
        assertEquals(studentName, result.student.fullName)
        // package starts today
        assertEquals(today, result.startedOn)
        // attendance contains today
        assertEquals(setOf(today), result.attendanceDates)
        // used classes = 1 of 5
        assertEquals(1, result.usedClasses)
        // package type is the one selected
        assertEquals(packageType, result.packageType)
        // total classes = 5
        assertEquals(5, result.packageType.totalClasses)
        // package expires in 6 weeks
        assertEquals(
            LocalDate(2026, 11, 6),
            result.expiresOn,
        )
    }
}
