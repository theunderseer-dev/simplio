package com.theunderseer.simplio.feature.attendance.data.remote

import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomClassScheduleRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentGroupCreationRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomPullStore
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomUploadStore
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupRequest
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupResult
import com.theunderseer.simplio.feature.attendance.domain.model.InitialAttendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.WeeklyClassSlot
import com.theunderseer.simplio.feature.attendance.fixtures.seedSyncDefaults
import com.theunderseer.simplio.feature.attendance.fixtures.testClassSchedule
import com.theunderseer.simplio.feature.attendance.fixtures.withFirestoreEmulator
import com.theunderseer.simplio.feature.attendance.fixtures.withSyncDatabases
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class FirestoreCustomGroupEmulatorTest {
    @Test
    fun `backdated group registration and extended schedule arrive on second device`() = runTest {
        withFirestoreEmulator { client, target ->
            withSyncDatabases { source, device ->
                source.seedSyncDefaults()
                val schedule = testClassSchedule(
                    id = "tuesday-class",
                    name = "Tuesday class",
                    startsOn = LocalDate(2026, 10, 7),
                    slots = listOf(
                        WeeklyClassSlot("tuesday", DayOfWeek.TUESDAY, LocalTime(18, 0), LocalTime(19, 0)),
                    ),
                )
                val group = assertIs<CreateStudentGroupResult.Success>(
                    RoomStudentGroupCreationRepository(source).create(
                        CreateStudentGroupRequest("Private yoga", newSchedule = schedule),
                    ),
                ).group
                val student = RoomStudentRepository(source).create("Private student", group.id, "Keep note")
                val type = source.packageTypeDao().getAll().first()
                val date = LocalDate(2026, 10, 6)
                val pack = RoomStudentPackageRepository(source).create(
                    student.id,
                    PackageType(
                        type.id,
                        type.name,
                        type.totalClasses,
                        type.validityWeeks,
                        type.price,
                        type.maxVisitsPerAttendance,
                    ),
                    date,
                    InitialAttendance(date, 1),
                )
                val session = assertNotNull(source.attendanceDao().getForPackage(pack.id).single().sessionId)
                val store = RoomUploadStore(source)
                val remote = FirestoreAttendanceUploadRemote(client)
                while (true) {
                    val batch = store.prepare(target) ?: break
                    store.acknowledge(batch, remote.sendOrRecover(store.snapshot(batch), batch.commitBody))
                }
                val reader = FirestoreAttendancePullRemote(client)
                RoomPullStore(device, target).apply(reader.readSnapshot(reader.readHead()))
                val restoredStudent = device.studentDao().getAll().single()
                val restoredPackage = assertNotNull(device.studentPackageDao().getLatestForStudent(restoredStudent.id))
                assertEquals(
                    "Private yoga",
                    device.studentGroupDao().getAll().single {
                        it.id == restoredStudent.groupId
                    }.name,
                )
                assertEquals(session, device.attendanceDao().getForPackage(restoredPackage.id).single().sessionId)
                assertEquals(
                    schedule.copy(startsOn = date),
                    RoomClassScheduleRepository(device).getForGroup(restoredStudent.groupId).single(),
                )
                assertEquals(0, device.syncPullDao().pendingCount())
            }
        }
    }
}
