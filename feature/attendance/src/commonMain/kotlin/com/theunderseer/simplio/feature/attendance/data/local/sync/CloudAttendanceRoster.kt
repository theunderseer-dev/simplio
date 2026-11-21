package com.theunderseer.simplio.feature.attendance.data.local.sync

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomClassScheduleRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.data.local.transaction.RoomTransactionRunner
import com.theunderseer.simplio.feature.attendance.data.sync.AttendancePullRemote
import com.theunderseer.simplio.feature.attendance.data.sync.UploadTarget
import com.theunderseer.simplio.feature.attendance.domain.model.AttendanceRoster
import com.theunderseer.simplio.feature.attendance.domain.repository.attendanceSchedulesByGroup
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetAttendanceUseCase

internal class CloudAttendanceRoster(
    database: AttendanceDatabase,
    target: UploadTarget,
    private val remote: AttendancePullRemote,
) {
    private val pull = RoomPullStore(database, target)
    private val transactions = RoomTransactionRunner(database)
    private val getAttendance = GetAttendanceUseCase(
        RoomStudentRepository(database),
        RoomStudentPackageRepository(database),
    )
    private val schedules = RoomClassScheduleRepository(database)

    suspend fun getAll(): AttendanceRoster {
        pull.refreshFrom(remote)
        return transactions.run {
            val attendance = getAttendance()
            val groupIds = attendance.entries.map { it.student.groupId }.toSet()
            AttendanceRoster(attendance, schedules.attendanceSchedulesByGroup(groupIds))
        }
    }
}
