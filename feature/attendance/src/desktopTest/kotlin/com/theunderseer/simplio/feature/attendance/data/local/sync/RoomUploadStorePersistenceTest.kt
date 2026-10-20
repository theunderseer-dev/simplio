package com.theunderseer.simplio.feature.attendance.data.local.sync

import androidx.room.useWriterConnection
import com.theunderseer.simplio.feature.attendance.data.local.database.createAttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.data.sync.UploadProblem
import com.theunderseer.simplio.feature.attendance.fixtures.acknowledgeCatalog
import com.theunderseer.simplio.feature.attendance.fixtures.registerSyncStudent
import com.theunderseer.simplio.feature.attendance.fixtures.testUploadAcknowledgement
import com.theunderseer.simplio.feature.attendance.fixtures.testUploadTarget
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RoomUploadStorePersistenceTest {
    @Test
    fun `reopened database preserves exact request and newer local changes`() = runTest {
        val directory = Files.createTempDirectory("simplio-upload-reopen-")
        val path = directory.resolve("attendance.db").toString()
        try {
            val original = createAttendanceDatabase(path).let { database ->
                try {
                    val student = database.registerSyncStudent().student
                    val store = RoomUploadStore(database)
                    store.acknowledgeCatalog()
                    val batch = assertNotNull(store.prepare(testUploadTarget))
                    RoomStudentRepository(database).updateNote(student.id, "newer note")
                    store.recordAttempt(batch.batchId)
                    store.block(batch.batchId, UploadProblem.CONFLICT)
                    assertNotNull(database.syncUploadDao().getBatch())
                } finally {
                    database.close()
                }
            }
            val reopened = createAttendanceDatabase(path)
            try {
                val store = RoomUploadStore(reopened)
                val batch = assertNotNull(store.prepare(testUploadTarget))
                assertEquals(original, batch)
                assertEquals(1, batch.attempts)
                assertEquals(UploadProblem.CONFLICT.name, batch.blockedReason)
                assertEquals(false, batch.commitBody.contains("newer note"))
                assertEquals("newer note", RoomStudentRepository(reopened).getAll().single().note)
                store.acknowledge(batch, testUploadAcknowledgement(store.snapshot(batch)))
                assertNull(reopened.syncUploadDao().getBatch())
                assertEquals(1, RoomSyncOutbox(reopened).getPending().size)
            } finally {
                reopened.close()
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `checkpoint failure rolls back acknowledgement and batch removal`() = runTest {
        val directory = Files.createTempDirectory("simplio-upload-rollback-")
        val database = createAttendanceDatabase(directory.resolve("attendance.db").toString())
        try {
            database.registerSyncStudent()
            val store = RoomUploadStore(database)
            val batch = assertNotNull(store.prepare(testUploadTarget))
            val pending = RoomSyncOutbox(database).getPending()
            database.useWriterConnection { connection ->
                connection.usePrepared(
                    """
                    CREATE TRIGGER fail_checkpoint BEFORE UPDATE ON sync_upload_state
                    BEGIN SELECT RAISE(ABORT, 'Simulated checkpoint failure'); END
                    """.trimIndent(),
                ) { it.step() }
            }
            assertFailsWith<Exception> { store.acknowledge(batch, testUploadAcknowledgement(store.snapshot(batch))) }
            assertEquals(pending, RoomSyncOutbox(database).getPending())
            assertEquals(batch, database.syncUploadDao().getBatch())
            assertNull(database.syncUploadDao().getState()?.headUpdateTime)
        } finally {
            database.close()
            directory.toFile().deleteRecursively()
        }
    }
}
