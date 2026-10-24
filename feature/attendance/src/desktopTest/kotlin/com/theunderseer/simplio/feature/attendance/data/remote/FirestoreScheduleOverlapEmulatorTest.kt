package com.theunderseer.simplio.feature.attendance.data.remote

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomClassScheduleRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentGroupCreationRepository
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomConflictStore
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomPullStore
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomUploadStore
import com.theunderseer.simplio.feature.attendance.data.sync.ConflictChoice
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupRequest
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupResult
import com.theunderseer.simplio.feature.attendance.domain.model.WeeklyClassSlot
import com.theunderseer.simplio.feature.attendance.domain.schedule.findScheduleOverlaps
import com.theunderseer.simplio.feature.attendance.fixtures.seedSyncDefaults
import com.theunderseer.simplio.feature.attendance.fixtures.testClassSchedule
import com.theunderseer.simplio.feature.attendance.fixtures.withFirestoreEmulator
import com.theunderseer.simplio.feature.attendance.fixtures.withSyncDatabases
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FirestoreScheduleOverlapEmulatorTest {
    @Test
    fun `offline groups with overlapping schedules survive reconciliation and round trip`() = runTest {
        withFirestoreEmulator { client, target ->
            withSyncDatabases { source, device ->
                val upload = FirestoreAttendanceUploadRemote(client)
                val reader = FirestoreAttendancePullRemote(client)
                suspend fun send(db: AttendanceDatabase) {
                    val store = RoomUploadStore(db)
                    while (true) {
                        val batch = store.prepare(target) ?: return
                        store.acknowledge(batch, upload.sendOrRecover(store.snapshot(batch), batch.commitBody))
                    }
                }
                source.seedSyncDefaults()
                send(source)
                RoomPullStore(device, target).apply(reader.readSnapshot(reader.readHead()))
                val a = testClassSchedule(
                    id = "remote-class",
                    name = "Remote class",
                    slots = listOf(WeeklyClassSlot("tuesday", DayOfWeek.TUESDAY, LocalTime(13, 0), LocalTime(14, 0))),
                )
                val b = a.copy(
                    id = "local-class",
                    name = "Local class",
                    slots = listOf(WeeklyClassSlot("tuesday", DayOfWeek.TUESDAY, LocalTime(13, 30), LocalTime(14, 30))),
                )
                for ((db, schedule) in listOf(source to a, device to b)) {
                    assertIs<CreateStudentGroupResult.Success>(
                        RoomStudentGroupCreationRepository(db).create(
                            CreateStudentGroupRequest(schedule.name, newSchedule = schedule),
                        ),
                    )
                }
                send(source)
                val store = RoomConflictStore(device, target)
                val review = store.create(reader.readSnapshot(reader.readHead()))
                store.apply(review.id, review.items.associate { it.key to ConflictChoice.DEVICE }, reader.readHead())
                send(device)
                RoomPullStore(source, target).apply(reader.readSnapshot(reader.readHead()))
                for (db in listOf(source, device)) {
                    val schedules = RoomClassScheduleRepository(db).getAll()
                    assertTrue(schedules.containsAll(listOf(a, b)))
                    assertTrue(db.studentGroupDao().getAll().map { it.name }.containsAll(listOf(a.name, b.name)))
                    assertEquals(1, findScheduleOverlaps(schedules).size)
                    assertEquals(0, db.syncPullDao().pendingCount())
                }
            }
        }
    }
}
