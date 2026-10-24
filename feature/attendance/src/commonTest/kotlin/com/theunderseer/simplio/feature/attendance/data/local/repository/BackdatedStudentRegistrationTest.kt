package com.theunderseer.simplio.feature.attendance.data.local.repository

import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomPullStore
import com.theunderseer.simplio.feature.attendance.data.local.transaction.RoomTransactionRunner
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupRequest
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupResult
import com.theunderseer.simplio.feature.attendance.domain.model.InitialAttendance
import com.theunderseer.simplio.feature.attendance.domain.model.WeeklyClassSlot
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetClassHistoryUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.RegisterStudentUseCase
import com.theunderseer.simplio.feature.attendance.fake.FakeAttendanceCloud
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.seedSyncDefaults
import com.theunderseer.simplio.feature.attendance.fixtures.testClassSchedule
import com.theunderseer.simplio.feature.attendance.fixtures.testUploadTarget
import com.theunderseer.simplio.feature.attendance.fixtures.withSyncDatabases
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BackdatedStudentRegistrationTest {
    private val today = LocalDate(2026, 10, 21)
    private val pastClass = LocalDate(2026, 10, 19)
    private val schedule = testClassSchedule(
        startsOn = today,
        slots = listOf(
            WeeklyClassSlot("monday", DayOfWeek.MONDAY, LocalTime(11, 0), LocalTime(12, 15)),
            WeeklyClassSlot("friday", DayOfWeek.FRIDAY, LocalTime(11, 0), LocalTime(12, 15)),
        ),
    )

    @Test
    fun `backdated registration extends shared series and round trips with or without first attendance`() = runTest {
        listOf(true, false).forEach { recordFirstAttendance ->
            withSyncDatabases { source, target ->
                source.seedSyncDefaults()
                val creation = RoomStudentGroupCreationRepository(source)
                val group = assertIs<CreateStudentGroupResult.Success>(
                    creation.create(CreateStudentGroupRequest("Morning", newSchedule = schedule)),
                ).group
                val sharedGroup = assertIs<CreateStudentGroupResult.Success>(
                    creation.create(CreateStudentGroupRequest("Shared morning", existingScheduleId = schedule.id)),
                ).group
                val cloud = FakeAttendanceCloud()
                cloud.uploadPending(source)
                val plan = AttendanceTestData.fourClassPackage.copy(id = source.packageTypeDao().getAll().first().id)
                val student = RegisterStudentUseCase(
                    RoomStudentRepository(source),
                    RoomStudentPackageRepository(source),
                    RoomTransactionRunner(source),
                )(
                    fullName = "Backdated student",
                    groupId = group.id,
                    packageType = plan,
                    registeredOn = pastClass,
                    recordFirstAttendance = recordFirstAttendance,
                )
                val schedules = RoomClassScheduleRepository(source)
                val extended = schedule.copy(startsOn = pastClass)
                assertEquals(extended, schedules.getForGroup(group.id).single())
                assertEquals(extended, schedules.getForGroup(sharedGroup.id).single())
                assertEquals(if (recordFirstAttendance) pastClass else null, student.studentPackage.startedOn)
                assertEquals(pastClass, student.studentPackage.purchasedOn)
                if (!recordFirstAttendance) {
                    assertNull(student.studentPackage.expiresOn)
                    RoomStudentPackageRepository(source).recordAttendance(student.studentPackage.id, pastClass, 1)
                }
                val sessionId = assertNotNull(
                    source.attendanceDao().getForPackage(student.studentPackage.id).single().sessionId,
                )
                cloud.uploadPending(source)
                RoomPullStore(target, testUploadTarget).apply(cloud.snapshot())
                val restoredStudent = target.studentDao().getAll().single()
                val restoredPackage = assertNotNull(
                    RoomStudentPackageRepository(target).getLatestPackageForStudent(restoredStudent.id),
                )
                assertEquals(pastClass, restoredPackage.purchasedOn)
                assertEquals(pastClass, restoredPackage.startedOn)
                assertEquals(1, restoredPackage.usedClasses)
                assertEquals(
                    extended,
                    RoomClassScheduleRepository(target).getForGroup(restoredStudent.groupId).single(),
                )
                assertEquals(sessionId, target.attendanceDao().getForPackage(restoredPackage.id).single().sessionId)
                val history = GetClassHistoryUseCase(RoomClassHistoryRepository(target.attendanceDao(), target))(today)
                val recorded = history.entries.single { it.sessionId == sessionId }
                assertEquals(pastClass, recorded.date)
                assertEquals(restoredStudent.id, recorded.students.single().student.id)
                assertEquals(0, target.syncPullDao().pendingCount())
            }
        }
    }

    @Test
    fun `invalid initial attendance rolls back earlier start package and queued schedule change`() = runTest {
        withSyncDatabases { database, _ ->
            database.seedSyncDefaults()
            val group = assertIs<CreateStudentGroupResult.Success>(
                RoomStudentGroupCreationRepository(database).create(
                    CreateStudentGroupRequest("Morning", newSchedule = schedule),
                ),
            ).group
            val student = RoomStudentRepository(database).create("Anna", group.id, "")
            val queue = database.syncDao().getPending(100)
            val plan = AttendanceTestData.fourClassPackage.copy(id = database.packageTypeDao().getAll().first().id)
            assertFailsWith<IllegalArgumentException> {
                RoomStudentPackageRepository(database).create(student.id, plan, pastClass, InitialAttendance(today, 1))
            }
            assertEquals(schedule, RoomClassScheduleRepository(database).getForGroup(group.id).single())
            assertEquals(emptyList(), database.studentPackageDao().getForStudent(student.id))
            assertEquals(queue, database.syncDao().getPending(100))
        }
    }
}
