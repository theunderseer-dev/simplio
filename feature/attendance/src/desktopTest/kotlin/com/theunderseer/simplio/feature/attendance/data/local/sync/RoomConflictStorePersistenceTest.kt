package com.theunderseer.simplio.feature.attendance.data.local.sync

import androidx.room.useWriterConnection
import com.theunderseer.simplio.feature.attendance.data.local.database.createAttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.data.sync.ConflictChoice
import com.theunderseer.simplio.feature.attendance.fake.FakeAttendanceCloud
import com.theunderseer.simplio.feature.attendance.fixtures.registerSyncStudent
import com.theunderseer.simplio.feature.attendance.fixtures.testSyncDatabase
import com.theunderseer.simplio.feature.attendance.fixtures.testUploadTarget
import com.theunderseer.simplio.feature.attendance.fixtures.withConflictDevices
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RoomConflictStorePersistenceTest {
    @Test
    fun `open review survives restart and applied choice resumes from durable outbox`() = runTest {
        val directory = Files.createTempDirectory("simplio-conflict-")
        val path = directory.resolve("attendance.db").toString()
        val source = testSyncDatabase()
        var device = createAttendanceDatabase(path)
        try {
            val student = source.registerSyncStudent().student
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            RoomPullStore(device, testUploadTarget).apply(cloud.snapshot())
            val id = device.studentDao().getAll().single().id
            RoomStudentRepository(device).updateNote(id, "Device note")
            RoomStudentRepository(source).updateNote(student.id, "Cloud note")
            cloud.uploadPending(source)
            val review = RoomConflictStore(device, testUploadTarget).create(cloud.snapshot())
            device.close()
            device = createAttendanceDatabase(path)
            assertEquals("OPEN", device.syncConflictDao().get(review.id)?.status)
            assertEquals("Device note", device.studentDao().getById(id)?.note)
            RoomConflictStore(device, testUploadTarget).apply(
                review.id,
                mapOf(review.items.single().key to ConflictChoice.DEVICE),
                cloud.readHead(),
            )
            device.close()
            device = createAttendanceDatabase(path)
            assertEquals("APPLIED", device.syncConflictDao().get(review.id)?.status)
            assertEquals(1, device.syncPullDao().pendingCount())
            cloud.uploadPending(device)
            assertNull(RoomUploadStore(device).prepare(testUploadTarget))
            val archive = assertNotNull(device.syncConflictDao().get(review.id))
            assertEquals(true, archive.localJson.contains("Device note"))
            assertEquals(true, archive.cloudJson.contains("Cloud note"))
        } finally {
            device.close()
            source.close()
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `archive write failure rolls back chosen rows checkpoint and queue replacement`() = runTest {
        withConflictDevices {
            localNote("Device note")
            remoteNote("Cloud note")
            val frozen = RoomUploadStore(device).prepare(testUploadTarget)
            val review = store.create(cloud.snapshot())
            val pending = RoomSyncOutbox(device).getPending()
            val checkpoint = device.syncUploadDao().getState()
            device.useWriterConnection { connection ->
                connection.usePrepared(
                    """
                    CREATE TRIGGER reject_review_application BEFORE UPDATE ON sync_conflict_reviews
                    WHEN NEW.status = 'APPLIED'
                    BEGIN SELECT RAISE(ABORT, 'injected archive failure'); END
                    """.trimIndent(),
                ) { it.step() }
            }
            assertFailsWith<Exception> {
                store.apply(review.id, mapOf(review.items.single().key to ConflictChoice.CLOUD), cloud.readHead())
            }
            assertEquals("Device note", device.studentDao().getById(localStudentId)?.note)
            assertEquals(pending, RoomSyncOutbox(device).getPending())
            assertEquals(checkpoint, device.syncUploadDao().getState())
            assertEquals(frozen, device.syncUploadDao().getBatch())
            assertEquals("OPEN", device.syncConflictDao().get(review.id)?.status)
        }
    }
}
