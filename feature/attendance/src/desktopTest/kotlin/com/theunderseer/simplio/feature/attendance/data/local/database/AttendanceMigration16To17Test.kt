package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AttendanceMigration16To17Test {
    @Test
    fun `existing templates remain selectable and purchases keep their original terms`() = runTest {
        val directory = Files.createTempDirectory("simplio-package-catalog-")
        val path = directory.resolve("attendance.db")
        try {
            val helper = MigrationTestHelper(
                Paths.get(checkNotNull(System.getProperty("simplio.room.schemaDirectory"))),
                path,
                BundledSQLiteDriver(),
                AttendanceDatabase::class,
            )
            helper.createDatabase(16).use { old ->
                old.execSQL("INSERT INTO student_groups (id,name,nameKey) VALUES (1,'Group A','group a')")
                old.execSQL("INSERT INTO students (id,fullName,groupId) VALUES (1,'Anna Smith',1)")
                old.execSQL("INSERT INTO package_types VALUES (1,'Five classes',5,6,1700,1)")
                old.execSQL(
                    "INSERT INTO student_packages (id,studentId,packageTypeId,packageName,totalClasses," +
                        "validityWeeks,price,purchasedOnEpochDay,startedOnEpochDay) " +
                        "VALUES (1,1,1,'Five classes',5,6,1700,20000,20000)",
                )
                old.execSQL("INSERT INTO attendance (packageId,attendedOnEpochDay,visitCount) VALUES (1,20000,1)")
            }
            helper.runMigrationsAndValidate(17, listOf(AttendanceMigration16To17)).close()
            val database = createAttendanceDatabase(path.toString())
            try {
                assertFalse(database.packageTypeDao().getActive().single().isArchived)
                assertEquals(6, database.studentPackageDao().getById(1)?.validityWeeks)
                assertEquals(1, database.attendanceDao().getAll().single().visitCount)
            } finally {
                database.close()
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
