package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeTransactionRunner
import com.theunderseer.simplio.feature.attendance.fixtures.testClassPackage
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RegisterStudentUseCaseTest {
    @Test
    fun `registering a student with a package records first attendance automatically`() = runTest {
        // Given
        // a new student is about to be registered
        val studentRepository = FakeStudentRepository()
        val studentPackageRepository = FakeStudentPackageRepository()
        val studentName = "Anna Smith"

        // And
        // a 4-class package is available
        val packageType = testClassPackage()

        // And
        // registration is happening on a specific date
        val registeredOn = LocalDate(2026, 9, 25)
        val transactionRunner = FakeTransactionRunner()
        val registerStudent =
            RegisterStudentUseCase(
                studentRepository,
                studentPackageRepository,
                transactionRunner,
            )

        // When
        // the student is registering with the chosen package
        val result =
            registerStudent(
                fullName = studentName,
                packageType = packageType,
                registeredOn = registeredOn,
                groupId = 1,
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

        // And
        // the transaction is run
        assertEquals(1, transactionRunner.runCount)
    }

    @Test
    fun `registering duo package records two visits on registration date`() = runTest {
        // Given
        val studentRepository = FakeStudentRepository()
        val studentPackageRepository = FakeStudentPackageRepository()

        val packageType =
            PackageType(
                id = 2,
                name = "Duo 8 · Special rate",
                totalClasses = 8,
                validityWeeks = 4,
                price = 2400,
                maxVisitsPerAttendance = 2,
            )

        val registeredOn = LocalDate(2026, 10, 9)

        val registerStudent =
            RegisterStudentUseCase(
                studentRepository = studentRepository,
                studentPackageRepository = studentPackageRepository,
                transactionRunner = FakeTransactionRunner(),
            )

        // When
        val result =
            registerStudent(
                fullName = "Anna & John",
                packageType = packageType,
                registeredOn = registeredOn,
                visitCount = 2,
                groupId = 1,
            )

        // Then
        assertEquals(
            mapOf(registeredOn to 2),
            result.studentPackage.attendanceHistory.visitsByDate,
        )

        assertEquals(
            2,
            result.studentPackage.usedClasses,
        )

        assertEquals(
            6,
            result.studentPackage.remainingClasses,
        )

        assertEquals(
            result.student,
            studentRepository.students.single(),
        )

        assertEquals(
            result.studentPackage,
            studentPackageRepository.getLatestPackageForStudent(
                result.student.id,
            ),
        )
    }

    @Test
    fun `registration with invalid visit count creates neither student nor package`() = runTest {
        // Given
        val studentRepository = FakeStudentRepository()
        val studentPackageRepository = FakeStudentPackageRepository()

        val registerStudent =
            RegisterStudentUseCase(
                studentRepository = studentRepository,
                studentPackageRepository = studentPackageRepository,
                transactionRunner = FakeTransactionRunner(),
            )

        // When / Then
        assertFailsWith<IllegalArgumentException> {
            registerStudent(
                fullName = "Anna Smith",
                packageType = testClassPackage(),
                registeredOn = LocalDate(2026, 10, 9),
                visitCount = 2,
                groupId = 1,
            )
        }

        assertTrue(studentRepository.students.isEmpty())
        assertTrue(studentPackageRepository.packages.isEmpty())
    }

    @Test
    fun `registering a student preserves selected group`() = runTest {
        // Given
        val studentRepository = FakeStudentRepository()
        val studentPackageRepository = FakeStudentPackageRepository()
        val registerStudent =
            RegisterStudentUseCase(
                studentRepository = studentRepository,
                studentPackageRepository = studentPackageRepository,
                transactionRunner = FakeTransactionRunner(),
            )

        val selectedGroupId = 2

        // When
        val result =
            registerStudent(
                fullName = "Anna Smith",
                packageType = testClassPackage(),
                registeredOn = LocalDate(2026, 10, 9),
                groupId = selectedGroupId,
            )

        // Then
        assertEquals(selectedGroupId, result.student.groupId)
        assertEquals(
            selectedGroupId,
            studentRepository.students.single().groupId,
        )
    }
}
