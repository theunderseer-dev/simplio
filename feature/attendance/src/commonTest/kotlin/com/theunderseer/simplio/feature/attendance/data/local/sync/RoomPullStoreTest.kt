package com.theunderseer.simplio.feature.attendance.data.local.sync

import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.data.sync.PullAction
import com.theunderseer.simplio.feature.attendance.data.sync.UploadPreparationFailure
import com.theunderseer.simplio.feature.attendance.data.sync.UploadProblem
import com.theunderseer.simplio.feature.attendance.fake.FakeAttendanceCloud
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.registerSyncStudent
import com.theunderseer.simplio.feature.attendance.fixtures.seedSyncDefaults
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentEntity
import com.theunderseer.simplio.feature.attendance.fixtures.testUploadTarget
import com.theunderseer.simplio.feature.attendance.fixtures.withSyncDatabases
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RoomPullStoreTest {
    @Test
    fun `new device imports cloud references terms notes and derived activation without echo uploads`() = runTest {
        withSyncDatabases { source, target ->
            source.seedSyncDefaults()
            source.studentDao().insert(testStudentEntity(id = 100))
            source.studentDao().deleteById(100)
            val registered = source.registerSyncStudent()
            RoomStudentRepository(source).updateNote(registered.student.id, "Bring change")
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            target.seedSyncDefaults()
            val snapshot = cloud.snapshot()
            val store = RoomPullStore(target, testUploadTarget)
            assertEquals(PullAction.DOWNLOAD, store.plan(snapshot.head))
            assertEquals(true, store.apply(snapshot))
            val student = target.studentDao().getAll().single()
            assertNotEquals(registered.student.id, student.id)
            assertEquals("Bring change", student.note)
            val pack = assertNotNull(RoomStudentPackageRepository(target).getLatestPackageForStudent(student.id))
            assertEquals(registered.studentPackage.purchasedOn, pack.purchasedOn)
            assertEquals(registered.studentPackage.startedOn, pack.startedOn)
            assertEquals(registered.studentPackage.expiresOn, pack.expiresOn)
            assertEquals(registered.studentPackage.attendanceHistory, pack.attendanceHistory)
            assertEquals(0, target.syncPullDao().pendingCount())
            assertNull(RoomUploadStore(target).prepare(testUploadTarget))
            assertEquals(snapshot.head.readTime, target.syncUploadDao().getState()?.lastPullReadTime)
        }
    }

    @Test
    fun `unknown cloud head never overwrites local student and pending changes`() = runTest {
        withSyncDatabases { source, target ->
            source.registerSyncStudent()
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            val local = target.registerSyncStudent()
            val before = RoomSyncOutbox(target).getPending()
            val error = assertFailsWith<UploadPreparationFailure> {
                RoomPullStore(target, testUploadTarget).plan(cloud.readHead())
            }
            assertEquals(UploadProblem.CONFLICT, error.problem)
            assertEquals(local.student.fullName, target.studentDao().getAll().single().fullName)
            assertEquals(before, RoomSyncOutbox(target).getPending())
            assertNull(target.syncUploadDao().getState())
        }
    }

    @Test
    fun `local edit during download aborts replacement under the write transaction`() = runTest {
        withSyncDatabases { source, target ->
            source.registerSyncStudent()
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            target.seedSyncDefaults()
            val store = RoomPullStore(target, testUploadTarget)
            val head = cloud.readHead()
            assertEquals(PullAction.DOWNLOAD, store.plan(head))
            target.registerSyncStudent()
            val before = RoomSyncOutbox(target).getPending()
            val error = assertFailsWith<UploadPreparationFailure> { store.apply(cloud.readSnapshot(head)) }
            assertEquals(UploadProblem.CONFLICT, error.problem)
            assertEquals(before, RoomSyncOutbox(target).getPending())
            assertNull(target.syncUploadDao().getState())
        }
    }

    @Test
    fun `refresh preserves local IDs and clears activation after remote last attendance removal`() = runTest {
        withSyncDatabases { source, target ->
            val registered = source.registerSyncStudent()
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            target.seedSyncDefaults()
            val store = RoomPullStore(target, testUploadTarget)
            store.apply(cloud.snapshot())
            val id = target.studentDao().getAll().single().id
            val packId = target.studentPackageDao().getLatestForStudent(id)?.id
            RoomStudentPackageRepository(source).removeAttendance(
                registered.studentPackage.id,
                AttendanceTestData.firstClassDate,
            )
            cloud.uploadPending(source)
            store.apply(cloud.snapshot())
            assertEquals(id, target.studentDao().getAll().single().id)
            val pack = assertNotNull(RoomStudentPackageRepository(target).getLatestPackageForStudent(id))
            assertEquals(packId, pack.id)
            assertNull(pack.startedOn)
            assertNull(pack.expiresOn)
            assertEquals(0, pack.usedClasses)
            assertEquals(4, pack.remainingClasses)
            assertEquals(0, target.syncPullDao().pendingCount())
        }
    }

    @Test
    fun `concurrent remote and local notes preserve pending local value and old checkpoint`() = runTest {
        withSyncDatabases { source, target ->
            val sourceStudent = source.registerSyncStudent().student
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            val store = RoomPullStore(target, testUploadTarget)
            store.apply(cloud.snapshot())
            val localId = target.studentDao().getAll().single().id
            RoomStudentRepository(target).updateNote(localId, "Local note")
            val checkpoint = target.syncUploadDao().getState()
            val pending = RoomSyncOutbox(target).getPending()
            RoomStudentRepository(source).updateNote(sourceStudent.id, "Remote note")
            cloud.uploadPending(source)
            val error = assertFailsWith<UploadPreparationFailure> { store.apply(cloud.snapshot()) }
            assertEquals(UploadProblem.CONFLICT, error.problem)
            assertEquals("Local note", target.studentDao().getById(localId)?.note)
            assertEquals(checkpoint, target.syncUploadDao().getState())
            assertEquals(pending, RoomSyncOutbox(target).getPending())
        }
    }

    @Test
    fun `same head allows queued local changes to upload without replacing rows`() = runTest {
        withSyncDatabases { source, target ->
            source.registerSyncStudent()
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            val store = RoomPullStore(target, testUploadTarget)
            store.apply(cloud.snapshot())
            val id = target.studentDao().getAll().single().id
            RoomStudentRepository(target).updateNote(id, "Local edit")
            assertEquals(PullAction.CURRENT, store.plan(cloud.readHead()))
            cloud.uploadPending(target)
            assertEquals(0, target.syncPullDao().pendingCount())
            assertEquals("Local edit", target.studentDao().getById(id)?.note)
        }
    }

    @Test
    fun `only untouched template batch can be replaced during first cloud import`() = runTest {
        withSyncDatabases { source, target ->
            source.registerSyncStudent()
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            target.seedSyncDefaults()
            val uploadStore = RoomUploadStore(target)
            val batch = assertNotNull(uploadStore.prepare(testUploadTarget))
            uploadStore.block(batch.batchId, UploadProblem.CONFLICT)
            val store = RoomPullStore(target, testUploadTarget)
            assertEquals(false, store.mustRecoverBatch())
            store.apply(cloud.snapshot())
            assertNull(target.syncUploadDao().getBatch())
            assertEquals(1, target.studentDao().count())
            assertEquals(0, target.syncPullDao().pendingCount())
        }
    }

    @Test
    fun `user authored frozen request must be recovered before pulling`() = runTest {
        withSyncDatabases { source, target ->
            val cloud = FakeAttendanceCloud()
            source.registerSyncStudent()
            cloud.uploadPending(source)
            target.registerSyncStudent()
            val batch = RoomUploadStore(target).prepare(testUploadTarget)
            val store = RoomPullStore(target, testUploadTarget)
            assertEquals(true, store.mustRecoverBatch())
            assertFailsWith<UploadPreparationFailure> { store.apply(cloud.snapshot()) }
            assertEquals(batch, target.syncUploadDao().getBatch())
        }
    }

    @Test
    fun `invalid graph is rejected before any local business or checkpoint writes`() = runTest {
        withSyncDatabases { source, target ->
            source.registerSyncStudent()
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            target.seedSyncDefaults()
            val before = RoomSyncOutbox(target).getPending()
            val snapshot = cloud.snapshot()
            val broken = snapshot.copy(
                documents = snapshot.documents.map {
                    if (it.type == SyncEntityType.STUDENT) {
                        it.copy(
                            fields = buildJsonObject {
                                it.fields.forEach { (key, value) -> put(key, value) }
                                put("groupId", buildJsonObject { put("stringValue", "missing-group") })
                            },
                        )
                    } else {
                        it
                    }
                },
            )
            val error =
                assertFailsWith<UploadPreparationFailure> { RoomPullStore(target, testUploadTarget).apply(broken) }
            assertEquals(UploadProblem.INVALID_DATA, error.problem)
            assertEquals(before, RoomSyncOutbox(target).getPending())
            assertEquals(0, target.studentDao().count())
            assertNull(target.syncUploadDao().getState())
        }
    }

    @Test
    fun `tombstone IDs stay reserved when a new student is registered`() = runTest {
        withSyncDatabases { source, target ->
            val registered = source.registerSyncStudent()
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            RoomStudentRepository(source).delete(registered.student.id)
            cloud.uploadPending(source)
            target.seedSyncDefaults()
            RoomPullStore(target, testUploadTarget).apply(cloud.snapshot())
            val tombstones = target.syncPullDao().records().filter { it.isDeleted }
            val next = target.registerSyncStudent()
            assertEquals(
                false,
                tombstones.any {
                    it.entityType == SyncEntityType.STUDENT && it.localId == next.student.id.toString()
                },
            )
            assertEquals(
                false,
                tombstones.any {
                    it.entityType == SyncEntityType.STUDENT_PACKAGE && it.localId == next.studentPackage.id.toString()
                },
            )
            assertEquals(3, RoomSyncOutbox(target).getPending().size)
            assertEquals(true, target.syncPullDao().records().containsAll(tombstones))
        }
    }

    @Test
    fun `account project mismatch and cloud reset leave existing local data intact`() = runTest {
        withSyncDatabases { source, target ->
            source.registerSyncStudent()
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            val snapshot = cloud.snapshot()
            val store = RoomPullStore(target, testUploadTarget)
            store.apply(snapshot)
            val before = target.syncUploadDao().getState()
            listOf(
                testUploadTarget.copy(userId = "other"),
                testUploadTarget.copy(projectId = "other-project"),
            ).forEach {
                val error = assertFailsWith<UploadPreparationFailure> {
                    store.apply(snapshot.copy(head = snapshot.head.copy(target = it)))
                }
                assertEquals(UploadProblem.TARGET_MISMATCH, error.problem)
            }
            assertFailsWith<UploadPreparationFailure> { store.plan(snapshot.head.copy(updateTime = null)) }
            assertEquals(before, target.syncUploadDao().getState())
            assertEquals(1, target.studentDao().count())
        }
    }

    @Test
    fun `empty cloud does not erase pending local registration`() = runTest {
        withSyncDatabases { _, target ->
            target.registerSyncStudent()
            val before = RoomSyncOutbox(target).getPending()
            val cloud = FakeAttendanceCloud()
            val store = RoomPullStore(target, testUploadTarget)
            assertEquals(false, store.apply(cloud.snapshot()))
            assertEquals(before, RoomSyncOutbox(target).getPending())
            assertEquals(1, target.studentDao().count())
            cloud.uploadPending(target)
            assertEquals(0, target.syncPullDao().pendingCount())
        }
    }

    @Test
    fun `missing acknowledged document is corruption rather than an implied delete`() = runTest {
        withSyncDatabases { source, target ->
            source.registerSyncStudent()
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            val snapshot = cloud.snapshot()
            val store = RoomPullStore(target, testUploadTarget)
            store.apply(snapshot)
            val checkpoint = target.syncUploadDao().getState()
            val error = assertFailsWith<UploadPreparationFailure> {
                store.apply(
                    snapshot.copy(
                        documents = snapshot.documents.filter {
                            it.type != SyncEntityType.ATTENDANCE
                        },
                    ),
                )
            }
            assertEquals(UploadProblem.INVALID_DATA, error.problem)
            assertEquals(checkpoint, target.syncUploadDao().getState())
            val id = target.studentDao().getAll().single().id
            assertEquals(1, RoomStudentPackageRepository(target).getLatestPackageForStudent(id)?.usedClasses)
        }
    }

    @Test
    fun `package purchased without attendance remains unactivated after import`() = runTest {
        withSyncDatabases { source, target ->
            val original = source.registerSyncStudent(recordFirstAttendance = false).studentPackage
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            RoomPullStore(target, testUploadTarget).apply(cloud.snapshot())
            val id = target.studentDao().getAll().single().id
            val pack = assertNotNull(RoomStudentPackageRepository(target).getLatestPackageForStudent(id))
            assertEquals(original.purchasedOn, pack.purchasedOn)
            assertEquals(0, pack.usedClasses)
            assertEquals(4, pack.remainingClasses)
            assertNull(pack.startedOn)
            assertNull(pack.expiresOn)
        }
    }

    @Test
    fun `older pinned snapshot cannot regress checkpoint or a remote note`() = runTest {
        withSyncDatabases { source, target ->
            val student = source.registerSyncStudent().student
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            val old = cloud.snapshot()
            val store = RoomPullStore(target, testUploadTarget)
            store.apply(old)
            RoomStudentRepository(source).updateNote(student.id, "Latest note")
            cloud.uploadPending(source)
            store.apply(cloud.snapshot())
            val checkpoint = target.syncUploadDao().getState()
            val error = assertFailsWith<UploadPreparationFailure> { store.apply(old) }
            assertEquals(UploadProblem.CONFLICT, error.problem)
            assertEquals(checkpoint, target.syncUploadDao().getState())
            assertEquals("Latest note", target.studentDao().getAll().single().note)
        }
    }
}
