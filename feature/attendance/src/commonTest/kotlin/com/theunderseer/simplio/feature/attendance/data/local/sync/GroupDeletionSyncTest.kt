package com.theunderseer.simplio.feature.attendance.data.local.sync

import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentGroupDeletionRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.data.sync.ConflictChoice
import com.theunderseer.simplio.feature.attendance.data.sync.InvalidConflictChoice
import com.theunderseer.simplio.feature.attendance.domain.repository.DeleteStudentGroupResult
import com.theunderseer.simplio.feature.attendance.fake.FakeAttendanceCloud
import com.theunderseer.simplio.feature.attendance.fixtures.ClassAttendanceTestClock
import com.theunderseer.simplio.feature.attendance.fixtures.GroupDeletionTestData
import com.theunderseer.simplio.feature.attendance.fixtures.registerGroupStudent
import com.theunderseer.simplio.feature.attendance.fixtures.seedDeletableGroup
import com.theunderseer.simplio.feature.attendance.fixtures.seedSyncDefaults
import com.theunderseer.simplio.feature.attendance.fixtures.testUploadTarget
import com.theunderseer.simplio.feature.attendance.fixtures.withSyncDatabases
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupDeletionSyncTest {
    @Test
    fun `independent offline deletions retire a shared series when reconciliation removes its last groups`() = runTest {
        withSyncDatabases { source, device ->
            source.seedSyncDefaults()
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            RoomPullStore(device, testUploadTarget).apply(cloud.snapshot())
            val clock = ClassAttendanceTestClock()
            RoomStudentGroupDeletionRepository(source, clock).delete(source.studentGroupDao().getAll().first().id)
            RoomStudentGroupDeletionRepository(device, clock).delete(device.studentGroupDao().getAll().last().id)
            cloud.uploadPending(source)
            val store = RoomConflictStore(device, testUploadTarget, clock)
            val review = store.create(cloud.snapshot())
            assertTrue(review.items.isEmpty())
            store.apply(review.id, emptyMap(), cloud.readHead())
            assertTrue(device.studentGroupDao().getAll().isEmpty())
            assertEquals(
                clock.now().toEpochMilliseconds(),
                device.classScheduleDao().schedules().single().archivedAtEpochMillis,
            )
            cloud.uploadPending(device)
            RoomPullStore(source, testUploadTarget).apply(cloud.snapshot())
            assertTrue(source.studentGroupDao().getAll().isEmpty())
            assertEquals(device.classScheduleDao().schedules(), source.classScheduleDao().schedules())
        }
    }

    @Test
    fun `implicit legacy schedule survives both choices when deletion meets offline registration`() = runTest {
        for (deletionOnSource in listOf(false, true)) {
            for (choice in ConflictChoice.entries) {
                withSyncDatabases { source, device ->
                    source.seedSyncDefaults()
                    val cloud = FakeAttendanceCloud()
                    cloud.uploadPending(source)
                    RoomPullStore(device, testUploadTarget).apply(cloud.snapshot())
                    val deleting = if (deletionOnSource) source else device
                    val registering = if (deletionOnSource) device else source
                    deleting.studentGroupDao().getAll().forEach {
                        RoomStudentGroupDeletionRepository(deleting, ClassAttendanceTestClock()).delete(it.id)
                    }
                    registering.registerGroupStudent(registering.studentGroupDao().getAll().first().id)
                    cloud.uploadPending(source)
                    val store = RoomConflictStore(device, testUploadTarget)
                    val review = store.create(cloud.snapshot())
                    assertEquals(1, review.items.size)
                    val safeChoice = if (deletionOnSource) ConflictChoice.DEVICE else ConflictChoice.CLOUD
                    if (choice != safeChoice) {
                        assertFailsWith<InvalidConflictChoice> {
                            store.apply(review.id, mapOf(review.items.single().key to choice), cloud.readHead())
                        }
                    }
                    store.apply(review.id, mapOf(review.items.single().key to safeChoice), cloud.readHead())
                    val keepRegistration = true
                    assertEquals(if (keepRegistration) 1 else 0, device.studentDao().getAll().size)
                    assertEquals(if (keepRegistration) 2 else 0, device.studentGroupDao().getAll().size)
                    cloud.uploadPending(device)
                    RoomPullStore(source, testUploadTarget).apply(cloud.snapshot())
                    assertEquals(device.studentDao().getAll().size, source.studentDao().getAll().size)
                }
            }
        }
    }

    @Test
    fun `group schedule and preceding student deletions upload atomically and pull without echoes`() = runTest {
        withSyncDatabases { source, target ->
            val group = source.seedDeletableGroup()
            val student = source.registerGroupStudent(group.id, true)
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            RoomPullStore(target, testUploadTarget).apply(cloud.snapshot())
            RoomStudentRepository(source).delete(student.student.id)
            assertEquals(
                DeleteStudentGroupResult.DELETED,
                RoomStudentGroupDeletionRepository(source, ClassAttendanceTestClock()).delete(group.id),
            )
            val store = RoomUploadStore(source)
            val batch = assertNotNull(store.prepare(testUploadTarget))
            val types = store.snapshot(batch).documents.map { it.change.entityType }.toSet()
            assertTrue(
                setOf(
                    SyncEntityType.STUDENT_GROUP,
                    SyncEntityType.CLASS_SCHEDULE,
                    SyncEntityType.STUDENT,
                    SyncEntityType.STUDENT_PACKAGE,
                    SyncEntityType.ATTENDANCE,
                ).all {
                    it in
                        types
                },
            )
            cloud.uploadPending(source)
            RoomPullStore(target, testUploadTarget).apply(cloud.snapshot())
            assertFalse(target.studentGroupDao().getAll().any { it.name == group.name })
            assertTrue(target.studentDao().getAll().isEmpty())
            assertNotNull(
                target.classScheduleDao().schedules().single {
                    it.id == GroupDeletionTestData.schedule.id
                }.archivedAtEpochMillis,
            )
            assertNull(RoomUploadStore(target).prepare(testUploadTarget))
        }
    }

    @Test
    fun `local deletion versus remote registration requires one complete graph choice`() = runTest {
        for (choice in ConflictChoice.entries) {
            withSyncDatabases { source, device ->
                val group = source.seedDeletableGroup()
                val cloud = FakeAttendanceCloud()
                cloud.uploadPending(source)
                RoomPullStore(device, testUploadTarget).apply(cloud.snapshot())
                val local = device.studentGroupDao().getAll().single { it.name == group.name }
                RoomStudentGroupDeletionRepository(device, ClassAttendanceTestClock()).delete(local.id)
                source.registerGroupStudent(group.id)
                cloud.uploadPending(source)
                val store = RoomConflictStore(device, testUploadTarget)
                val review = store.create(cloud.snapshot())
                assertEquals(1, review.items.size)
                assertFalse(review.items.single().deviceChoiceAllowed)
                if (choice != ConflictChoice.CLOUD) {
                    assertFailsWith<InvalidConflictChoice> {
                        store.apply(review.id, mapOf(review.items.single().key to choice), cloud.readHead())
                    }
                }
                store.apply(review.id, mapOf(review.items.single().key to ConflictChoice.CLOUD), cloud.readHead())
                assertTrue(device.studentGroupDao().getAll().any { it.name == group.name })
                assertEquals(1, device.studentDao().getAll().size)
                cloud.uploadPending(device)
                RoomPullStore(source, testUploadTarget).apply(cloud.snapshot())
                assertEquals(device.studentDao().getAll().size, source.studentDao().getAll().size)
            }
        }
    }

    @Test
    fun `remote deletion versus offline registration cannot discard the newly added student`() = runTest {
        for (choice in ConflictChoice.entries) {
            withSyncDatabases { source, device ->
                val group = source.seedDeletableGroup()
                val cloud = FakeAttendanceCloud()
                cloud.uploadPending(source)
                RoomPullStore(device, testUploadTarget).apply(cloud.snapshot())
                val local = device.studentGroupDao().getAll().single { it.name == group.name }
                device.registerGroupStudent(local.id)
                RoomStudentGroupDeletionRepository(source, ClassAttendanceTestClock()).delete(group.id)
                cloud.uploadPending(source)
                val store = RoomConflictStore(device, testUploadTarget)
                val review = store.create(cloud.snapshot())
                assertEquals(1, review.items.size)
                assertFalse(review.items.single().cloudChoiceAllowed)
                if (choice != ConflictChoice.DEVICE) {
                    assertFailsWith<InvalidConflictChoice> {
                        store.apply(review.id, mapOf(review.items.single().key to choice), cloud.readHead())
                    }
                }
                store.apply(review.id, mapOf(review.items.single().key to ConflictChoice.DEVICE), cloud.readHead())
                assertTrue(device.studentGroupDao().getAll().any { it.name == group.name })
                assertEquals(1, device.studentDao().getAll().size)
                cloud.uploadPending(device)
                RoomPullStore(source, testUploadTarget).apply(cloud.snapshot())
                assertEquals(device.studentDao().getAll().size, source.studentDao().getAll().size)
            }
        }
    }
}
