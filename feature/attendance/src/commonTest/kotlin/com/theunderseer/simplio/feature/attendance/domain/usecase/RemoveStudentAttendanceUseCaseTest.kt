package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.model.InitialAttendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageAttendanceHistory
import com.theunderseer.simplio.feature.attendance.domain.result.MarkStudentPresentResult
import com.theunderseer.simplio.feature.attendance.domain.result.RemoveStudentAttendanceResult
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeTransactionRunner
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.duoSpecialPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.firstClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.fiveClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.fourClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.secondClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.thirdClassDate
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoveStudentAttendanceUseCaseTest {
    private val studentId = AttendanceTestData.student.id
    private lateinit var repository: FakeStudentPackageRepository
    private lateinit var transactionRunner: FakeTransactionRunner
    private lateinit var removeStudentAttendance: RemoveStudentAttendanceUseCase

    @BeforeTest
    fun setUp() {
        repository = FakeStudentPackageRepository()
        transactionRunner = FakeTransactionRunner()
        removeStudentAttendance = RemoveStudentAttendanceUseCase(
            repository = repository,
            transactionRunner = transactionRunner,
        )
    }

    @Test
    fun `removing attendance deletes only requested date from student package`() = runTest {
        // Given
        val studentId = 7
        val studentPackage =
            repository.seed(
                studentId = studentId,
                packageType = fiveClassPackage,
                startedOn = firstClassDate,
                attendanceDates = setOf(
                    firstClassDate,
                    secondClassDate,
                    thirdClassDate,
                ),
            )
        val otherStudentPackage =
            repository.seed(
                studentId = 19,
                packageType = fiveClassPackage,
                startedOn = firstClassDate,
                attendanceDates = setOf(
                    firstClassDate,
                    secondClassDate,
                ),
            )

        // When
        val result =
            removeStudentAttendance(
                studentId = studentId,
                attendanceDate = secondClassDate,
            )

        // Then
        val success = assertIs<RemoveStudentAttendanceResult.Success>(result)
        val expectedPackage =
            studentPackage.copy(
                attendanceHistory =
                PackageAttendanceHistory(
                    visitsByDate =
                    mapOf(
                        firstClassDate to 1,
                        thirdClassDate to 1,
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

        // When
        val result =
            removeStudentAttendance(
                studentId = studentId,
                attendanceDate = secondClassDate,
            )

        // Then
        assertEquals(RemoveStudentAttendanceResult.PackageNotFound, result)
        assertEquals(emptyList(), repository.packages)
    }

    @Test
    fun `removing attendance again returns unchanged package`() = runTest {
        // Given
        val studentPackage =
            repository.seed(
                studentId = studentId,
                packageType = fiveClassPackage,
                startedOn = firstClassDate,
                attendanceDates = setOf(firstClassDate, secondClassDate),
            )
        val expectedPackage =
            studentPackage.copy(
                attendanceHistory =
                PackageAttendanceHistory(
                    visitsByDate = mapOf(firstClassDate to 1),
                ),
            )

        // When
        val firstResult =
            removeStudentAttendance(
                studentId = studentPackage.studentId,
                attendanceDate = secondClassDate,
            )
        val repeatedResult =
            removeStudentAttendance(
                studentId = studentPackage.studentId,
                attendanceDate = secondClassDate,
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
        val remainingDates =
            setOf(
                firstClassDate,
                secondClassDate,
                thirdClassDate,
                LocalDate(2026, 10, 12),
            )
        val attendanceToRemove = LocalDate(2026, 10, 16)
        val studentPackage =
            repository.seed(
                studentId = studentId,
                packageType = fiveClassPackage,
                startedOn = firstClassDate,
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
        val startedOn = LocalDate(2026, 9, 1)
        val attendanceToRemove = LocalDate(2026, 9, 4)
        val dateAfterExpiration = LocalDate(2026, 10, 16)

        val studentPackage = repository.seed(
            studentId = studentId,
            packageType = fiveClassPackage,
            startedOn = startedOn,
            attendanceDates = setOf(
                startedOn,
                attendanceToRemove,
            ),
        )

        assertTrue(studentPackage.isExpiredOn(dateAfterExpiration))

        // When
        val result = removeStudentAttendance(
            studentId = studentId,
            attendanceDate = attendanceToRemove,
        )

        // Then
        val success = assertIs<RemoveStudentAttendanceResult.Success>(result)
        val expectedPackage = studentPackage.copy(
            attendanceHistory = PackageAttendanceHistory(
                visitsByDate = mapOf(startedOn to 1),
            ),
        )

        assertEquals(expectedPackage, success.studentPackage)
        assertEquals(
            studentPackage.expiresOn,
            success.studentPackage.expiresOn,
        )
        assertTrue(success.studentPackage.isExpiredOn(dateAfterExpiration))
        assertEquals(
            expectedPackage,
            repository.getLatestPackageForStudent(studentId),
        )
    }

    @Test
    fun `removing attendance runs operation in transaction`() = runTest {
        // Given
        val studentPackage =
            repository.seed(
                studentId = studentId,
                packageType = fiveClassPackage,
                startedOn = firstClassDate,
                attendanceDates = setOf(firstClassDate, secondClassDate),
            )

        // When
        val result =
            removeStudentAttendance(
                studentId = studentPackage.studentId,
                attendanceDate = secondClassDate,
            )

        // Then
        assertEquals(1, transactionRunner.runCount)
        assertIs<RemoveStudentAttendanceResult.Success>(result)
        assertEquals(
            studentPackage.copy(
                attendanceHistory =
                PackageAttendanceHistory(
                    visitsByDate = mapOf(firstClassDate to 1),
                ),
            ),
            repository.getLatestPackageForStudent(studentPackage.studentId),
        )
    }

    @Test
    fun `removing the only attendance resets activation and restores the full balance`() = runTest {
        // Given
        listOf(fourClassPackage, fiveClassPackage, duoSpecialPackage).forEach { packageType ->
            val studentPackage = repository.create(
                studentId = studentId,
                packageType = packageType,
                purchasedOn = AttendanceTestData.registrationDate,
                initialAttendance = InitialAttendance(
                    date = firstClassDate,
                    visitCount = packageType.maxVisitsPerAttendance,
                ),
            )

            // When
            val result = removeStudentAttendance(
                studentId = studentId,
                attendanceDate = firstClassDate,
            )

            // Then
            val updatedPackage = assertIs<RemoveStudentAttendanceResult.Success>(result).studentPackage
            val expectedPackage = studentPackage.copy(
                startedOn = null,
                attendanceHistory = PackageAttendanceHistory(visitsByDate = emptyMap()),
            )

            assertEquals(expectedPackage, updatedPackage)
            assertEquals(0, updatedPackage.usedClasses)
            assertEquals(packageType.totalClasses, updatedPackage.remainingClasses)
            assertNull(updatedPackage.startedOn)
            assertNull(updatedPackage.expiresOn)
            assertEquals(expectedPackage, repository.getLatestPackageForStudent(studentId))
        }
    }

    @Test
    fun `removing first attendance activates package from earliest remaining date`() = runTest {
        // Given
        val studentPackage = repository.seed(
            studentId = studentId,
            packageType = fiveClassPackage,
            startedOn = firstClassDate,
            attendanceDates = setOf(thirdClassDate, firstClassDate, secondClassDate),
        )

        // When
        val result = removeStudentAttendance(
            studentId = studentId,
            attendanceDate = firstClassDate,
        )

        // Then
        val updatedPackage = assertIs<RemoveStudentAttendanceResult.Success>(result).studentPackage
        val expectedPackage = studentPackage.copy(
            startedOn = secondClassDate,
            attendanceHistory = PackageAttendanceHistory(
                visitsByDate = mapOf(secondClassDate to 1, thirdClassDate to 1),
            ),
        )

        assertEquals(expectedPackage, updatedPackage)
        assertEquals(LocalDate(2026, 11, 9), updatedPackage.expiresOn)
        assertEquals(expectedPackage, repository.getLatestPackageForStudent(studentId))
    }

    @Test
    fun `removing the only attendance again keeps package unactivated`() = runTest {
        // Given
        val studentPackage = repository.seed(
            studentId = studentId,
            packageType = fiveClassPackage,
            startedOn = firstClassDate,
        )

        // When
        val firstResult = removeStudentAttendance(studentId, firstClassDate)
        val repeatedResult = removeStudentAttendance(studentId, firstClassDate)

        // Then
        val updatedPackage = assertIs<RemoveStudentAttendanceResult.Success>(firstResult).studentPackage
        assertEquals(firstResult, repeatedResult)
        assertEquals(studentPackage.purchasedOn, updatedPackage.purchasedOn)
        assertEquals(0, updatedPackage.usedClasses)
        assertNull(updatedPackage.startedOn)
        assertNull(updatedPackage.expiresOn)
        assertEquals(updatedPackage, repository.getLatestPackageForStudent(studentId))
    }

    @Test
    fun `attendance after resetting activation starts validity from the new visit`() = runTest {
        // Given
        val studentPackage = repository.seed(
            studentId = studentId,
            packageType = fiveClassPackage,
            startedOn = firstClassDate,
        )
        removeStudentAttendance(studentId, firstClassDate)
        val markStudentPresent = MarkStudentPresentUseCase(repository, transactionRunner)

        // When
        val result = markStudentPresent(studentId, secondClassDate)

        // Then
        val updatedPackage = assertIs<MarkStudentPresentResult.Success>(result).studentPackage
        assertEquals(studentPackage.id, updatedPackage.id)
        assertEquals(studentPackage.purchasedOn, updatedPackage.purchasedOn)
        assertEquals(secondClassDate, updatedPackage.startedOn)
        assertEquals(LocalDate(2026, 11, 9), updatedPackage.expiresOn)
        assertEquals(mapOf(secondClassDate to 1), updatedPackage.attendanceHistory.visitsByDate)
        assertEquals(updatedPackage, repository.getLatestPackageForStudent(studentId))
    }

    @Test
    fun `removing first duo attendance restores both visits and preserves later visit counts`() = runTest {
        // Given
        val studentPackage = repository.create(
            studentId = studentId,
            packageType = duoSpecialPackage,
            purchasedOn = AttendanceTestData.registrationDate,
            initialAttendance = InitialAttendance(date = firstClassDate, visitCount = 2),
        )
        repository.recordAttendance(studentPackage.id, secondClassDate, visitCount = 1)
        repository.recordAttendance(studentPackage.id, thirdClassDate, visitCount = 2)

        // When
        val result = removeStudentAttendance(studentId, firstClassDate)

        // Then
        val updatedPackage = assertIs<RemoveStudentAttendanceResult.Success>(result).studentPackage
        assertEquals(studentPackage.purchasedOn, updatedPackage.purchasedOn)
        assertEquals(secondClassDate, updatedPackage.startedOn)
        assertEquals(LocalDate(2026, 11, 2), updatedPackage.expiresOn)
        assertEquals(mapOf(secondClassDate to 1, thirdClassDate to 2), updatedPackage.attendanceHistory.visitsByDate)
        assertEquals(3, updatedPackage.usedClasses)
        assertEquals(5, updatedPackage.remainingClasses)
        assertEquals(updatedPackage, repository.getLatestPackageForStudent(studentId))
    }
}
