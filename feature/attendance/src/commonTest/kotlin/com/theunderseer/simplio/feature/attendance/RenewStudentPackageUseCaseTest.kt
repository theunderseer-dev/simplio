package com.theunderseer.simplio.feature.attendance

import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.result.RenewStudentPackageResult
import com.theunderseer.simplio.feature.attendance.domain.usecase.RenewStudentPackageUseCase
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RenewStudentPackageUseCaseTest {
    @Test
    fun `renewing package creates a new package for existing student`() {
        // Given
        // an existing student
        val studentId = 1
        val repository = FakeStudentPackageRepository()

        // And
        // a previous package is completed
        val previousPackageType =
            PackageType(
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
                name = "4-class package",
                totalClasses = 4,
                validityWeeks = 5,
                price = 1400,
            )

        val renewedOn = LocalDate(2026, 9, 28)

        val renewStudentPackage = RenewStudentPackageUseCase(repository = repository)

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
    }
}
