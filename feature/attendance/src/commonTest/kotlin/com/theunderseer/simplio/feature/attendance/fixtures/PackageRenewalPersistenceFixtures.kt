package com.theunderseer.simplio.feature.attendance.fixtures

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent

internal suspend fun AttendanceDatabase.registerCompletedSyncStudent(note: String = ""): RegisteredStudent {
    val registered = registerSyncStudent()
    val packages = RoomStudentPackageRepository(this)
    for (date in listOf(
        AttendanceTestData.secondClassDate,
        AttendanceTestData.thirdClassDate,
        AttendanceTestData.fourthClassDate,
    )) {
        packages.recordAttendance(registered.studentPackage.id, date, visitCount = 1)
    }
    RoomStudentRepository(this).updateNote(registered.student.id, note)
    return RegisteredStudent(
        registered.student.copy(note = note),
        checkNotNull(packages.getLatestPackageForStudent(registered.student.id)),
    )
}
