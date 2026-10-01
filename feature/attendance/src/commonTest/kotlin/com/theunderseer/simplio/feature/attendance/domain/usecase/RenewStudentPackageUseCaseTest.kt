package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.result.RenewStudentPackageResult
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeTransactionRunner
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RenewStudentPackageUseCaseTest {
    @Test
    fun `renewing completed package creates a new package for existing student`() =
        runTest {
            // Given
            // an existing student
            val studentId = 1
            val repository = FakeStudentPackageRepository()

            // And
            // a previous package is completed
            val previousPackageType =
                PackageType(
                    id = 1,
                    name = "5-class package",
                    totalClasses = 5,
                    validityWeeks = 6,
                    price = 1700,
                )

            val previousPackage =
                repository.seed(
                    studentId = studentId,
                    packageType = previousPackageType,
                    startedOn = LocalDate(2026, 8, 1),
                    attendanceDates =
                        setOf(
                            LocalDate(2026, 8, 1),
                            LocalDate(2026, 8, 8),
                            LocalDate(2026, 8, 15),
                            LocalDate(2026, 8, 22),
                            LocalDate(2026, 8, 29),
                        ),
                )

            val newPackageType =
                PackageType(
                    id = 2,
                    name = "4-class package",
                    totalClasses = 4,
                    validityWeeks = 5,
                    price = 1400,
                )

            val renewedOn = LocalDate(2026, 8, 30)

            val transactionRunner = FakeTransactionRunner()
            val renewStudentPackage =
                RenewStudentPackageUseCase(
                    repository = repository,
                    transactionRunner = transactionRunner,
                )

            // When
            // I renew the student with a new package on the renewal date
            val result =
                renewStudentPackage(
                    studentId = studentId,
                    packageType = newPackageType,
                    renewedOn = renewedOn,
                )

            // Then
            // a new student package is created for student
            val success = assertIs<RenewStudentPackageResult.Success>(result)

            // And
            // a new package entity is created
            val renewedPackage = success.studentPackage
            assertNotEquals(previousPackage.id, renewedPackage.id)

            // And
            // it belongs to the same student
            assertEquals(studentId, renewedPackage.studentId)

            // And
            // it uses the selected package type
            assertEquals(newPackageType, renewedPackage.packageType)

            // And
            // it starts on the renewal date
            assertEquals(renewedOn, renewedPackage.startedOn)

            // And
            // the first attendance is recorded automatically
            assertEquals(setOf(renewedOn), renewedPackage.attendanceDates)

            // And
            // used classes are 1 of 4
            assertEquals(1, renewedPackage.usedClasses)

            // And
            // the previous package remains persisted unchanged
            val persistedPreviousPackage =
                repository.packages.single {
                    it.id == previousPackage.id
                }

            assertEquals(
                previousPackage,
                persistedPreviousPackage,
            )

            // And
            // the new package is persisted separately
            assertTrue(
                repository.packages.contains(renewedPackage),
            )

            // And
            // both packages remain in the repository
            assertEquals(2, repository.packages.size)

            // And
            // the new package is the latest one for the student
            assertEquals(
                renewedPackage,
                repository.getLatestPackageForStudent(studentId),
            )

            // And
            // the transaction is run
            assertEquals(1, transactionRunner.runCount)
        }

    @Test
    fun `renewing package is rejected when current package is still active`() =
        runTest {
            // Given
            // an existing student
            val studentId = 1
            val repository = FakeStudentPackageRepository()

            // And
            // the student has an active package with unused classes
            val currentPackageType =
                PackageType(
                    id = 1,
                    name = "5-class package",
                    totalClasses = 5,
                    validityWeeks = 6,
                    price = 1700,
                )

            val currentPackage =
                repository.seed(
                    studentId = studentId,
                    packageType = currentPackageType,
                    startedOn = LocalDate(2026, 9, 1),
                    attendanceDates =
                        setOf(
                            LocalDate(2026, 9, 1),
                            LocalDate(2026, 9, 8),
                        ),
                )

            val newPackageType =
                PackageType(
                    id = 2,
                    name = "4-class package",
                    totalClasses = 4,
                    validityWeeks = 5,
                    price = 1400,
                )

            val renewedOn = LocalDate(2026, 9, 28)
            val transactionRunner = FakeTransactionRunner()

            val renewStudentPackage =
                RenewStudentPackageUseCase(
                    repository = repository,
                    transactionRunner = transactionRunner,
                )

            // When
            // I try to renew the student with a new package
            val result =
                renewStudentPackage(
                    studentId = studentId,
                    packageType = newPackageType,
                    renewedOn = renewedOn,
                )

            // Then
            // the operation reports that an active package already exists
            val activePackageResult =
                assertIs<RenewStudentPackageResult.ActivePackageExists>(result)

            // And
            // the current active package is returned unchanged
            assertEquals(
                currentPackage,
                activePackageResult.studentPackage,
            )

            // And
            // no new package is created
            assertEquals(
                1,
                repository.packages.size,
            )

            // And
            // the current package remains the latest package
            assertEquals(
                currentPackage,
                repository.getLatestPackageForStudent(studentId),
            )

            // And
            // the transaction is run
            assertEquals(1, transactionRunner.runCount)
        }

    @Test
    fun `renewing package creates a new package when current package is expired but incomplete`() =
        runTest {
            // Given
            // an existing student
            val studentId = 1
            val repository = FakeStudentPackageRepository()

            // And
            // the student has an expired package with unused classes
            val expiredPackageType =
                PackageType(
                    id = 1,
                    name = "5-class package",
                    totalClasses = 5,
                    validityWeeks = 6,
                    price = 1700,
                )

            val expiredPackage =
                repository.seed(
                    studentId = studentId,
                    packageType = expiredPackageType,
                    startedOn = LocalDate(2026, 8, 1),
                    attendanceDates =
                        setOf(
                            LocalDate(2026, 8, 1),
                            LocalDate(2026, 8, 8),
                        ),
                )

            val newPackageType =
                PackageType(
                    id = 2,
                    name = "4-class package",
                    totalClasses = 4,
                    validityWeeks = 5,
                    price = 1400,
                )

            val renewedOn = LocalDate(2026, 9, 28)
            val transactionRunner = FakeTransactionRunner()

            val renewStudentPackage =
                RenewStudentPackageUseCase(
                    repository = repository,
                    transactionRunner = transactionRunner,
                )

            // When
            // I renew the student with a new package on the renewal date
            val result =
                renewStudentPackage(
                    studentId = studentId,
                    packageType = newPackageType,
                    renewedOn = renewedOn,
                )

            // Then
            // a new student package is created for the student
            val success = assertIs<RenewStudentPackageResult.Success>(result)

            // And
            // a new package entity is created
            val renewedPackage = success.studentPackage
            assertNotEquals(expiredPackage.id, renewedPackage.id)

            // And
            // it belongs to the same student
            assertEquals(studentId, renewedPackage.studentId)

            // And
            // it uses the selected package type
            assertEquals(newPackageType, renewedPackage.packageType)

            // And
            // it starts on the renewal date
            assertEquals(renewedOn, renewedPackage.startedOn)

            // And
            // the first attendance is recorded automatically
            assertEquals(
                setOf(renewedOn),
                renewedPackage.attendanceDates,
            )

            // And
            // used classes start at 1
            assertEquals(
                1,
                renewedPackage.usedClasses,
            )

            // And
            // the expired package remains persisted unchanged
            val persistedExpiredPackage =
                repository.packages.single {
                    it.id == expiredPackage.id
                }

            assertEquals(
                expiredPackage,
                persistedExpiredPackage,
            )

            // And
            // the renewed package is persisted separately
            assertTrue(
                repository.packages.contains(renewedPackage),
            )

            // And
            // both packages remain in the repository
            assertEquals(
                2,
                repository.packages.size,
            )

            // And
            // the renewed package becomes the latest package
            assertEquals(
                renewedPackage,
                repository.getLatestPackageForStudent(studentId),
            )

            // And
            // the transaction is run
            assertEquals(1, transactionRunner.runCount)
        }
}
