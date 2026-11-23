package com.theunderseer.simplio.feature.attendance.data.local.repository

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.transaction.RoomTransactionRunner
import com.theunderseer.simplio.feature.attendance.domain.model.AttendanceRoster
import com.theunderseer.simplio.feature.attendance.domain.repository.attendanceSchedulesByGroup
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetAttendanceUseCase
import kotlinx.coroutines.flow.map

internal class RoomAttendanceRoster(private val database: AttendanceDatabase) {
    private val transactions = RoomTransactionRunner(database)
    private val getAttendance = GetAttendanceUseCase(
        RoomStudentRepository(database),
        RoomStudentPackageRepository(database),
    )
    private val schedules = RoomClassScheduleRepository(database)
    private val groups = RoomStudentGroupRepository(database.studentGroupDao())
    private val packages = RoomPackageTypeRepository(database.packageTypeDao())

    suspend fun getAll(): AttendanceRoster = transactions.run {
        val attendance = getAttendance()
        val studentGroups = groups.getAll()
        val groupIds = (studentGroups.map { it.id } + attendance.entries.map { it.student.groupId }).toSet()
        AttendanceRoster(
            attendance = attendance,
            groupSchedules = schedules.attendanceSchedulesByGroup(groupIds),
            packageTypes = packages.getAll(),
            studentGroups = studentGroups,
            classSchedules = schedules.getAll().filter { it.archivedAt == null },
        )
    }

    fun observe() = database.invalidationTracker.createFlow(
        "students",
        "student_packages",
        "attendance",
        "package_types",
        "student_groups",
        "class_schedules",
        "class_schedule_slots",
        "student_group_schedules",
    ).map { getAll() }
}
