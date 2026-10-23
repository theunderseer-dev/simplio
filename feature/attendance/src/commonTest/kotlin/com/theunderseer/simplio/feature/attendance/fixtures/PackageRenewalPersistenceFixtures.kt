package com.theunderseer.simplio.feature.attendance.fixtures

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent

internal suspend fun AttendanceDatabase.registerCompletedSyncStudent(
    note: String = "",
    packageType: PackageType = AttendanceTestData.fourClassPackage,
): RegisteredStudent {
    val registered = registerSyncStudent(packageType = packageType)
    val packages = RoomStudentPackageRepository(this)
    val completed = testCompletedPackage(registered.student, registered.studentPackage.packageType)
    for ((date, visits) in completed.attendanceHistory.visitsByDate) {
        if (date !in registered.studentPackage.attendanceDates) {
            packages.recordAttendance(registered.studentPackage.id, date, visitCount = visits)
        }
    }
    RoomStudentRepository(this).updateNote(registered.student.id, note)
    return RegisteredStudent(
        registered.student.copy(note = note),
        checkNotNull(packages.getLatestPackageForStudent(registered.student.id)),
    )
}
