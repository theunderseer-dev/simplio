package com.theunderseer.simplio.feature.attendance

import com.theunderseer.simplio.attendance.domain.usecase.RenewStudentPackageUseCase
import com.theunderseer.simplio.attendance.domain.usecase.result.RenewStudentPackageResult
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RenewStudentPackageUseCaseTest {
    @Test
    fun `renewing package creates a new package for existing student`() {
        // Given
        // an existing student
        val studentId = 1

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
            StudentPackage(
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

        val repository =
            FakeStudentPackageRepository(
                initialPackages = listOf(previousPackage),
            )

        val newPackageType =
            PackageType(
                name = "4-class package",
                totalClasses = 4,
                validityWeeks = 5,
                price = 1400,
            )

        val renewedOn = LocalDate(2026, 9, 28)

        val renewStudentPackageUseCase = RenewStudentPackageUseCase(repository = repository)

        // When
        // I renew student with a new package on the renewal date
        val result =
            renewStudentPackageUseCase(
                studentId = studentId,
                packageType = newPackageType,
                renewedOn = renewedOn,
            )

        // Then
        // a new StudentPackage is created for student
        val success = assertIs<RenewStudentPackageResult.Success>(result)
        val renewedPackage = success.studentPackage

        // And
        // the renewed package is created for a proper student and package type
        assertEquals(studentId, renewedPackage.studentId)
        assertEquals(newPackageType, renewedPackage.packageType)
        // And
        // the renewed package starts on the renewal date
        assertEquals(renewedOn, renewedPackage.startedOn)
        // And
        // the first attendance is recorded on the renewal date
        assertEquals(setOf(renewedOn), renewedPackage.attendanceDates)
        // And
        // used classes are 1 of 4
        assertEquals(1, renewedPackage.usedClasses)

        // And
        // previous package remains unchanged
        assertEquals(5, previousPackage.usedClasses)
        assertTrue(previousPackage.isCompleted)

        // And
        // new package was persisted
        assertEquals(
            renewedPackage,
            repository.getLatestPackageForStudent(studentId),
        )
    }
}
