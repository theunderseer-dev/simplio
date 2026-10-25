package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.theunderseer.simplio.feature.attendance.data.local.entity.PackageTypeEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentPackageEntity
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttendanceMigration12To13Test {
    @Test
    fun `nullable validity migration preserves snapshots attendance sessions queue and id sequences`() = runTest {
        withMigratedPackages(seed = { old ->
            old.execSQL("INSERT INTO student_groups (id, name, nameKey) VALUES (7, 'Legacy', 'legacy')")
            old.execSQL("INSERT INTO students (id, fullName, groupId, note) VALUES (12, 'Anna', 7, 'Keep note')")
            old.execSQL("INSERT INTO package_types VALUES (9, 'Legacy package', 8, 6, 9000, 2)")
            old.execSQL("INSERT INTO package_types VALUES (99, 'Deleted template', 4, 4, 1400, 1)")
            old.execSQL("DELETE FROM package_types WHERE id = 99")
            old.execSQL(
                "INSERT INTO student_packages VALUES (15, 12, 9, 'Purchased terms', 8, 5, 8500, 20000, 20000, 2)",
            )
            old.execSQL(
                "INSERT INTO student_packages VALUES (199, 12, 9, 'Deleted package', 8, 5, 8500, 20000, NULL, 2)",
            )
            old.execSQL("DELETE FROM student_packages WHERE id = 199")
            old.execSQL("INSERT INTO attendance VALUES (15, 20000, 2, 'preserved-session')")
            old.execSQL(
                "INSERT INTO sync_records VALUES ('PACKAGE_TYPE', '9', 'custom-cloud-id', 2, 1, 0, NULL, 'PACKAGE_TYPE:9')",
            )
            old.execSQL(
                "INSERT INTO sync_outbox VALUES (77, 'PACKAGE_TYPE', '9', 2, 'UPSERT', 'preserved-mutation')",
            )
        }) { database ->
            assertEquals(6, database.packageTypeDao().getById(9)?.validityWeeks)
            val previous = checkNotNull(database.studentPackageDao().getById(15))
            assertEquals("Purchased terms", previous.packageName)
            assertEquals(5, previous.validityWeeks)
            assertEquals(8500, previous.price)
            assertEquals(20000L, previous.startedOnEpochDay)
            val attendance = database.attendanceDao().getForPackage(15).single()
            assertEquals(2, attendance.visitCount)
            assertEquals("preserved-session", attendance.sessionId)
            assertEquals("Keep note", database.studentDao().getById(12)?.note)
            assertEquals(
                "custom-cloud-id",
                database.syncDao().getRecord(
                    com.theunderseer.simplio.feature.attendance.data.local.sync.SyncEntityType.PACKAGE_TYPE,
                    "9",
                )?.cloudId,
            )
            val nextType = database.packageTypeDao().insert(
                PackageTypeEntity(
                    name = "Unlimited",
                    totalClasses = 20,
                    validityWeeks = null,
                    price = 22000,
                ),
            ).toInt()
            assertTrue(nextType > 99)
            assertNull(database.packageTypeDao().getById(nextType)?.validityWeeks)
            val nextPackage = database.studentPackageDao().insert(
                StudentPackageEntity(
                    studentId = 12,
                    packageTypeId = nextType,
                    packageName = "Unlimited",
                    totalClasses = 20,
                    validityWeeks = null,
                    price = 22000,
                    purchasedOnEpochDay = 20001,
                ),
            ).toInt()
            assertTrue(nextPackage > 199)
            assertNull(database.studentPackageDao().getById(nextPackage)?.validityWeeks)
            assertEquals("preserved-mutation", database.syncDao().getPending(10).single().mutationId)
        }
    }

    @Test
    fun `migration retains id high water marks when package tables have no remaining rows`() = runTest {
        withMigratedPackages(seed = { old ->
            old.execSQL("INSERT INTO student_groups (id, name, nameKey) VALUES (7, 'Legacy', 'legacy')")
            old.execSQL("INSERT INTO students (id, fullName, groupId, note) VALUES (12, 'Anna', 7, '')")
            old.execSQL("INSERT INTO package_types VALUES (99, 'Deleted template', 4, 4, 1400, 1)")
            old.execSQL(
                "INSERT INTO student_packages VALUES (199, 12, 99, 'Deleted package', 4, 4, 1400, 20000, NULL, 1)",
            )
            old.execSQL("DELETE FROM student_packages")
            old.execSQL("DELETE FROM package_types")
        }) { database ->
            val id = database.packageTypeDao().insert(
                PackageTypeEntity(
                    name = "Unlimited",
                    totalClasses = 8,
                    validityWeeks = null,
                    price = 9000,
                ),
            ).toInt()
            assertTrue(id > 99)
            val purchase = database.studentPackageDao().insert(
                StudentPackageEntity(
                    studentId = 12,
                    packageTypeId = id,
                    packageName = "Unlimited",
                    totalClasses = 8,
                    validityWeeks = null,
                    price = 9000,
                    purchasedOnEpochDay = 20001,
                ),
            )
            assertTrue(purchase > 199)
        }
    }
}

private suspend fun withMigratedPackages(
    seed: (SQLiteConnection) -> Unit,
    verify: suspend (AttendanceDatabase) -> Unit,
) {
    val directory = Files.createTempDirectory("simplio-package-migration-")
    val path = directory.resolve("attendance.db")
    try {
        val helper = MigrationTestHelper(
            schemaDirectoryPath = Paths.get(checkNotNull(System.getProperty("simplio.room.schemaDirectory"))),
            databasePath = path,
            driver = BundledSQLiteDriver(),
            databaseClass = AttendanceDatabase::class,
        )
        helper.createDatabase(version = 12).use(seed)
        helper.runMigrationsAndValidate(13, listOf(AttendanceMigration12To13)).close()
        val database = createAttendanceDatabase(path.toString())
        try {
            verify(database)
        } finally {
            database.close()
        }
    } finally {
        directory.toFile().deleteRecursively()
    }
}
