package com.theunderseer.simplio.feature.attendance.fake

import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentPackageRepository
import kotlinx.datetime.LocalDate

class FakeStudentPackageRepository(
    initialPackages: List<StudentPackage> = emptyList(),
) : StudentPackageRepository {
    private val storedPackages = mutableListOf<StudentPackage>()
    private val storedUpdatedPackages = mutableListOf<StudentPackage>()

    val packages: List<StudentPackage>
        get() = storedPackages

    val updatedPackages: List<StudentPackage>
        get() = storedUpdatedPackages

    private var nextId = (initialPackages.maxOfOrNull { it.id } ?: 0) + 1

    override suspend fun create(
        studentId: Int,
        packageType: PackageType,
        startedOn: LocalDate,
    ): StudentPackage =
        addPackage(
            studentId = studentId,
            packageType = packageType,
            startedOn = startedOn,
            attendanceDates = setOf(startedOn),
        )

    fun seed(
        studentId: Int,
        packageType: PackageType,
        startedOn: LocalDate,
        attendanceDates: Set<LocalDate> = setOf(startedOn),
    ): StudentPackage =
        addPackage(
            studentId = studentId,
            packageType = packageType,
            startedOn = startedOn,
            attendanceDates = attendanceDates,
        )

    override suspend fun getLatestPackageForStudent(studentId: Int): StudentPackage? =
        storedPackages
            .filter { it.studentId == studentId }
            .maxByOrNull { it.startedOn }

    override suspend fun update(studentPackage: StudentPackage) {
        val index = storedPackages.indexOfFirst { it.id == studentPackage.id }

        require(index >= 0) {
            "Student package with id ${studentPackage.id} not found"
        }

        storedPackages[index] = studentPackage
        storedUpdatedPackages += studentPackage
    }

    private fun addPackage(
        studentId: Int,
        packageType: PackageType,
        startedOn: LocalDate,
        attendanceDates: Set<LocalDate>,
    ): StudentPackage {
        val studentPackage =
            StudentPackage(
                id = nextId++,
                studentId = studentId,
                packageType = packageType,
                startedOn = startedOn,
                attendanceDates = attendanceDates,
            )

        storedPackages += studentPackage

        return studentPackage
    }
}
