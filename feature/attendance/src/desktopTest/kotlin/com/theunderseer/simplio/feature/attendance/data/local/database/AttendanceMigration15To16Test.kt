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

class AttendanceMigration15To16Test {
    @Test
    fun `migration preserves purchases visits cancellation and outbox without retroactive grants`() = runTest {
        val directory = Files.createTempDirectory("simplio-validity-extension-")
        val path = directory.resolve("attendance.db")
        try {
            val helper = MigrationTestHelper(
                Paths.get(checkNotNull(System.getProperty("simplio.room.schemaDirectory"))),
                path,
                BundledSQLiteDriver(),
                AttendanceDatabase::class,
            )
            helper.createDatabase(15).use { old ->
                old.execSQL("INSERT INTO student_groups (id,name,nameKey) VALUES (1,'Group A','group a')")
                old.execSQL("INSERT INTO students (id,fullName,groupId,note) VALUES (1,'Anna Smith',1,'Keep note')")
                old.execSQL("INSERT INTO package_types VALUES (1,'Finite',4,4,1400,1)")
                old.execSQL(
                    "INSERT INTO student_packages (id,studentId,packageTypeId,packageName,totalClasses," +
                        "validityWeeks,price,purchasedOnEpochDay,startedOnEpochDay,maxVisitsPerAttendance) " +
                        "VALUES (1,1,1,'Finite',4,4,1400,20000,20000,1)",
                )
                old.execSQL("INSERT INTO attendance (packageId,attendedOnEpochDay,visitCount) VALUES (1,20000,1)")
                old.execSQL(
                    "INSERT INTO class_schedules (id,name,timeZoneId) VALUES ('legacy','Legacy','Asia/Bangkok')",
                )
                old.execSQL("INSERT INTO class_schedule_slots VALUES ('legacy','mon',1,540,615)")
                old.execSQL("INSERT INTO student_group_schedules VALUES (1,'legacy')")
                old.execSQL("INSERT INTO class_session_states VALUES ('legacy:mon:20003','legacy',20003,'CANCELLED')")
                old.execSQL(
                    "INSERT INTO sync_records VALUES ('STUDENT_PACKAGE','1','pack-cloud',2,1,0,NULL,'STUDENT:1')",
                )
                old.execSQL("INSERT INTO sync_outbox VALUES (1,'STUDENT_PACKAGE','1',2,'UPSERT','keep-mutation')")
            }
            helper.runMigrationsAndValidate(16, listOf(AttendanceMigration15To16)).close()
            createAttendanceDatabase(path.toString()).let { db ->
                try {
                    val pack = db.studentPackageDao().getById(1)!!
                    assertTrue(pack.validityExtensions.isEmpty())
                    assertEquals(4, pack.totalClasses)
                    assertEquals(4, pack.validityWeeks)
                    assertEquals(1400, pack.price)
                    assertEquals(20000L, pack.startedOnEpochDay)
                    assertEquals(1, db.attendanceDao().getAll().single().visitCount)
                    assertEquals("CANCELLED", db.classSessionStateDao().getAll().single().status)
                    assertEquals("Keep note", db.studentDao().getById(1)?.note)
                    assertEquals("keep-mutation", db.syncDao().getPending(10).single().mutationId)
                    db.studentPackageDao().update(pack.copy(validityExtensions = mapOf("legacy:mon:20003" to 3)))
                } finally {
                    db.close()
                }
            }
            createAttendanceDatabase(path.toString()).let { db ->
                try {
                    assertEquals(mapOf("legacy:mon:20003" to 3), db.studentPackageDao().getById(1)?.validityExtensions)
                } finally {
                    db.close()
                }
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
