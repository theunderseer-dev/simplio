package com.theunderseer.simplio.feature.attendance.fixtures

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentGroupCreationRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.data.local.transaction.RoomTransactionRunner
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupRequest
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupResult
import com.theunderseer.simplio.feature.attendance.domain.model.WeeklyClassSlot
import com.theunderseer.simplio.feature.attendance.domain.usecase.RegisterStudentUseCase
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

internal object GroupDeletionTestData {
    val schedule = testClassSchedule(
        id = "removable-classes",
        name = "Private classes",
        slots = listOf(WeeklyClassSlot("friday", DayOfWeek.FRIDAY, LocalTime(11, 0), LocalTime(12, 0))),
    )
    val groupName = "Private group"
}

internal suspend fun AttendanceDatabase.seedDeletableGroup() = run {
    seedSyncDefaults()
    (
        RoomStudentGroupCreationRepository(this).create(
            CreateStudentGroupRequest(
                GroupDeletionTestData.groupName,
                newSchedule = GroupDeletionTestData.schedule,
            ),
        ) as CreateStudentGroupResult.Success
        ).group
}

internal suspend fun AttendanceDatabase.registerGroupStudent(groupId: Int, recordFirstAttendance: Boolean = false) =
    RegisterStudentUseCase(
        RoomStudentRepository(this),
        RoomStudentPackageRepository(this),
        RoomTransactionRunner(this),
    )(
        AttendanceTestData.student.fullName,
        groupId,
        AttendanceTestData.fourClassPackage.copy(
            id = packageTypeDao().getAll().first {
                it.name ==
                    AttendanceTestData.fourClassPackage.name
            }.id,
        ),
        AttendanceTestData.registrationDate,
        1,
        recordFirstAttendance,
        "",
    )
