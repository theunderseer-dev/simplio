package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals

class AttendanceMigration9To10Test {
    @Test
    fun `migration adds empty review archive and preserves existing data and sync checkpoint`() = runTest {
        val directory = Files.createTempDirectory("simplio-review-migration-")
        val path = directory.resolve("attendance.db")
        try {
            val helper = MigrationTestHelper(
                schemaDirectoryPath = Paths.get(checkNotNull(System.getProperty("simplio.room.schemaDirectory"))),
                databasePath = path,
                driver = BundledSQLiteDriver(),
                databaseClass = AttendanceDatabase::class,
            )
            helper.createDatabase(version = 9).use { connection ->
                connection.execSQL("INSERT INTO student_groups (id, name) VALUES (1, 'Group A')")
                connection.execSQL(
                    "INSERT INTO students (id, fullName, groupId, note) VALUES (7, 'Anna', 1, 'Keep note')",
                )
                connection.execSQL(
                    """
                    INSERT INTO sync_upload_state (id, projectId, userId, headUpdateTime, lastPullReadTime)
                    VALUES (1, 'test-project', 'owner', '2026-10-19T00:00:00Z', '2026-10-19T00:00:01Z')
                    """.trimIndent(),
                )
                connection.execSQL(
                    """
                    INSERT INTO sync_upload_batch (id, batchId, snapshotJson, commitBody, attempts, blockedReason)
                    VALUES (1, 'keep-batch', 'keep-snapshot', 'keep-body', 2, 'CONFLICT')
                    """.trimIndent(),
                )
            }
            helper.runMigrationsAndValidate(version = 10, migrations = listOf(AttendanceMigration9To10)).close()
            val database = createAttendanceDatabase(path.toString())
            try {
                assertEquals("Keep note", database.studentDao().getById(7)?.note)
                assertEquals("2026-10-19T00:00:01Z", database.syncUploadDao().getState()?.lastPullReadTime)
                assertEquals("2026-10-19T00:00:00Z", database.syncUploadDao().getState()?.headUpdateTime)
                assertEquals("keep-body", database.syncUploadDao().getBatch()?.commitBody)
                assertEquals(emptyList(), database.syncConflictDao().getAll())
            } finally {
                database.close()
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
