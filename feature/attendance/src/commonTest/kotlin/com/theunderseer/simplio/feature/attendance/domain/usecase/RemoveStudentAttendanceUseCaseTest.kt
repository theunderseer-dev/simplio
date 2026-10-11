package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.model.PackageAttendanceHistory
import com.theunderseer.simplio.feature.attendance.domain.model.PackagePlan
import com.theunderseer.simplio.feature.attendance.domain.result.RemoveStudentAttendanceResult
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeTransactionRunner
import com.theunderseer.simplio.feature.attendance.fixtures.testClassPackage
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RemoveStudentAttendanceUseCaseTest {
    private val packageType = testClassPackage(plan = PackagePlan.FIVE_CLASSES)

    @Test
    fun `removing attendance deletes only requested date from student package`() = runTest {
        // Given
        val repository = FakeStudentPackageRepository()
        val removeStudentAttendance =
            RemoveStudentAttendanceUseCase(
                repository = repository,
                transactionRunner = FakeTransactionRunner(),
            )
        val studentId = 7
        val firstAttendance = LocalDate(2026, 10, 2)
        val attendanceToRemove = LocalDate(2026, 10, 5)
        val lastAttendance = LocalDate(2026, 10, 9)
        val studentPackage =
            repository.seed(
                studentId = studentId,
                packageType = packageType,
                startedOn = firstAttendance,
                attendanceDates = setOf(
                    firstAttendance,
                    attendanceToRemove,
                    lastAttendance,
                ),
            )
        val otherStudentPackage =
            repository.seed(
                studentId = 19,
                packageType = packageType,
                startedOn = firstAttendance,
                attendanceDates = setOf(
                    firstAttendance,
                    attendanceToRemove,
                ),
            )

        // When
        val result =
            removeStudentAttendance(
                studentId = studentId,
                attendanceDate = attendanceToRemove,
            )

        // Then
        val success = assertIs<RemoveStudentAttendanceResult.Success>(result)
        val expectedPackage =
            studentPackage.copy(
                attendanceHistory =
                PackageAttendanceHistory(
                    visitsByDate =
                    mapOf(
                        firstAttendance to 1,
                        lastAttendance to 1,
                    ),
                ),
            )

        assertEquals(
            expectedPackage,
            success.studentPackage,
        )
        assertEquals(
            2,
            success.studentPackage.usedClasses,
        )
        assertEquals(
            expectedPackage,
            repository.getLatestPackageForStudent(studentId),
        )
        assertEquals(
            otherStudentPackage,
            repository.getLatestPackageForStudent(otherStudentPackage.studentId),
        )
    }

    @Test
    fun `removing attendance returns package not found when student has no package`() = runTest {
        // Given
        val repository = FakeStudentPackageRepository()
        val removeStudentAttendance =
            RemoveStudentAttendanceUseCase(
                repository = repository,
                transactionRunner = FakeTransactionRunner(),
            )

        // When
        val result =
            removeStudentAttendance(
                studentId = 7,
                attendanceDate = LocalDate(2026, 10, 5),
            )

        // Then
        assertEquals(RemoveStudentAttendanceResult.PackageNotFound, result)
        assertEquals(emptyList(), repository.packages)
    }

    @Test
    fun `removing attendance again returns unchanged package`() = runTest {
        // Given
        val repository = FakeStudentPackageRepository()
        val removeStudentAttendance =
            RemoveStudentAttendanceUseCase(
                repository = repository,
                transactionRunner = FakeTransactionRunner(),
            )
        val firstAttendance = LocalDate(2026, 10, 2)
        val attendanceToRemove = LocalDate(2026, 10, 5)
        val studentPackage =
            repository.seed(
                studentId = 7,
                packageType = packageType,
                startedOn = firstAttendance,
                attendanceDates = setOf(firstAttendance, attendanceToRemove),
            )
        val expectedPackage =
            studentPackage.copy(
                attendanceHistory =
                PackageAttendanceHistory(
                    visitsByDate = mapOf(firstAttendance to 1),
                ),
            )

        // When
        val firstResult =
            removeStudentAttendance(
                studentId = studentPackage.studentId,
                attendanceDate = attendanceToRemove,
            )
        val repeatedResult =
            removeStudentAttendance(
                studentId = studentPackage.studentId,
                attendanceDate = attendanceToRemove,
            )

        // Then
        assertEquals(
            expectedPackage,
            assertIs<RemoveStudentAttendanceResult.Success>(firstResult).studentPackage,
        )
        assertEquals(firstResult, repeatedResult)
        assertEquals(
            expectedPackage,
            repository.getLatestPackageForStudent(studentPackage.studentId),
        )
    }

    @Test
    fun `removing attendance from completed package makes it incomplete`() = runTest {
        // Given
        val repository = FakeStudentPackageRepository()
        val removeStudentAttendance =
            RemoveStudentAttendanceUseCase(
                repository = repository,
                transactionRunner = FakeTransactionRunner(),
            )
        val remainingDates =
            setOf(
                LocalDate(2026, 10, 2),
                LocalDate(2026, 10, 5),
                LocalDate(2026, 10, 9),
                LocalDate(2026, 10, 12),
            )
        val attendanceToRemove = LocalDate(2026, 10, 16)
        val studentPackage =
            repository.seed(
                studentId = 7,
                packageType = packageType,
                startedOn = LocalDate(2026, 10, 2),
                attendanceDates = remainingDates + attendanceToRemove,
            )

        assertTrue(studentPackage.isCompleted)

        // When
        val result =
            removeStudentAttendance(
                studentId = studentPackage.studentId,
                attendanceDate = attendanceToRemove,
            )

        // Then
        val success = assertIs<RemoveStudentAttendanceResult.Success>(result)
        val expectedPackage =
            studentPackage.copy(
                attendanceHistory =
                PackageAttendanceHistory(
                    visitsByDate = remainingDates.associateWith { 1 },
                ),
            )

        assertEquals(expectedPackage, success.studentPackage)
        assertEquals(4, success.studentPackage.usedClasses)
        assertFalse(success.studentPackage.isCompleted)
        assertEquals(
            expectedPackage,
            repository.getLatestPackageForStudent(studentPackage.studentId),
        )
    }

    @Test
    fun `removing attendance from expired package preserves package expiration`() = runTest {
        // Given
        val repository = FakeStudentPackageRepository()
        val removeStudentAttendance =
            RemoveStudentAttendanceUseCase(
                repository = repository,
                transactionRunner = FakeTransactionRunner(),
            )
        val firstAttendance = LocalDate(2026, 9, 1)
        val attendanceToRemove = LocalDate(2026, 10, 16)
        val studentPackage =
            repository.seed(
                studentId = 7,
                packageType = packageType,
                startedOn = firstAttendance,
                attendanceDates = setOf(firstAttendance, attendanceToRemove),
            )

        assertTrue(studentPackage.isExpiredOn(attendanceToRemove))

        // When
        val result =
            removeStudentAttendance(
                studentId = studentPackage.studentId,
                attendanceDate = attendanceToRemove,
            )

        // Then
        val success = assertIs<RemoveStudentAttendanceResult.Success>(result)
        val expectedPackage =
            studentPackage.copy(
                attendanceHistory =
                PackageAttendanceHistory(
                    visitsByDate = mapOf(firstAttendance to 1),
                ),
            )

        assertEquals(expectedPackage, success.studentPackage)
        assertTrue(success.studentPackage.isExpiredOn(attendanceToRemove))
        assertEquals(
            expectedPackage,
            repository.getLatestPackageForStudent(studentPackage.studentId),
        )
    }

    @Test
    fun `removing attendance runs operation in transaction`() = runTest {
        // Given
        val repository = FakeStudentPackageRepository()
        val transactionRunner = FakeTransactionRunner()
        val removeStudentAttendance =
            RemoveStudentAttendanceUseCase(
                repository = repository,
                transactionRunner = transactionRunner,
            )
        val firstAttendance = LocalDate(2026, 10, 2)
        val attendanceToRemove = LocalDate(2026, 10, 5)
        val studentPackage =
            repository.seed(
                studentId = 7,
                packageType = packageType,
                startedOn = firstAttendance,
                attendanceDates = setOf(firstAttendance, attendanceToRemove),
            )

        // When
        val result =
            removeStudentAttendance(
                studentId = studentPackage.studentId,
                attendanceDate = attendanceToRemove,
            )

        // Then
        assertEquals(1, transactionRunner.runCount)
        assertIs<RemoveStudentAttendanceResult.Success>(result)
        assertEquals(
            studentPackage.copy(
                attendanceHistory =
                PackageAttendanceHistory(
                    visitsByDate = mapOf(firstAttendance to 1),
                ),
            ),
            repository.getLatestPackageForStudent(studentPackage.studentId),
        )
    }

    @Test
    fun `removing the only attendance is rejected and preserves the package`() = runTest {
        // Given
        val repository = FakeStudentPackageRepository()
        val removeStudentAttendance =
            RemoveStudentAttendanceUseCase(
                repository = repository,
                transactionRunner = FakeTransactionRunner(),
            )
        val firstAttendance = LocalDate(2026, 10, 2)
        val studentPackage =
            repository.seed(
                studentId = 7,
                packageType = packageType,
                startedOn = firstAttendance,
            )

        // When
        val result =
            removeStudentAttendance(
                studentId = studentPackage.studentId,
                attendanceDate = firstAttendance,
            )

        // Then
        assertEquals(RemoveStudentAttendanceResult.FirstAttendanceCannotBeRemoved, result)
        assertEquals(
            studentPackage,
            repository.getLatestPackageForStudent(studentPackage.studentId),
        )
    }

    @Test
    fun `removing first attendance is rejected even when later attendance exists`() = runTest {
        // Given
        val repository = FakeStudentPackageRepository()
        val removeStudentAttendance =
            RemoveStudentAttendanceUseCase(
                repository = repository,
                transactionRunner = FakeTransactionRunner(),
            )
        val firstAttendance = LocalDate(2026, 10, 2)
        val studentPackage =
            repository.seed(
                studentId = 7,
                packageType = packageType,
                startedOn = firstAttendance,
                attendanceDates = setOf(
                    firstAttendance,
                    LocalDate(2026, 10, 5),
                    LocalDate(2026, 10, 9),
                ),
            )

        // When
        val result =
            removeStudentAttendance(
                studentId = studentPackage.studentId,
                attendanceDate = firstAttendance,
            )

        // Then
        assertEquals(RemoveStudentAttendanceResult.FirstAttendanceCannotBeRemoved, result)
        assertEquals(
            studentPackage,
            repository.getLatestPackageForStudent(studentPackage.studentId),
        )
    }
}
