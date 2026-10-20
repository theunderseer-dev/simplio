package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.result.RenewStudentPackageResult
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeTransactionRunner
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.firstClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.fiveClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.fourClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.registrationDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.secondClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.student
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.thirdClassDate
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RenewStudentPackageUseCaseTest {
    private val studentId = student.id
    private lateinit var repository: FakeStudentPackageRepository
    private lateinit var transactionRunner: FakeTransactionRunner
    private lateinit var renewStudentPackage: RenewStudentPackageUseCase

    @BeforeTest
    fun setUp() {
        repository = FakeStudentPackageRepository()
        transactionRunner = FakeTransactionRunner()
        renewStudentPackage = RenewStudentPackageUseCase(
            repository = repository,
            studentRepository = FakeStudentRepository().also { it.seed(student) },
            transactionRunner = transactionRunner,
        )
    }

    @Test
    fun `renewing completed package creates a new package for existing student`() = runTest {
        // Given
        val previousPackage = repository.seed(
            studentId = studentId,
            packageType = fiveClassPackage,
            startedOn = registrationDate,
            attendanceDates = setOf(
                firstClassDate,
                secondClassDate,
                thirdClassDate,
                LocalDate(2026, 10, 12),
                LocalDate(2026, 10, 16),
            ),
        )
        val renewedOn = LocalDate(2026, 10, 19)

        assertTrue(previousPackage.isCompleted)
        assertFalse(previousPackage.isExpiredOn(renewedOn))

        // When
        val result = renewStudentPackage(
            studentId = studentId,
            packageType = fourClassPackage,
            renewedOn = renewedOn,
            previousPackageId = previousPackage.id,
            note = student.note,
        )

        // Then
        val success = assertIs<RenewStudentPackageResult.Success>(result)
        val renewedPackage = success.studentPackage

        assertNotEquals(previousPackage.id, renewedPackage.id)
        assertEquals(studentId, renewedPackage.studentId)
        assertEquals(fourClassPackage, renewedPackage.packageType)
        assertEquals(renewedOn, renewedPackage.startedOn)
        assertEquals(setOf(renewedOn), renewedPackage.attendanceDates)
        assertEquals(1, renewedPackage.usedClasses)

        // And: the previous package is preserved
        assertEquals(
            previousPackage,
            repository.packages.single { it.id == previousPackage.id },
        )

        // And: the new package is persisted separately and becomes latest
        assertTrue(repository.packages.contains(renewedPackage))
        assertEquals(2, repository.packages.size)
        assertEquals(
            renewedPackage,
            repository.getLatestPackageForStudent(studentId),
        )
        assertEquals(1, transactionRunner.runCount)
    }

    @Test
    fun `renewing package is rejected when current package is still active`() = runTest {
        // Given
        val currentPackage = repository.seed(
            studentId = studentId,
            packageType = fiveClassPackage,
            startedOn = registrationDate,
            attendanceDates = setOf(
                firstClassDate,
                secondClassDate,
            ),
        )
        val renewedOn = thirdClassDate

        assertFalse(currentPackage.isCompleted)
        assertFalse(currentPackage.isExpiredOn(renewedOn))

        // When
        val result = renewStudentPackage(
            studentId = studentId,
            packageType = fourClassPackage,
            renewedOn = renewedOn,
            previousPackageId = currentPackage.id,
            note = student.note,
        )

        // Then
        val activePackageResult =
            assertIs<RenewStudentPackageResult.ActivePackageExists>(result)

        assertEquals(currentPackage, activePackageResult.studentPackage)

        // And: no new package is created
        assertEquals(1, repository.packages.size)
        assertEquals(
            currentPackage,
            repository.getLatestPackageForStudent(studentId),
        )
        assertEquals(1, transactionRunner.runCount)
    }

    @Test
    fun `renewing package creates a new package when current package is expired but incomplete`() = runTest {
        // Given
        val expiredPackage = repository.seed(
            studentId = studentId,
            packageType = fiveClassPackage,
            startedOn = LocalDate(2026, 7, 1),
            attendanceDates = setOf(
                LocalDate(2026, 7, 1),
                LocalDate(2026, 7, 8),
            ),
        )
        val renewedOn = registrationDate

        assertFalse(expiredPackage.isCompleted)
        assertTrue(expiredPackage.isExpiredOn(renewedOn))

        // When
        val result = renewStudentPackage(
            studentId = studentId,
            packageType = fourClassPackage,
            renewedOn = renewedOn,
            previousPackageId = expiredPackage.id,
            note = student.note,
        )

        // Then
        val success = assertIs<RenewStudentPackageResult.Success>(result)
        val renewedPackage = success.studentPackage

        assertNotEquals(expiredPackage.id, renewedPackage.id)
        assertEquals(studentId, renewedPackage.studentId)
        assertEquals(fourClassPackage, renewedPackage.packageType)
        assertEquals(renewedOn, renewedPackage.startedOn)
        assertEquals(setOf(renewedOn), renewedPackage.attendanceDates)
        assertEquals(1, renewedPackage.usedClasses)

        // And: the expired package is preserved
        assertEquals(
            expiredPackage,
            repository.packages.single { it.id == expiredPackage.id },
        )

        // And: the new package is persisted separately and becomes latest
        assertTrue(repository.packages.contains(renewedPackage))
        assertEquals(2, repository.packages.size)
        assertEquals(
            renewedPackage,
            repository.getLatestPackageForStudent(studentId),
        )
        assertEquals(1, transactionRunner.runCount)
    }
}
