package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomSyncOutbox
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomUploadStore
import com.theunderseer.simplio.feature.attendance.fixtures.testUploadTarget
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AttendanceMigration7To8Test {
    @Test
    fun `migration preserves pending identities and assigns aggregate boundaries`() = runTest {
        val directory = Files.createTempDirectory("simplio-upload-migration-")
        val path = directory.resolve("attendance.db")
        try {
            val helper = MigrationTestHelper(
                schemaDirectoryPath = Paths.get(checkNotNull(System.getProperty("simplio.room.schemaDirectory"))),
                databasePath = path,
                driver = BundledSQLiteDriver(),
                databaseClass = AttendanceDatabase::class,
            )
            helper.createDatabase(version = 7).use { connection ->
                connection.execSQL("INSERT INTO student_groups (id, name) VALUES (1, 'Group A')")
                connection.execSQL(
                    "INSERT INTO students (id, fullName, groupId, note) VALUES (7, 'Anna', 1, 'Preserve note')",
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
                val keys = listOf(
                    "STUDENT_GROUP" to "1",
                    "PACKAGE_TYPE" to "3",
                    "STUDENT" to "7",
                    "STUDENT_PACKAGE" to "11",
                    "ATTENDANCE" to "11:20728",
                    "STUDENT" to "20",
                    "STUDENT_PACKAGE" to "30",
                    "ATTENDANCE" to "30:20700",
                )
                keys.forEachIndexed { index, (type, id) ->
                    val deleted = if (index >= 5) 1 else 0
                    val operation = if (deleted == 1) "DELETE" else "UPSERT"
                    connection.execSQL(
                        """
                        INSERT INTO sync_records (entityType, localId, cloudId, revision, acknowledgedRevision,
                            isDeleted, serverUpdateTime) VALUES ('$type', '$id', 'cloud-$index', 2, 0, $deleted, NULL)
                        """.trimIndent(),
                    )
                    connection.execSQL(
                        """
                        INSERT INTO sync_outbox (entityType, localId, revision, operation, mutationId)
                        VALUES ('$type', '$id', 2, '$operation', 'mutation-$index')
                        """.trimIndent(),
                    )
                }
            }
            helper.runMigrationsAndValidate(version = 8, migrations = listOf(AttendanceMigration7To8)).close()
            val database = createAttendanceDatabase(path.toString())
            try {
                val pending = RoomSyncOutbox(database).getPending()
                assertEquals(List(8) { "mutation-$it" }, pending.map { it.mutationId })
                assertEquals(
                    listOf(
                        "STUDENT_GROUP:1",
                        "PACKAGE_TYPE:3",
                        "STUDENT:7",
                        "STUDENT:7",
                        "STUDENT:7",
                        "legacy-deletions",
                        "legacy-deletions",
                        "legacy-deletions",
                    ),
                    pending.map { it.aggregateKey },
                )
                assertEquals("Preserve note", database.studentDao().getById(7)?.note)
                assertEquals(1, database.attendanceDao().getForPackage(11).single().visitCount)
                assertNull(database.syncUploadDao().getState())
                assertNull(database.syncUploadDao().getBatch())
                val store = RoomUploadStore(database)
                assertEquals(2, store.snapshot(assertNotNull(store.prepare(testUploadTarget))).documents.size)
            } finally {
                database.close()
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
