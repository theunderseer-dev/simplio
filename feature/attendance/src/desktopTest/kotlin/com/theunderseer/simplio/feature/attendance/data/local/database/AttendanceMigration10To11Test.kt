package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomClassScheduleRepository
import com.theunderseer.simplio.feature.attendance.domain.schedule.DefaultClassSchedule
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals

class AttendanceMigration10To11Test {
    @Test
    fun `migration attaches every existing group to one shared series and preserves data and pending sync`() = runTest {
        val directory = Files.createTempDirectory("simplio-schedule-migration-")
        val path = directory.resolve("attendance.db")
        try {
            val helper = MigrationTestHelper(
                schemaDirectoryPath = Paths.get(checkNotNull(System.getProperty("simplio.room.schemaDirectory"))),
                databasePath = path,
                driver = BundledSQLiteDriver(),
                databaseClass = AttendanceDatabase::class,
            )
            helper.createDatabase(version = 10).use { connection ->
                connection.execSQL("INSERT INTO student_groups VALUES (7, 'Existing A'), (19, 'Existing B')")
                connection.execSQL("INSERT INTO students VALUES (12, 'Anna', 7, 'Keep note')")
                connection.execSQL("INSERT INTO package_types VALUES (3, 'Four', 4, 4, 1400, 1)")
                connection.execSQL(
                    """
                    INSERT INTO student_packages
                        (id, studentId, packageTypeId, packageName, totalClasses, validityWeeks,
                         price, startedOnEpochDay, maxVisitsPerAttendance, purchasedOnEpochDay)
                    VALUES (23, 12, 3, 'Four', 4, 4, 1400, 20728, 1, 20728)
                    """.trimIndent(),
                )
                connection.execSQL("INSERT INTO attendance VALUES (23, 20728, 1)")
                connection.execSQL(
                    """
                    INSERT INTO sync_records
                        (entityType, localId, cloudId, revision, acknowledgedRevision, isDeleted, aggregateKey)
                    VALUES ('STUDENT', '12', 'preserved-student', 2, 1, 0, 'STUDENT:12')
                    """.trimIndent(),
                )
                connection.execSQL(
                    """
                    INSERT INTO sync_outbox (entityType, localId, revision, operation, mutationId)
                    VALUES ('STUDENT', '12', 2, 'UPSERT', 'keep-mutation')
                    """.trimIndent(),
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
            helper.runMigrationsAndValidate(version = 11, migrations = listOf(AttendanceMigration10To11)).close()
            val database = createAttendanceDatabase(path.toString())
            try {
                assertEquals(DefaultClassSchedule.schedule, RoomClassScheduleRepository(database).getAll().single())
                assertEquals(listOf(7, 19), database.classScheduleDao().groupSchedules().map { it.groupId })
                assertEquals(
                    setOf(DefaultClassSchedule.ID),
                    database.classScheduleDao().groupSchedules()
                        .map { it.scheduleId }.toSet(),
                )
                assertEquals("Keep note", database.studentDao().getById(12)?.note)
                assertEquals(20728L, database.studentPackageDao().getById(23)?.startedOnEpochDay)
                assertEquals(20728L, database.studentPackageDao().getById(23)?.purchasedOnEpochDay)
                assertEquals(1, database.attendanceDao().getForPackage(23).single().visitCount)
                assertEquals("keep-body", database.syncUploadDao().getBatch()?.commitBody)
                assertEquals("2026-10-19T00:00:01Z", database.syncUploadDao().getState()?.lastPullReadTime)
                assertEquals(1, database.syncPullDao().pendingCount())
                assertEquals("keep-mutation", database.syncDao().getPending(10).single().mutationId)
            } finally {
                database.close()
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
