package com.theunderseer.simplio.feature.attendance.fake

import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentPackageRepository
import kotlinx.datetime.LocalDate

class FakeStudentPackageRepository(initialPackages: List<StudentPackage> = emptyList()) : StudentPackageRepository {
    private val storedPackages = mutableListOf<StudentPackage>()
    private val storedRecordedAttendances =
        mutableListOf<RecordedAttendance>()

    val packages: List<StudentPackage>
        get() = storedPackages

    val recordedAttendances: List<RecordedAttendance>
        get() = storedRecordedAttendances

    private var nextId = (initialPackages.maxOfOrNull { it.id } ?: 0) + 1

    override suspend fun create(studentId: Int, packageType: PackageType, startedOn: LocalDate): StudentPackage =
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
    ): StudentPackage = addPackage(
        studentId = studentId,
        packageType = packageType,
        startedOn = startedOn,
        attendanceDates = attendanceDates,
    )

    override suspend fun getLatestPackageForStudent(studentId: Int): StudentPackage? = storedPackages
        .filter { it.studentId == studentId }
        .maxByOrNull { it.startedOn }

    override suspend fun recordAttendance(packageId: Int, attendedOn: LocalDate) {
        val index =
            storedPackages.indexOfFirst {
                it.id == packageId
            }

        require(index >= 0) {
            "Student package with id $packageId not found"
        }

        val currentPackage = storedPackages[index]

        storedPackages[index] =
            currentPackage.copy(
                attendanceDates =
                currentPackage.attendanceDates + attendedOn,
            )

        storedRecordedAttendances +=
            RecordedAttendance(
                packageId = packageId,
                attendedOn = attendedOn,
            )
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

data class RecordedAttendance(val packageId: Int, val attendedOn: LocalDate)
