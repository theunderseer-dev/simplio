package com.theunderseer.simplio.feature.attendance.data.local.sync

import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomClassScheduleRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentGroupCreationRepository
import com.theunderseer.simplio.feature.attendance.data.sync.ConflictChoice
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupRequest
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupResult
import com.theunderseer.simplio.feature.attendance.domain.model.WeeklyClassSlot
import com.theunderseer.simplio.feature.attendance.domain.schedule.findScheduleOverlaps
import com.theunderseer.simplio.feature.attendance.fake.FakeAttendanceCloud
import com.theunderseer.simplio.feature.attendance.fixtures.seedSyncDefaults
import com.theunderseer.simplio.feature.attendance.fixtures.testClassSchedule
import com.theunderseer.simplio.feature.attendance.fixtures.testUploadTarget
import com.theunderseer.simplio.feature.attendance.fixtures.withSyncDatabases
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ScheduleOverlapSyncTest {
    @Test
    fun `independent offline schedules merge preserve both groups and expose temporal conflict on both devices`() =
        runTest {
            withSyncDatabases { source, device ->
                source.seedSyncDefaults()
                val cloud = FakeAttendanceCloud()
                cloud.uploadPending(source)
                RoomPullStore(device, testUploadTarget).apply(cloud.snapshot())
                val a = testClassSchedule(
                    id = "cloud-class",
                    name = "Cloud class",
                    slots = listOf(WeeklyClassSlot("tuesday", DayOfWeek.TUESDAY, LocalTime(13, 0), LocalTime(14, 0))),
                )
                val b = a.copy(
                    id = "device-class",
                    name = "Device class",
                    slots = listOf(WeeklyClassSlot("tuesday", DayOfWeek.TUESDAY, LocalTime(13, 30), LocalTime(14, 30))),
                )
                for ((db, schedule) in listOf(source to a, device to b)) {
                    assertIs<CreateStudentGroupResult.Success>(
                        RoomStudentGroupCreationRepository(db).create(
                            CreateStudentGroupRequest(schedule.name, newSchedule = schedule),
                        ),
                    )
                }
                cloud.uploadPending(source)
                val store = RoomConflictStore(device, testUploadTarget)
                val review = store.create(cloud.snapshot())
                store.apply(review.id, review.items.associate { it.key to ConflictChoice.DEVICE }, cloud.readHead())
                cloud.uploadPending(device)
                RoomPullStore(source, testUploadTarget).apply(cloud.snapshot())
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
