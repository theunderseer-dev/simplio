package com.theunderseer.simplio.feature.attendance.data.remote

import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomSyncOutbox
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomUploadStore
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceUploadCoordinator
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceUploadStatus
import com.theunderseer.simplio.feature.attendance.data.sync.UploadProblem
import com.theunderseer.simplio.feature.attendance.fixtures.registerSyncStudent
import com.theunderseer.simplio.feature.attendance.fixtures.testSyncDatabase
import com.theunderseer.simplio.feature.attendance.fixtures.withFirestoreEmulator
import com.theunderseer.simplio.shared.firebase.FirestoreFailure
import com.theunderseer.simplio.shared.firebase.FirestoreFailureKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FirestoreUploadEmulatorTest {
    @Test
    fun `second database cannot overwrite existing cloud head`() = runTest {
        withFirestoreEmulator { client, target ->
            val first = testSyncDatabase()
            val second = testSyncDatabase()
            try {
                first.registerSyncStudent()
                second.registerSyncStudent()
                val remote = FirestoreAttendanceUploadRemote(client)
                assertEquals(
                    AttendanceUploadStatus.Uploading,
                    AttendanceUploadCoordinator(RoomUploadStore(first), remote, target).uploadNext(),
                )
                val store = RoomUploadStore(second)
                val batch = assertNotNull(store.prepare(target))
                assertEquals(
                    AttendanceUploadStatus.Paused(UploadProblem.CONFLICT),
                    AttendanceUploadCoordinator(store, remote, target).uploadNext(),
                )
                assertNull(client.getDocument(FirestoreUploadProtocol.RECEIPT_COLLECTION, batch.batchId))
                assertEquals(8, RoomSyncOutbox(second).getPending().size)
            } finally {
                first.close()
                second.close()
            }
        }
    }

    @Test
    fun `failed document precondition rolls back entire commit`() = runTest {
        withFirestoreEmulator { client, target ->
            val database = testSyncDatabase()
            try {
                database.registerSyncStudent()
                val store = RoomUploadStore(database)
                val remote = FirestoreAttendanceUploadRemote(client)
                AttendanceUploadCoordinator(store, remote, target).uploadNext()
                val originalHead = client.getDocument(
                    FirestoreUploadProtocol.HEAD_COLLECTION,
                    FirestoreUploadProtocol.HEAD_ID,
                )
                val snapshot = store.snapshot(assertNotNull(store.prepare(target)))
                val last = snapshot.documents.last()
                val bad = snapshot.copy(
                    documents = snapshot.documents.dropLast(1) +
                        last.copy(change = last.change.copy(serverUpdateTime = "2000-01-01T00:00:00Z")),
                )
                assertEquals(
                    FirestoreFailureKind.CONFLICT,
                    assertFailsWith<FirestoreFailure> {
                        remote.sendOrRecover(bad, FirestoreUploadProtocol.commitBody(bad))
                    }.kind,
                )
                snapshot.documents.forEach {
                    assertNull(
                        client.getDocument(
                            FirestoreUploadProtocol.collection(it.change.entityType),
                            it.change.cloudId,
                        ),
                    )
                }
                assertNull(client.getDocument(FirestoreUploadProtocol.RECEIPT_COLLECTION, snapshot.batchId))
                assertEquals(
                    originalHead,
                    client.getDocument(FirestoreUploadProtocol.HEAD_COLLECTION, FirestoreUploadProtocol.HEAD_ID),
                )
            } finally {
                database.close()
            }
        }
    }
}
