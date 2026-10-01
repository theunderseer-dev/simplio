package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.result.MarkStudentPresentResult
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeTransactionRunner
import com.theunderseer.simplio.feature.attendance.fake.RecordedAttendance
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MarkStudentPresentUseCaseTest {
    private lateinit var repository: FakeStudentPackageRepository
    private lateinit var markStudentPresent: MarkStudentPresentUseCase
    private lateinit var transactionRunner: FakeTransactionRunner
    private val studentId = 1
    private val packageType =
        PackageType(
            id = 1,
            name = "5-class package",
            totalClasses = 5,
            validityWeeks = 6,
            price = 1700,
        )

    @BeforeTest
    fun setUp() {
        repository = FakeStudentPackageRepository()
        transactionRunner = FakeTransactionRunner()
        markStudentPresent =
            MarkStudentPresentUseCase(
                repository = repository,
                transactionRunner = transactionRunner,
            )
    }

    @Test
    fun `marking a student as present adds the date to attendance`() =
        runTest {
            // Given
            // student has an active 5-class package
            // And
            // he has already attended one class
            val firstAttendance = LocalDate(2026, 9, 25)
            val studentPackage =
                repository.seed(
                    studentId = studentId,
                    packageType = packageType,
                    startedOn = firstAttendance,
                )

            // When
            // I mark student present on a new date
            val nextAttendanceDate = LocalDate(2026, 9, 27)

            val result =
                markStudentPresent(
                    studentId = studentId,
                    attendanceDate = nextAttendanceDate,
                )

            // Then
            // the operation is successful
            val success = assertIs<MarkStudentPresentResult.Success>(result)

            // And
            // the same package is updated, not replaced
            assertEquals(
                studentPackage.id,
                success.studentPackage.id,
            )

            // And
            // the new attendance date is recorded
            assertEquals(
                setOf(firstAttendance, nextAttendanceDate),
                success.studentPackage.attendanceDates,
            )

            // And
            // used classes increase from 1 to 2
            assertEquals(2, success.studentPackage.usedClasses)

            // And
            // the updated package is persisted
            assertEquals(
                success.studentPackage,
                repository.getLatestPackageForStudent(studentId),
            )

            // And
            // the attendance persistence operation is recorded
            assertEquals(
                listOf(
                    RecordedAttendance(
                        packageId = studentPackage.id,
                        attendedOn = nextAttendanceDate,
                    ),
                ),
                repository.recordedAttendances,
            )
        }

    @Test
    fun `marking a student as present on the same date does not duplicate attendance`() =
        runTest {
            // Given
            // student has an active 5-class package
            // And
            // he has already attended one class
            val firstAttendance = LocalDate(2026, 9, 25)

            repository.seed(
                studentId = studentId,
                packageType = packageType,
                startedOn = firstAttendance,
            )

            // When
            // I mark the student present on the same date again
            val result =
                markStudentPresent(
                    studentId = studentId,
                    attendanceDate = firstAttendance,
                )

            // Then
            // the operation is successful
            val success = assertIs<MarkStudentPresentResult.Success>(result)

            // And
            // the attendance date is not duplicated
            assertEquals(
                setOf(firstAttendance),
                success.studentPackage.attendanceDates,
            )

            // And
            // used classes remain at 1
            assertEquals(1, success.studentPackage.usedClasses)

            // And
            // no persistence update is required
            assertTrue(repository.recordedAttendances.isEmpty())
        }

    @Test
    fun `marking the final attendance marks the package as completed`() =
        runTest {
            // Given
            // student has a 5-class package
            // And
            // he has already used 4 classes
            val studentPackage =
                repository.seed(
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
            // I mark the student present on a new date
            val result =
                markStudentPresent(
                    studentId = studentId,
                    attendanceDate = finalAttendanceDate,
                )

            // Then
            // the operation is successful
            val success = assertIs<MarkStudentPresentResult.Success>(result)

            // And
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
            // same package was updated, not replaced
            assertEquals(
                studentPackage.id,
                success.studentPackage.id,
            )

            // And
            // completed state is persisted
            assertEquals(
                success.studentPackage,
                repository.getLatestPackageForStudent(studentId),
            )

            // And
            // the final attendance persistence operation is recorded
            assertEquals(
                listOf(
                    RecordedAttendance(
                        packageId = studentPackage.id,
                        attendedOn = finalAttendanceDate,
                    ),
                ),
                repository.recordedAttendances,
            )
        }

    @Test
    fun `marking attendance on a completed package is rejected`() =
        runTest {
            // Given
            // student has a completed 5-class package
            // And
            // all 5 classes have already been used
            val studentPackage =
                repository.seed(
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
            // I try to mark the student present on another date
            val result =
                markStudentPresent(
                    studentId = studentId,
                    attendanceDate = nextAttendanceDate,
                )

            // Then
            // the operation reports that the package is completed
            val completedResult = assertIs<MarkStudentPresentResult.PackageCompleted>(result)

            // And
            // the original package is returned
            assertEquals(
                studentPackage,
                completedResult.studentPackage,
            )

            // And
            // the attendance must not be added
            assertFalse(nextAttendanceDate in completedResult.studentPackage.attendanceDates)

            // And
            // rejected attendance is not persisted
            assertTrue(repository.recordedAttendances.isEmpty())
        }

    @Test
    fun `marking attendance on an expired package is rejected`() =
        runTest {
            // Given
            // student has a 5-class package
            // And
            // the package still has unused classes
            // And
            // the package has expired
            val studentPackage =
                repository.seed(
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
            // I try to mark the student present on a later date
            val result =
                markStudentPresent(
                    studentId = studentId,
                    attendanceDate = attendanceDate,
                )

            // Then
            // the package is already expired
            val expiredResult = assertIs<MarkStudentPresentResult.PackageExpired>(result)

            // And
            // the original package is returned
            assertEquals(
                studentPackage,
                expiredResult.studentPackage,
            )

            // And
            // the attendance must not be added
            assertFalse(attendanceDate in expiredResult.studentPackage.attendanceDates)

            // And
            // rejected attendance is not persisted
            assertTrue(repository.recordedAttendances.isEmpty())
        }

    @Test
    fun `marking attendance without a student package is rejected`() =
        runTest {
            // Given
            // student has no package at all
            // When
            // I try to mark student present on a date without any package
            val result =
                markStudentPresent(
                    studentId = studentId,
                    attendanceDate = LocalDate(2026, 9, 29),
                )

            // Then
            // the operation reports that no package was found for the student
            assertIs<MarkStudentPresentResult.PackageNotFound>(result)

            // And
            // rejected attendance is not persisted
            assertTrue(repository.recordedAttendances.isEmpty())
        }

    @Test
    fun `attendance marking executes within a single transaction`() =
        runTest {
            // Given
            repository.seed(
                studentId = studentId,
                packageType = packageType,
                startedOn = LocalDate(2026, 9, 25),
            )

            // When
            markStudentPresent(
                studentId = studentId,
                attendanceDate = LocalDate(2026, 9, 27),
            )

            // Then
            assertEquals(
                1,
                transactionRunner.runCount,
            )
        }
}
