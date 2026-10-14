package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.model.PackageAttendanceHistory
import com.theunderseer.simplio.feature.attendance.domain.result.RemoveStudentAttendanceResult
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeTransactionRunner
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.firstClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.fiveClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.secondClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.thirdClassDate
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
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
    fun `removing the only attendance is rejected and preserves the package`() = runTest {
        // Given
        val studentPackage =
            repository.seed(
                studentId = studentId,
                packageType = fiveClassPackage,
                startedOn = firstClassDate,
            )

        // When
        val result =
            removeStudentAttendance(
                studentId = studentPackage.studentId,
                attendanceDate = firstClassDate,
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

        // When
        val result =
            removeStudentAttendance(
                studentId = studentPackage.studentId,
                attendanceDate = firstClassDate,
            )

        // Then
        assertEquals(RemoveStudentAttendanceResult.FirstAttendanceCannotBeRemoved, result)
        assertEquals(
            studentPackage,
            repository.getLatestPackageForStudent(studentPackage.studentId),
        )
    }
}
