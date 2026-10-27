package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AttendanceMigration13To14Test {
    @Test
    fun `explicit class decisions start empty and migration preserves attendance packages and queued writes`() =
        runTest {
            val directory = Files.createTempDirectory("simplio-class-state-migration-")
            val path = directory.resolve("attendance.db")
            try {
                val helper = MigrationTestHelper(
                    schemaDirectoryPath = Paths.get(checkNotNull(System.getProperty("simplio.room.schemaDirectory"))),
                    databasePath = path,
                    driver = BundledSQLiteDriver(),
                    databaseClass = AttendanceDatabase::class,
                )
                helper.createDatabase(13).use { old ->
                    old.execSQL("INSERT INTO student_groups (id, name, nameKey) VALUES (7, 'Legacy', 'legacy')")
                    old.execSQL(
                        "INSERT INTO students (id, fullName, groupId, note) VALUES (12, 'Anna', 7, 'Keep note')",
                    )
                    old.execSQL("INSERT INTO package_types VALUES (9, 'Unlimited', 8, NULL, 9000, 2)")
                    old.execSQL(
                        "INSERT INTO student_packages VALUES (15, 12, 9, 'Purchased', 8, NULL, 8500, 20000, 20000, 2)",
                    )
                    old.execSQL("INSERT INTO attendance VALUES (15, 20000, 2, 'preserved-session')")
                    old.execSQL(
                        "INSERT INTO sync_records VALUES " +
                            "('STUDENT', '12', 'student-cloud', 2, 1, 0, NULL, 'STUDENT:12')",
                    )
                    old.execSQL(
                        "INSERT INTO sync_outbox VALUES (77, 'STUDENT', '12', 2, 'UPSERT', 'preserved-mutation')",
                    )
                }
                helper.runMigrationsAndValidate(14, listOf(AttendanceMigration13To14)).close()
                val database = createAttendanceDatabase(path.toString())
                try {
                    assertTrue(database.classSessionStateDao().getAll().isEmpty())
                    assertEquals("Keep note", database.studentDao().getById(12)?.note)
                    assertEquals("Purchased", database.studentPackageDao().getById(15)?.packageName)
                    assertEquals("preserved-session", database.attendanceDao().getForPackage(15).single().sessionId)
                    assertEquals(2, database.attendanceDao().getForPackage(15).single().visitCount)
                    assertEquals("preserved-mutation", database.syncDao().getPending(10).single().mutationId)
                } finally {
                    database.close()
                }
            } finally {
                directory.toFile().deleteRecursively()
            }
        }
}
