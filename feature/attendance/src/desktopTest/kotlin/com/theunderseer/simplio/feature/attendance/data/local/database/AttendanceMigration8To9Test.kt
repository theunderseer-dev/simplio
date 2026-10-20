package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomSyncOutbox
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AttendanceMigration8To9Test {
    @Test
    fun `migration preserves upload batch pending revisions and business data`() = runTest {
        val directory = Files.createTempDirectory("simplio-pull-migration-")
        val path = directory.resolve("attendance.db")
        try {
            val helper = MigrationTestHelper(
                schemaDirectoryPath = Paths.get(checkNotNull(System.getProperty("simplio.room.schemaDirectory"))),
                databasePath = path,
                driver = BundledSQLiteDriver(),
                databaseClass = AttendanceDatabase::class,
            )
            helper.createDatabase(version = 8).use { connection ->
                connection.execSQL("INSERT INTO student_groups (id, name) VALUES (1, 'Group A')")
                connection.execSQL(
                    "INSERT INTO students (id, fullName, groupId, note) VALUES (7, 'Anna', 1, 'Keep note')",
                )
                connection.execSQL(
                    """
                    INSERT INTO package_types (id, name, totalClasses, validityWeeks, price, maxVisitsPerAttendance)
                    VALUES (3, '4-class package', 4, 4, 1400, 1)
                    """.trimIndent(),
                )
                connection.execSQL(
                    """
                    INSERT INTO student_packages (id, studentId, packageTypeId, packageName, totalClasses,
                        validityWeeks, price, purchasedOnEpochDay, startedOnEpochDay, maxVisitsPerAttendance)
                    VALUES (11, 7, 3, '4-class package', 4, 4, 1400, 20728, 20728, 1)
                    """.trimIndent(),
                )
                connection.execSQL(
                    "INSERT INTO attendance (packageId, attendedOnEpochDay, visitCount) VALUES (11, 20728, 1)",
                )
                connection.execSQL(
                    """
                    INSERT INTO sync_records (entityType, localId, cloudId, revision, acknowledgedRevision,
                        isDeleted, serverUpdateTime, aggregateKey)
                    VALUES ('STUDENT', '7', 'student-cloud-id', 2, 1, 0, '2026-10-19T00:00:00Z', 'STUDENT:7')
                    """.trimIndent(),
                )
                connection.execSQL(
                    """
                    INSERT INTO sync_outbox (entityType, localId, revision, operation, mutationId)
                    VALUES ('STUDENT', '7', 2, 'UPSERT', 'pending-mutation')
                    """.trimIndent(),
                )
                connection.execSQL(
                    """
                    INSERT INTO sync_upload_state (id, projectId, userId, headUpdateTime)
                    VALUES (1, 'test-project', 'owner', '2026-10-19T00:00:00Z')
                    """.trimIndent(),
                )
                connection.execSQL(
                    """
                    INSERT INTO sync_upload_batch (id, batchId, snapshotJson, commitBody, attempts, blockedReason)
                    VALUES (1, 'frozen-batch', 'frozen-snapshot', 'frozen-body', 2, 'CONFLICT')
                    """.trimIndent(),
                )
            }
            helper.runMigrationsAndValidate(version = 9, migrations = listOf(AttendanceMigration8To9)).close()
            val database = createAttendanceDatabase(path.toString())
            try {
                assertEquals("Keep note", database.studentDao().getById(7)?.note)
                assertEquals(20728L, database.studentPackageDao().getById(11)?.startedOnEpochDay)
                assertEquals(1, database.attendanceDao().getForPackage(11).single().visitCount)
                assertEquals("pending-mutation", RoomSyncOutbox(database).getPending().single().mutationId)
                assertEquals(2L, RoomSyncOutbox(database).getPending().single().revision)
                val batch = database.syncUploadDao().getBatch()
                assertEquals("frozen-snapshot", batch?.snapshotJson)
                assertEquals("frozen-body", batch?.commitBody)
                assertEquals(2, batch?.attempts)
                assertEquals("CONFLICT", batch?.blockedReason)
                assertEquals("2026-10-19T00:00:00Z", database.syncUploadDao().getState()?.headUpdateTime)
                assertNull(database.syncUploadDao().getState()?.lastPullReadTime)
            } finally {
                database.close()
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
