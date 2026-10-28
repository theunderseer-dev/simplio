package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentGroupDeletionRepository
import com.theunderseer.simplio.feature.attendance.data.local.seed.StudentGroupSeeder
import com.theunderseer.simplio.feature.attendance.fixtures.ClassAttendanceTestClock
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttendanceMigration14To15Test {
    @Test
    fun `migration preserves schedules links decisions and pending mutations then deletion survives reopen`() =
        runTest {
            val directory = Files.createTempDirectory("simplio-group-retirement-")
            val path = directory.resolve("attendance.db")
            try {
                val helper =
                    MigrationTestHelper(
                        Paths.get(checkNotNull(System.getProperty("simplio.room.schemaDirectory"))),
                        path,
                        BundledSQLiteDriver(),
                        AttendanceDatabase::class,
                    )
                helper.createDatabase(14).use { old ->
                    old.execSQL("INSERT INTO student_groups (id,name,nameKey) VALUES (1,'Group A','group a')")
                    old.execSQL("INSERT INTO class_schedules VALUES ('legacy','Legacy','Asia/Bangkok',NULL,NULL)")
                    old.execSQL("INSERT INTO class_schedule_slots VALUES ('legacy','mon',1,540,615)")
                    old.execSQL("INSERT INTO student_group_schedules VALUES (1,'legacy')")
                    old.execSQL("INSERT INTO class_session_states VALUES ('legacy:mon:20000','legacy',20000,'HELD')")
                    old.execSQL(
                        "INSERT INTO sync_records VALUES ('STUDENT_GROUP','1','default-group-a',1,0,0,NULL,'STUDENT_GROUP:1')",
                    )
                    old.execSQL("INSERT INTO sync_outbox VALUES (1,'STUDENT_GROUP','1',1,'UPSERT','keep-mutation')")
                }
                helper.runMigrationsAndValidate(15, listOf(AttendanceMigration14To15)).close()
                createAttendanceDatabase(path.toString()).let { db ->
                    try {
                        assertNull(db.classScheduleDao().schedules().single().archivedAtEpochMillis)
                        assertEquals(1, db.classScheduleDao().groupSchedules().size)
                        assertEquals("HELD", db.classSessionStateDao().getAll().single().status)
                        assertEquals("keep-mutation", db.syncDao().getPending(10).single().mutationId)
                        RoomStudentGroupDeletionRepository(db, ClassAttendanceTestClock()).delete(1)
                    } finally {
                        db.close()
                    }
                }
                createAttendanceDatabase(path.toString()).let { db ->
                    try {
                        StudentGroupSeeder(db).seedDefaults()
                        assertTrue(db.studentGroupDao().getAll().none { it.id == 1 })
                        assertEquals("HELD", db.classSessionStateDao().getAll().single().status)
                        assertTrue(
                            db.classScheduleDao().schedules().any {
                                it.id == "legacy" &&
                                    it.archivedAtEpochMillis != null
                            },
                        )
                    } finally {
                        db.close()
                    }
                }
            } finally {
                directory.toFile().deleteRecursively()
            }
        }
}
