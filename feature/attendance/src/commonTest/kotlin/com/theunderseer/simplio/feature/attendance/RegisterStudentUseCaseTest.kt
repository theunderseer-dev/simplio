package com.theunderseer.simplio.feature.attendance

import com.theunderseer.simplio.attendance.domain.usecase.RegisterStudentUseCase
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentRepository
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class RegisterStudentUseCaseTest {
    @Test
    fun `registering a student with a package records first attendance automatically`() {
        // Given
        val studentRepository = FakeStudentRepository()
        val studentPackageRepository = FakeStudentPackageRepository()
        val studentName = "Anna Smith"

        // a 5-class package is available
        val packageType =
            PackageType(
                name = "5-class package",
                totalClasses = 5,
                validityWeeks = 6,
                price = 1700,
            )
        // and registration is happening today
        val registeredOn = LocalDate(2026, 9, 25)
        val registerStudentUseCase =
            RegisterStudentUseCase(
                studentRepository,
                studentPackageRepository,
            )

        // When
        // Anna Smith is registered with that package
        val result =
            registerStudentUseCase(
                fullName = studentName,
                packageType = packageType,
                registeredOn = registeredOn,
            )

        // Then
        // Anna is registered
        assertEquals(studentName, result.student.fullName)
        // And the package is associated with her
        assertEquals(
            result.student.id,
            result.studentPackage.studentId,
        )
        // And package type is the one selected
        assertEquals(packageType, result.studentPackage.packageType)
        // And package starts today
        assertEquals(registeredOn, result.studentPackage.startedOn)
        // And attendance contains registration date
        assertEquals(setOf(registeredOn), result.studentPackage.attendanceDates)
        // And the student is stored in the repository
        assertEquals(
            result.student,
            studentRepository.students.single(),
        )
    }
}
