package com.theunderseer.simplio.feature.attendance.fake

import com.theunderseer.simplio.feature.attendance.domain.model.InitialAttendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageAttendanceHistory
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentPackageRepository
import kotlinx.datetime.LocalDate

class FakeStudentPackageRepository(initialPackages: List<StudentPackage> = emptyList()) : StudentPackageRepository {
    private val storedPackages = initialPackages.toMutableList()
    private val storedRecordedAttendances = mutableListOf<RecordedAttendance>()

    val packages: List<StudentPackage>
        get() = storedPackages

    val recordedAttendances: List<RecordedAttendance>
        get() = storedRecordedAttendances

    private var nextId = (initialPackages.maxOfOrNull { it.id } ?: 0) + 1

    override suspend fun create(
        studentId: Int,
        packageType: PackageType,
        purchasedOn: LocalDate,
        initialAttendance: InitialAttendance?,
    ): StudentPackage = addPackage(
        studentId = studentId,
        packageType = packageType,
        purchasedOn = purchasedOn,
        startedOn = initialAttendance?.date,
        packageAttendanceHistory = PackageAttendanceHistory(
            visitsByDate = initialAttendance?.let {
                mapOf(it.date to it.visitCount)
            } ?: emptyMap(),
        ),
    )

    fun seed(
        studentId: Int,
        packageType: PackageType,
        startedOn: LocalDate,
        attendanceDates: Set<LocalDate> = setOf(startedOn),
        purchasedOn: LocalDate = startedOn,
    ): StudentPackage = addPackage(
        studentId = studentId,
        packageType = packageType,
        purchasedOn = purchasedOn,
        startedOn = startedOn,
        packageAttendanceHistory = PackageAttendanceHistory(
            visitsByDate = attendanceDates.associateWith { 1 },
        ),
    )

    override suspend fun getLatestPackageForStudent(studentId: Int): StudentPackage? = storedPackages
        .filter { it.studentId == studentId }
        .maxWithOrNull(
            compareBy<StudentPackage> { it.purchasedOn }
                .thenBy { it.id },
        )

    override suspend fun getAttendanceDatesForStudent(studentId: Int): Set<LocalDate> = storedPackages
        .filter { it.studentId == studentId }.flatMap { it.attendanceDates }.toSet()

    override suspend fun recordAttendance(packageId: Int, attendedOn: LocalDate, visitCount: Int) {
        val index = storedPackages.indexOfFirst {
            it.id == packageId
        }

        require(index >= 0) {
            "Student package with id $packageId not found"
        }

        val currentPackage = storedPackages[index]
        val visitsByDate = currentPackage.attendanceHistory.visitsByDate

        storedPackages[index] = currentPackage.copy(
            startedOn = currentPackage.startedOn ?: attendedOn,
            attendanceHistory = PackageAttendanceHistory(
                visitsByDate = visitsByDate +
                    (attendedOn to (visitsByDate[attendedOn] ?: visitCount)),
            ),
        )

        storedRecordedAttendances += RecordedAttendance(
            packageId = packageId,
            attendedOn = attendedOn,
            visitCount = visitCount,
        )
    }

    override suspend fun removeAttendance(packageId: Int, attendedOn: LocalDate) {
        val index = storedPackages.indexOfFirst {
            it.id == packageId
        }

        require(index >= 0) {
            "Student package with id $packageId not found"
        }

        val currentPackage = storedPackages[index]
        val remainingVisits = currentPackage.attendanceHistory.visitsByDate - attendedOn

        storedPackages[index] = currentPackage.copy(
            startedOn = remainingVisits.keys.minOrNull(),
            attendanceHistory = PackageAttendanceHistory(
                visitsByDate = remainingVisits,
            ),
        )
    }

    private fun addPackage(
        studentId: Int,
        packageType: PackageType,
        purchasedOn: LocalDate,
        startedOn: LocalDate?,
        packageAttendanceHistory: PackageAttendanceHistory,
    ): StudentPackage {
        val studentPackage = StudentPackage(
            id = nextId++,
            studentId = studentId,
            packageType = packageType,
            purchasedOn = purchasedOn,
            startedOn = startedOn,
            attendanceHistory = packageAttendanceHistory,
        )

        storedPackages += studentPackage

        return studentPackage
    }
}

data class RecordedAttendance(val packageId: Int, val attendedOn: LocalDate, val visitCount: Int = 1)
