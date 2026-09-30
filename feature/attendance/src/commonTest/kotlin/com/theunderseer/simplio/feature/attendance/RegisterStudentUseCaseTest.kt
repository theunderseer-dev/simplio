package com.theunderseer.simplio.feature.attendance

import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.usecase.RegisterStudentUseCase
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeTransactionRunner
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class RegisterStudentUseCaseTest {
    @Test
    fun `registering a student with a package records first attendance automatically`() =
        runTest {
            // Given
            // a new student is about to be registered
            val studentRepository = FakeStudentRepository()
            val studentPackageRepository = FakeStudentPackageRepository()
            val studentName = "Anna Smith"

            // And
            // a 5-class package is available
            val packageType =
                PackageType(
                    name = "5-class package",
                    totalClasses = 5,
                    validityWeeks = 6,
                    price = 1700,
                )

            // And
            // registration is happening on a specific date
            val registeredOn = LocalDate(2026, 9, 25)
            val registerStudent =
                RegisterStudentUseCase(
                    studentRepository,
                    studentPackageRepository,
                    FakeTransactionRunner(),
                )

            // When
            // the student is registering with the chosen package
            val result =
                registerStudent(
                    fullName = studentName,
                    packageType = packageType,
                    registeredOn = registeredOn,
                )

            // Then
            // the registered student has the expected name
            assertEquals(studentName, result.student.fullName)

            // And
            // the package belongs to the registered student
            assertEquals(
                result.student.id,
                result.studentPackage.studentId,
            )

            // And
            // the selected package type is assigned
            assertEquals(packageType, result.studentPackage.packageType)

            // And
            // the package starts on the registration date
            assertEquals(registeredOn, result.studentPackage.startedOn)

            // And
            // the first attendance is recorded automatically
            assertEquals(setOf(registeredOn), result.studentPackage.attendanceDates)

            // And
            // the student is persisted
            assertEquals(
                result.student,
                studentRepository.students.single(),
            )

            // And
            // the package is persisted
            assertEquals(
                result.studentPackage,
                studentPackageRepository.getLatestPackageForStudent(
                    result.student.id,
                ),
            )
        }
}
