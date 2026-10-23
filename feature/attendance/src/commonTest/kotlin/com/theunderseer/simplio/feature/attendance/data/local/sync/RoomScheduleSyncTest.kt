package com.theunderseer.simplio.feature.attendance.data.local.sync

import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomClassScheduleRepository
import com.theunderseer.simplio.feature.attendance.data.remote.FirestoreUploadProtocol
import com.theunderseer.simplio.feature.attendance.data.sync.ConflictChoice
import com.theunderseer.simplio.feature.attendance.data.sync.UploadPreparationFailure
import com.theunderseer.simplio.feature.attendance.data.sync.UploadProblem
import com.theunderseer.simplio.feature.attendance.domain.schedule.DefaultClassSchedule
import com.theunderseer.simplio.feature.attendance.domain.schedule.sessionsBetween
import com.theunderseer.simplio.feature.attendance.fake.FakeAttendanceCloud
import com.theunderseer.simplio.feature.attendance.fixtures.seedSyncDefaults
import com.theunderseer.simplio.feature.attendance.fixtures.testClassSchedule
import com.theunderseer.simplio.feature.attendance.fixtures.testUploadTarget
import com.theunderseer.simplio.feature.attendance.fixtures.withSyncDatabases
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomScheduleSyncTest {
    @Test
    fun `two groups share one schedule and round trip without echo uploads`() = runTest {
        withSyncDatabases { source, target ->
            source.seedSyncDefaults()
            val schedule = testClassSchedule()
            val repository = RoomClassScheduleRepository(source)
            repository.save(schedule)
            repository.assignToGroup(1, setOf(schedule.id))
            repository.assignToGroup(2, setOf(schedule.id))
            val cloud = FakeAttendanceCloud()
            val store = RoomUploadStore(source)
            val batch = assertNotNull(store.prepare(testUploadTarget))
            val upload = store.snapshot(batch)
            assertEquals(3, upload.protocolVersion)
            assertEquals(SyncEntityType.CLASS_SCHEDULE, upload.documents.first().change.entityType)
            cloud.uploadPending(source)
            target.seedSyncDefaults()
            RoomPullStore(target, testUploadTarget).apply(cloud.snapshot())
            val restored = RoomClassScheduleRepository(target)
            val groups = target.studentGroupDao().getAll()
            val a = restored.getForGroup(groups.first().id).single()
            val b = restored.getForGroup(groups.last().id).single()
            assertEquals(schedule, a)
            assertEquals(a, b)
            val date = LocalDate(2026, 10, 2)
            assertEquals(a.sessionsBetween(date, date), b.sessionsBetween(date, date))
            assertEquals(1, target.classScheduleDao().schedules().size)
            assertEquals(0, target.syncPullDao().pendingCount())
            assertNull(RoomUploadStore(target).prepare(testUploadTarget))
        }
    }

    @Test
    fun `legacy groups imply shared default series without manufacturing remote writes`() = runTest {
        withSyncDatabases { source, target ->
            source.seedSyncDefaults()
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            val snapshot = cloud.snapshot()
            assertTrue(snapshot.documents.none { it.type == SyncEntityType.CLASS_SCHEDULE })
            assertTrue(
                snapshot.documents.filter { it.type == SyncEntityType.STUDENT_GROUP }
                    .all { "scheduleIds" !in it.fields },
            )
            RoomPullStore(target, testUploadTarget).apply(snapshot)
            assertEquals(1, target.classScheduleDao().schedules().size)
            assertEquals(2, target.classScheduleDao().groupSchedules().size)
            assertEquals(DefaultClassSchedule.schedule, RoomClassScheduleRepository(target).getAll().single())
            assertEquals(0, target.syncPullDao().pendingCount())
        }
    }

    @Test
    fun `invalid schedule reference or zone aborts import without replacing current data`() = runTest {
        withSyncDatabases { source, target ->
            source.seedSyncDefaults()
            val repository = RoomClassScheduleRepository(source)
            val schedule = testClassSchedule()
            repository.save(schedule)
            repository.assignToGroup(1, setOf(schedule.id))
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            val snapshot = cloud.snapshot()
            target.seedSyncDefaults()
            val before = target.classScheduleDao().schedules()
            val pending = target.syncDao().getPending(100)
            val missing = snapshot.copy(
                documents = snapshot.documents.filter {
                    it.type != SyncEntityType.CLASS_SCHEDULE
                },
            )
            val invalid = snapshot.copy(
                documents = snapshot.documents.map { document ->
                    if (document.type !=
                        SyncEntityType.CLASS_SCHEDULE
                    ) {
                        document
                    } else {
                        document.copy(
                            fields = buildJsonObject {
                                document.fields.forEach { (key, value) -> put(key, value) }
                                put("timeZoneId", FirestoreUploadProtocol.string("Invalid/Zone"))
                            },
                        )
                    }
                },
            )
            listOf(missing, invalid).forEach { broken ->
                val failure = assertFailsWith<UploadPreparationFailure> {
                    RoomPullStore(target, testUploadTarget).apply(broken)
                }
                assertEquals(UploadProblem.INVALID_DATA, failure.problem)
                assertEquals(before, target.classScheduleDao().schedules())
                assertEquals(pending, target.syncDao().getPending(100))
            }
        }
    }

    @Test
    fun `assigning missing schedule rolls back links and queue atomically`() = runTest {
        withSyncDatabases { source, _ ->
            source.seedSyncDefaults()
            val repository = RoomClassScheduleRepository(source)
            val links = source.classScheduleDao().groupSchedules()
            val pending = source.syncDao().getPending(100)
            assertFailsWith<IllegalArgumentException> { repository.assignToGroup(1, setOf("missing-series")) }
            assertEquals(links, source.classScheduleDao().groupSchedules())
            assertEquals(pending, source.syncDao().getPending(100))
        }
    }

    @Test
    fun `schedule conflict retains both versions and rebases only chosen schedule`() = runTest {
        ConflictChoice.entries.forEach { choice ->
            withSyncDatabases { source, target ->
                source.seedSyncDefaults()
                val original = testClassSchedule()
                val remote = RoomClassScheduleRepository(source)
                remote.save(original)
                remote.assignToGroup(1, setOf(original.id))
                val cloud = FakeAttendanceCloud()
                cloud.uploadPending(source)
                RoomPullStore(target, testUploadTarget).apply(cloud.snapshot())
                RoomClassScheduleRepository(target).save(original.copy(name = "Device schedule"))
                remote.save(original.copy(name = "Cloud schedule"))
                cloud.uploadPending(source)
                val store = RoomConflictStore(target, testUploadTarget)
                val review = store.create(cloud.snapshot())
                val item = review.items.single()
                assertEquals("Device schedule", item.label)
                assertTrue(item.deviceDetails.contains("Time zone: Asia/Bangkok"))
                assertTrue(item.cloudDetails.contains("Schedule: Cloud schedule"))
                store.apply(review.id, mapOf(item.key to choice), cloud.readHead())
                val chosen = RoomClassScheduleRepository(target).getAll().single { it.id == original.id }
                assertEquals(if (choice == ConflictChoice.DEVICE) "Device schedule" else "Cloud schedule", chosen.name)
                cloud.uploadPending(target)
                RoomPullStore(source, testUploadTarget).apply(cloud.snapshot())
                assertEquals(chosen, remote.getAll().single { it.id == original.id })
                assertEquals(0, target.syncPullDao().pendingCount())
            }
        }
    }

    @Test
    fun `group schedule conflict shows names for both choices and preserves chosen links`() = runTest {
        ConflictChoice.entries.forEach { choice ->
            withSyncDatabases { source, target ->
                source.seedSyncDefaults()
                val cloud = FakeAttendanceCloud()
                cloud.uploadPending(source)
                RoomPullStore(target, testUploadTarget).apply(cloud.snapshot())
                val localSchedule = testClassSchedule(id = "device-series", name = "Private class")
                val remoteSchedule = testClassSchedule(id = "cloud-series", name = "Group class")
                val deviceRepository = RoomClassScheduleRepository(target)
                val remoteRepository = RoomClassScheduleRepository(source)
                deviceRepository.save(localSchedule)
                deviceRepository.assignToGroup(target.studentGroupDao().getAll().first().id, setOf(localSchedule.id))
                remoteRepository.save(remoteSchedule)
                remoteRepository.assignToGroup(1, setOf(remoteSchedule.id))
                cloud.uploadPending(source)
                val store = RoomConflictStore(target, testUploadTarget)
                val review = store.create(cloud.snapshot())
                val item = review.items.single()
                assertTrue(item.deviceDetails.contains("Schedule: Private class · Asia/Bangkok"))
                assertTrue(item.cloudDetails.contains("Schedule: Group class · Asia/Bangkok"))
                store.apply(review.id, mapOf(item.key to choice), cloud.readHead())
                val group = target.studentGroupDao().getAll().first()
                assertEquals(
                    if (choice == ConflictChoice.DEVICE) localSchedule else remoteSchedule,
                    deviceRepository.getForGroup(group.id).single(),
                )
                cloud.uploadPending(target)
                assertEquals(0, target.syncPullDao().pendingCount())
            }
        }
    }
}
