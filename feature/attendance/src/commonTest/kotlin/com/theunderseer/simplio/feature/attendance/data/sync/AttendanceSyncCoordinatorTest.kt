package com.theunderseer.simplio.feature.attendance.data.sync

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomPullStore
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomUploadStore
import com.theunderseer.simplio.feature.attendance.fake.FakeAttendanceCloud
import com.theunderseer.simplio.feature.attendance.fixtures.registerSyncStudent
import com.theunderseer.simplio.feature.attendance.fixtures.seedSyncDefaults
import com.theunderseer.simplio.feature.attendance.fixtures.testUploadTarget
import com.theunderseer.simplio.feature.attendance.fixtures.withSyncDatabases
import com.theunderseer.simplio.shared.firebase.FirestoreFailure
import com.theunderseer.simplio.shared.firebase.FirestoreFailureKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AttendanceSyncCoordinatorTest {
    @Test
    fun `first sync imports before attempting template upload`() = runTest {
        withSyncDatabases { source, target ->
            source.registerSyncStudent()
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            val before = cloud.uploads
            target.seedSyncDefaults()
            assertEquals(AttendanceSyncStatus.Idle, coordinator(target, cloud).syncOnce())
            assertEquals(before, cloud.uploads)
            assertEquals(1, target.studentDao().count())
            assertEquals(0, target.syncPullDao().pendingCount())
        }
    }

    @Test
    fun `unchanged cloud head skips collection downloads and uploads local edits`() = runTest {
        withSyncDatabases { source, target ->
            source.registerSyncStudent()
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            val sync = coordinator(target, cloud)
            sync.syncOnce()
            val downloads = cloud.downloads
            val id = target.studentDao().getAll().single().id
            RoomStudentRepository(target).updateNote(id, "New note")
            assertEquals(AttendanceSyncStatus.Syncing, sync.syncOnce())
            assertEquals(AttendanceSyncStatus.Idle, sync.syncOnce())
            assertEquals(downloads, cloud.downloads)
            assertEquals(0, target.syncPullDao().pendingCount())
        }
    }

    @Test
    fun `network failure keeps local data and retries fresh snapshot`() = runTest {
        withSyncDatabases { source, target ->
            source.registerSyncStudent()
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            var offline = true
            val remote = object : AttendancePullRemote {
                override suspend fun readHead(): AttendanceCloudHead {
                    if (offline) throw FirestoreFailure(FirestoreFailureKind.RETRYABLE)
                    return cloud.readHead()
                }
                override suspend fun readSnapshot(head: AttendanceCloudHead) = cloud.readSnapshot(head)
            }
            target.seedSyncDefaults()
            val sync = coordinator(target, cloud, remote)
            assertEquals(AttendanceSyncStatus.WaitingToRetry(1), sync.syncOnce())
            assertEquals(5, target.syncPullDao().pendingCount())
            offline = false
            assertEquals(AttendanceSyncStatus.Idle, sync.syncOnce())
            assertEquals(1, target.studentDao().count())
        }
    }

    @Test
    fun `pending local changes and remote changes pause without sending`() = runTest {
        withSyncDatabases { source, target ->
            val student = source.registerSyncStudent().student
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            val sync = coordinator(target, cloud)
            sync.syncOnce()
            RoomStudentRepository(target).updateNote(target.studentDao().getAll().single().id, "Local note")
            RoomStudentRepository(source).updateNote(student.id, "Remote note")
            cloud.uploadPending(source)
            val before = cloud.uploads
            assertEquals(AttendanceSyncStatus.Paused(UploadProblem.CONFLICT), sync.syncOnce())
            assertEquals(before, cloud.uploads)
            assertEquals(1, target.syncPullDao().pendingCount())
            assertEquals("Local note", target.studentDao().getAll().single().note)
        }
    }

    @Test
    fun `authentication permission and malformed responses remain distinct`() = runTest {
        withSyncDatabases { _, target ->
            val cloud = FakeAttendanceCloud()
            val cases = listOf(
                FirestoreFailureKind.AUTHENTICATION to UploadProblem.AUTHENTICATION,
                FirestoreFailureKind.PERMISSION to UploadProblem.PERMISSION,
            )
            cases.forEach { (kind, expected) ->
                val remote = object : AttendancePullRemote {
                    override suspend fun readHead(): AttendanceCloudHead = throw FirestoreFailure(kind)
                    override suspend fun readSnapshot(head: AttendanceCloudHead) = cloud.readSnapshot(head)
                }
                assertEquals(AttendanceSyncStatus.Paused(expected), coordinator(target, cloud, remote).syncOnce())
            }
            val broken = object : AttendancePullRemote {
                override suspend fun readHead(): AttendanceCloudHead =
                    throw FirestoreFailure(FirestoreFailureKind.INVALID_RESPONSE)
                override suspend fun readSnapshot(head: AttendanceCloudHead) = cloud.readSnapshot(head)
            }
            val sync = coordinator(target, cloud, broken)
            assertEquals(AttendanceSyncStatus.WaitingToRetry(1), sync.syncOnce())
            assertEquals(AttendanceSyncStatus.Paused(UploadProblem.SERVICE), sync.syncOnce())
        }
    }

    @Test
    fun `cancelled snapshot download does not advance local checkpoint`() = runTest {
        withSyncDatabases { _, target ->
            val cloud = FakeAttendanceCloud()
            val remote = object : AttendancePullRemote {
                override suspend fun readHead() = cloud.readHead()
                override suspend fun readSnapshot(head: AttendanceCloudHead): AttendanceCloudSnapshot =
                    throw CancellationException("test")
            }
            assertFailsWith<CancellationException> { coordinator(target, cloud, remote).syncOnce() }
            assertNull(target.syncUploadDao().getState())
        }
    }

    @Test
    fun `running sync polls remote changes while local data stays idle`() = runTest {
        withSyncDatabases { source, target ->
            val student = source.registerSyncStudent().student
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            val sync = coordinator(target, cloud, pollMillis = 20)
            val job = backgroundScope.launch { sync.run() }
            target.invalidationTracker.createFlow("students").first { target.studentDao().count() == 1 }
            RoomStudentRepository(source).updateNote(student.id, "Remote update")
            cloud.uploadPending(source)
            target.invalidationTracker.createFlow("students").first {
                target.studentDao().getAll().singleOrNull()?.note == "Remote update"
            }
            assertEquals(0, target.syncPullDao().pendingCount())
            job.cancelAndJoin()
        }
    }

    private fun coordinator(
        database: AttendanceDatabase,
        cloud: FakeAttendanceCloud,
        remote: AttendancePullRemote = cloud,
        pollMillis: Long = 60_000,
    ) = AttendanceSyncCoordinator(
        store = RoomPullStore(database, testUploadTarget),
        remote = remote,
        uploader = AttendanceUploadCoordinator(RoomUploadStore(database), cloud, testUploadTarget),
        pollMillis = pollMillis,
    )
}
