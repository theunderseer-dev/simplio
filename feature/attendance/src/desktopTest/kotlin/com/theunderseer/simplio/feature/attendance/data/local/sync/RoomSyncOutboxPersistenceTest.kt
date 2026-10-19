package com.theunderseer.simplio.feature.attendance.data.local.sync

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.theunderseer.simplio.feature.attendance.data.local.database.createAttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.registerSyncStudent
import com.theunderseer.simplio.feature.attendance.fixtures.seedSyncDefaults
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RoomSyncOutboxPersistenceTest {
    @Test
    fun `pending edits tombstones and acknowledgement survive reopening database`() = runTest {
        val directory = Files.createTempDirectory("simplio-outbox-")
        val path = directory.resolve("attendance.db").toString()
        try {
            val original = createAttendanceDatabase(path).let { database ->
                try {
                    val registered = database.registerSyncStudent()
                    RoomStudentRepository(database).updateNote(registered.student.id, "Pending note")
                    RoomStudentPackageRepository(database).removeAttendance(
                        registered.studentPackage.id,
                        AttendanceTestData.firstClassDate,
                    )
                    val outbox = RoomSyncOutbox(database)
                    val student = outbox.getPending().single { it.entityType == SyncEntityType.STUDENT }
                    outbox.acknowledge(student, "persisted-server-version")
                    outbox.getPending()
                } finally {
                    database.close()
                }
            }
            createAttendanceDatabase(path).let { database ->
                try {
                    assertEquals(original, RoomSyncOutbox(database).getPending())
                    val student = RoomStudentRepository(database).getAll().single()
                    assertEquals("Pending note", student.note)
                    val record =
                        assertNotNull(database.syncDao().getRecord(SyncEntityType.STUDENT, student.id.toString()))
                    assertEquals(record.revision, record.acknowledgedRevision)
                    assertEquals("persisted-server-version", record.serverUpdateTime)
                    assertEquals(
                        SyncOperation.DELETE,
                        original.single {
                            it.entityType == SyncEntityType.ATTENDANCE
                        }.operation,
                    )
                } finally {
                    database.close()
                }
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `outbox failure rolls back complete registration including nested repository transactions`() = runTest {
        val directory = Files.createTempDirectory("simplio-outbox-rollback-")
        val path = directory.resolve("attendance.db").toString()
        val database = createAttendanceDatabase(path)
        try {
            database.seedSyncDefaults()
            val original = RoomSyncOutbox(database).getPending()
            BundledSQLiteDriver().open(path).use { connection ->
                connection.execSQL(
                    """
                    CREATE TRIGGER reject_package_outbox BEFORE INSERT ON sync_outbox
                    WHEN NEW.entityType = 'STUDENT_PACKAGE'
                    BEGIN SELECT RAISE(ABORT, 'Simulated outbox failure'); END
                    """.trimIndent(),
                )
            }

            val failure = assertFailsWith<Exception> { database.registerSyncStudent() }
            assertTrue(failure.message.orEmpty().contains("Simulated outbox failure"))

            assertTrue(database.studentDao().getAll().isEmpty())
            assertEquals(original, RoomSyncOutbox(database).getPending())
            assertTrue(database.studentPackageDao().getForStudent(1).isEmpty())
        } finally {
            database.close()
            directory.toFile().deleteRecursively()
        }
    }
}
