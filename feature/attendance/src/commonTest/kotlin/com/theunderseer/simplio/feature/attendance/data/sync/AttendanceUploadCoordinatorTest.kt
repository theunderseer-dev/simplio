package com.theunderseer.simplio.feature.attendance.data.sync

import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomSyncOutbox
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomUploadStore
import com.theunderseer.simplio.feature.attendance.fixtures.registerSyncStudent
import com.theunderseer.simplio.feature.attendance.fixtures.testSyncDatabase
import com.theunderseer.simplio.feature.attendance.fixtures.testUploadAcknowledgement
import com.theunderseer.simplio.feature.attendance.fixtures.testUploadTarget
import com.theunderseer.simplio.shared.firebase.FirestoreFailure
import com.theunderseer.simplio.shared.firebase.FirestoreFailureKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class AttendanceUploadCoordinatorTest {
    @Test
    fun `uploads pending catalog and student then becomes idle`() = runTest {
        val database = testSyncDatabase()
        try {
            database.registerSyncStudent()
            var calls = 0
            val coordinator = AttendanceUploadCoordinator(RoomUploadStore(database), { snapshot, _ ->
                calls++
                testUploadAcknowledgement(snapshot)
            }, testUploadTarget)
            assertEquals(AttendanceUploadStatus.Uploading, coordinator.uploadNext())
            assertEquals(AttendanceUploadStatus.Uploading, coordinator.uploadNext())
            assertEquals(AttendanceUploadStatus.Idle, coordinator.uploadNext())
            assertEquals(2, calls)
            assertEquals(emptyList(), RoomSyncOutbox(database).getPending())
        } finally {
            database.close()
        }
    }

    @Test
    fun `network retry sends exact persisted request despite newer edit`() = runTest {
        val database = testSyncDatabase()
        try {
            val student = database.registerSyncStudent().student
            val store = RoomUploadStore(database)
            val bodies = mutableListOf<String>()
            var shouldFail = false
            val coordinator = AttendanceUploadCoordinator(store, { snapshot, body ->
                bodies += body
                if (shouldFail) throw FirestoreFailure(FirestoreFailureKind.RETRYABLE)
                testUploadAcknowledgement(snapshot)
            }, testUploadTarget)
            coordinator.uploadNext()
            shouldFail = true
            assertIs<AttendanceUploadStatus.WaitingToRetry>(coordinator.uploadNext())
            val frozen = assertNotNull(database.syncUploadDao().getBatch())
            RoomStudentRepository(database).updateNote(student.id, "later note")
            shouldFail = false
            coordinator.uploadNext()
            assertEquals(frozen.commitBody, bodies.last())
            assertEquals(1, RoomSyncOutbox(database).getPending().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun `conflict remains paused across coordinator recreation`() = runTest {
        val database = testSyncDatabase()
        try {
            database.registerSyncStudent()
            var calls = 0
            val remote = AttendanceUploadRemote { _, _ ->
                calls++
                throw FirestoreFailure(FirestoreFailureKind.CONFLICT)
            }
            repeat(2) {
                val result = AttendanceUploadCoordinator(
                    RoomUploadStore(database),
                    remote,
                    testUploadTarget,
                ).uploadNext()
                assertEquals(AttendanceUploadStatus.Paused(UploadProblem.CONFLICT), result)
            }
            assertEquals(1, calls)
            assertEquals(8, RoomSyncOutbox(database).getPending().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun `cancellation keeps request and pending work`() = runTest {
        val database = testSyncDatabase()
        try {
            database.registerSyncStudent()
            val coordinator = AttendanceUploadCoordinator(RoomUploadStore(database), { _, _ ->
                throw CancellationException("test")
            }, testUploadTarget)
            assertFailsWith<CancellationException> { coordinator.uploadNext() }
            assertNotNull(database.syncUploadDao().getBatch())
            assertEquals(8, RoomSyncOutbox(database).getPending().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun `authentication and permission failures have distinct states`() = runTest {
        val database = testSyncDatabase()
        try {
            database.registerSyncStudent()
            val store = RoomUploadStore(database)
            listOf(
                FirestoreFailureKind.AUTHENTICATION to UploadProblem.AUTHENTICATION,
                FirestoreFailureKind.PERMISSION to UploadProblem.PERMISSION,
            ).forEach { (kind, problem) ->
                store.retry()
                val coordinator = AttendanceUploadCoordinator(
                    store,
                    { _, _ -> throw FirestoreFailure(kind) },
                    testUploadTarget,
                )
                assertEquals(AttendanceUploadStatus.Paused(problem), coordinator.uploadNext())
            }
            assertEquals(8, RoomSyncOutbox(database).getPending().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun `repeated ambiguous server failure pauses after one automatic retry`() = runTest {
        val database = testSyncDatabase()
        try {
            database.registerSyncStudent()
            val coordinator = AttendanceUploadCoordinator(RoomUploadStore(database), { _, _ ->
                throw FirestoreFailure(FirestoreFailureKind.INTERNAL)
            }, testUploadTarget)
            assertEquals(AttendanceUploadStatus.WaitingToRetry(1), coordinator.uploadNext())
            assertEquals(AttendanceUploadStatus.Paused(UploadProblem.SERVICE), coordinator.uploadNext())
            assertEquals(2, database.syncUploadDao().getBatch()?.attempts)
        } finally {
            database.close()
        }
    }

    @Test
    fun `running coordinator notices local edits after becoming idle`() = runTest {
        val database = testSyncDatabase()
        try {
            val student = database.registerSyncStudent().student
            val store = RoomUploadStore(database)
            val uploads = Channel<UploadSnapshot>(Channel.UNLIMITED)
            val coordinator = AttendanceUploadCoordinator(store, { snapshot, _ ->
                uploads.send(snapshot)
                testUploadAcknowledgement(snapshot)
            }, testUploadTarget)
            val job = backgroundScope.launch { coordinator.run() }
            assertEquals(5, uploads.receive().documents.size)
            assertEquals(3, uploads.receive().documents.size)
            store.observeWork().first { !it }
            RoomStudentRepository(database).updateNote(student.id, "new note")
            val snapshot = uploads.receive()
            assertEquals(1, snapshot.documents.size)
            assertEquals(true, snapshot.documents.single().fields.toString().contains("new note"))
            store.observeWork().first { !it }
            job.cancelAndJoin()
        } finally {
            database.close()
        }
    }
}
