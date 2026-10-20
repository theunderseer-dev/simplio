package com.theunderseer.simplio.feature.attendance.data.remote

import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomUploadStore
import com.theunderseer.simplio.feature.attendance.data.local.sync.SyncEntityType
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceCloudHead
import com.theunderseer.simplio.feature.attendance.data.sync.AttendancePullRemote
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceSyncStatus
import com.theunderseer.simplio.feature.attendance.data.sync.ConflictChoice
import com.theunderseer.simplio.feature.attendance.data.sync.ConflictReviewState
import com.theunderseer.simplio.feature.attendance.data.sync.UploadProblem
import com.theunderseer.simplio.feature.attendance.fixtures.registerSyncStudent
import com.theunderseer.simplio.feature.attendance.fixtures.testFirestoreSyncCoordinator
import com.theunderseer.simplio.feature.attendance.fixtures.withFirestoreEmulator
import com.theunderseer.simplio.feature.attendance.fixtures.withSyncDatabases
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FirestoreConflictEmulatorTest {
    @Test
    fun `device choice rebases a fenced prepared request and uploads the chosen note`() = runTest {
        withFirestoreEmulator { client, target ->
            withSyncDatabases { source, device ->
                val student = source.registerSyncStudent().student
                val first = testFirestoreSyncCoordinator(source, client, target)
                repeat(4) { first.syncOnce() }
                val second = testFirestoreSyncCoordinator(device, client, target)
                second.syncOnce()
                val localId = device.studentDao().getAll().single().id
                RoomStudentRepository(device).updateNote(localId, "Chosen device note")
                val frozen = assertNotNull(RoomUploadStore(device).prepare(target))
                RoomStudentRepository(source).updateNote(student.id, "Cloud note")
                first.syncOnce()
                assertEquals(AttendanceSyncStatus.Paused(UploadProblem.CONFLICT), second.syncOnce())
                second.openReview()
                val review = assertIs<ConflictReviewState.Ready>(second.review.value).review
                second.applyReview(review.id, mapOf(review.items.single().key to ConflictChoice.DEVICE))
                assertEquals(AttendanceSyncStatus.Syncing, second.syncOnce())
                assertEquals(AttendanceSyncStatus.Idle, first.syncOnce())
                assertEquals("Chosen device note", source.studentDao().getById(student.id)?.note)
                assertNull(client.getDocument(FirestoreUploadProtocol.RECEIPT_COLLECTION, frozen.batchId))
                assertEquals("APPLIED", device.syncConflictDao().get(review.id)?.status)
                assertEquals(true, device.syncConflictDao().get(review.id)?.localJson?.contains(frozen.batchId))
            }
        }
    }

    @Test
    fun `cloud choice clears pending work without writing a replacement cloud head`() = runTest {
        withFirestoreEmulator { client, target ->
            withSyncDatabases { source, device ->
                val student = source.registerSyncStudent().student
                val first = testFirestoreSyncCoordinator(source, client, target)
                repeat(4) { first.syncOnce() }
                val second = testFirestoreSyncCoordinator(device, client, target)
                second.syncOnce()
                val id = device.studentDao().getAll().single().id
                RoomStudentRepository(device).updateNote(id, "Device note")
                RoomStudentRepository(source).updateNote(student.id, "Chosen cloud note")
                first.syncOnce()
                second.syncOnce()
                second.openReview()
                val review = assertIs<ConflictReviewState.Ready>(second.review.value).review
                val head = client.getDocument(FirestoreUploadProtocol.HEAD_COLLECTION, FirestoreUploadProtocol.HEAD_ID)
                second.applyReview(review.id, mapOf(review.items.single().key to ConflictChoice.CLOUD))
                assertEquals(AttendanceSyncStatus.Idle, second.syncOnce())
                assertEquals(
                    head,
                    client.getDocument(FirestoreUploadProtocol.HEAD_COLLECTION, FirestoreUploadProtocol.HEAD_ID),
                )
                assertEquals("Chosen cloud note", device.studentDao().getById(id)?.note)
                assertEquals(0, device.syncPullDao().pendingCount())
            }
        }
    }

    @Test
    fun `remote write after resolution head check is rejected by actual commit precondition`() = runTest {
        withFirestoreEmulator { client, target ->
            withSyncDatabases { source, device ->
                val student = source.registerSyncStudent().student
                val first = testFirestoreSyncCoordinator(source, client, target)
                repeat(4) { first.syncOnce() }
                var race = false
                val reads = FirestoreAttendancePullRemote(client)
                val remote = object : AttendancePullRemote {
                    override suspend fun readHead() = reads.readHead().also {
                        if (race) {
                            race = false
                            RoomStudentRepository(source).updateNote(student.id, "Newest remote note")
                            first.syncOnce()
                        }
                    }
                    override suspend fun readSnapshot(head: AttendanceCloudHead) = reads.readSnapshot(head)
                }
                val second = testFirestoreSyncCoordinator(device, client, target, remote)
                second.syncOnce()
                val id = device.studentDao().getAll().single().id
                RoomStudentRepository(device).updateNote(id, "Chosen device note")
                RoomStudentRepository(source).updateNote(student.id, "Cloud note")
                first.syncOnce()
                second.syncOnce()
                second.openReview()
                val review = assertIs<ConflictReviewState.Ready>(second.review.value).review
                second.applyReview(review.id, mapOf(review.items.single().key to ConflictChoice.DEVICE))
                race = true
                assertEquals(AttendanceSyncStatus.Paused(UploadProblem.CONFLICT), second.syncOnce())
                val cloudId = assertNotNull(
                    source.syncDao().getRecord(SyncEntityType.STUDENT, student.id.toString()),
                ).cloudId
                val current = assertNotNull(client.getDocument("students", cloudId))
                assertEquals(
                    "Newest remote note",
                    current.fields.getValue("note").jsonObject
                        .getValue("stringValue").jsonPrimitive.content,
                )
                assertEquals("Chosen device note", device.studentDao().getById(id)?.note)
                assertEquals(1, device.syncPullDao().pendingCount())
            }
        }
    }
}
