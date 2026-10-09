package com.theunderseer.simplio.feature.attendance.fake

import com.theunderseer.simplio.feature.attendance.domain.model.AttendanceHistory
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

    override suspend fun recordAttendance(packageId: Int, attendedOn: LocalDate, visitCount: Int) {
        val index =
            storedPackages.indexOfFirst {
                it.id == packageId
            }

        require(index >= 0) {
            "Student package with id $packageId not found"
        }

        val currentPackage = storedPackages[index]
        val visitsByDate = currentPackage.attendanceHistory.visitsByDate

        storedPackages[index] =
            currentPackage.copy(
                attendanceHistory =
                AttendanceHistory(
                    visitsByDate =
                    visitsByDate +
                        (attendedOn to (visitsByDate[attendedOn] ?: visitCount)),
                ),
            )

        storedRecordedAttendances +=
            RecordedAttendance(
                packageId = packageId,
                attendedOn = attendedOn,
                visitCount = visitCount,
            )
    }

    override suspend fun removeAttendance(packageId: Int, attendedOn: LocalDate) {
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
                attendanceHistory =
                AttendanceHistory(
                    visitsByDate =
                    currentPackage.attendanceHistory.visitsByDate -
                        attendedOn,
                ),
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
                attendanceHistory =
                AttendanceHistory(
                    visitsByDate = attendanceDates.associateWith { 1 },
                ),
            )

        storedPackages += studentPackage

        return studentPackage
    }
}

data class RecordedAttendance(val packageId: Int, val attendedOn: LocalDate, val visitCount: Int = 1)
