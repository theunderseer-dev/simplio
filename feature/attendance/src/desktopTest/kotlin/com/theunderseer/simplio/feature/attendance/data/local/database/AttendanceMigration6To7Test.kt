package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomSyncOutbox
import com.theunderseer.simplio.feature.attendance.data.local.sync.SyncEntityType
import com.theunderseer.simplio.feature.attendance.data.local.sync.SyncOperation
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.seedSyncDefaults
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AttendanceMigration6To7Test {
    @Test
    fun `migration preserves business data and backfills stable pending identities`() = runTest {
        val directory = Files.createTempDirectory("simplio-sync-migration-")
        val path = directory.resolve("attendance.db")
        val helper = MigrationTestHelper(
            schemaDirectoryPath = Paths.get(checkNotNull(System.getProperty("simplio.room.schemaDirectory"))),
            databasePath = path,
            driver = BundledSQLiteDriver(),
            databaseClass = AttendanceDatabase::class,
        )
        val first = AttendanceTestData.firstClassDate.toEpochDays()
        val second = AttendanceTestData.secondClassDate.toEpochDays()
        try {
            helper.createDatabase(version = 6).use { connection ->
                connection.execSQL("INSERT INTO student_groups (id, name) VALUES (1, 'Group A')")
                connection.execSQL(
                    "INSERT INTO students (id, fullName, groupId, note) VALUES (7, 'Anna Smith', 1, 'Keep note')",
                )
                connection.execSQL(
                    """
                    INSERT INTO package_types (id, name, totalClasses, validityWeeks, price, maxVisitsPerAttendance)
                    VALUES (3, 'Duo 8 · Special rate', 8, 4, 2400, 2),
                        (4, 'Custom package', 20, 10, 5000, 1),
                        (5, 'Duo 8 · Special rate', 8, 4, 2400, 2)
                    """.trimIndent(),
                )
                connection.execSQL(
                    """
                    INSERT INTO student_packages
                        (id, studentId, packageTypeId, packageName, totalClasses,
                         validityWeeks, price, purchasedOnEpochDay, startedOnEpochDay, maxVisitsPerAttendance)
                    VALUES (11, 7, 3, 'Duo 8 · Special rate', 8, 4, 2400, $first, $first, 2),
                        (12, 7, 4, 'Custom package', 20, 10, 5000, $second, NULL, 1)
                    """.trimIndent(),
                )
                connection.execSQL(
                    "INSERT INTO attendance (packageId, attendedOnEpochDay, visitCount) VALUES (11, $first, 2)",
                )
            }
            helper.runMigrationsAndValidate(version = 7, migrations = listOf(AttendanceMigration6To7)).close()

            val original = createAttendanceDatabase(path.toString()).let { database ->
                try {
                    val student = RoomStudentRepository(database).getAll().single()
                    assertEquals(7, student.id)
                    assertEquals("Keep note", student.note)
                    assertEquals("Anna Smith", student.fullName)
                    assertEquals(1, student.groupId)
                    val latest = assertNotNull(RoomStudentPackageRepository(database).getLatestPackageForStudent(7))
                    assertEquals(12, latest.id)
                    assertEquals(AttendanceTestData.secondClassDate, latest.purchasedOn)
                    assertEquals(null, latest.startedOn)
                    assertEquals("Custom package", latest.packageType.name)
                    assertEquals(20, latest.remainingClasses)
                    val older = assertNotNull(database.studentPackageDao().getById(11))
                    assertEquals(first, older.startedOnEpochDay)
                    assertEquals(2400, older.price)
                    assertEquals(2, database.attendanceDao().getForPackage(11).single().visitCount)
                    val pending = RoomSyncOutbox(database).getPending()
                    assertEquals(8, pending.size)
                    assertTrue(pending.all { it.operation == SyncOperation.UPSERT && it.revision == 1L })
                    assertEquals(
                        "default-group-a",
                        pending.single {
                            it.entityType == SyncEntityType.STUDENT_GROUP
                        }.cloudId,
                    )
                    val types = pending.filter { it.entityType == SyncEntityType.PACKAGE_TYPE }
                    assertEquals("default-plan-duo_special", types.single { it.localId == "3" }.cloudId)
                    assertEquals(3, types.map { it.cloudId }.toSet().size)
                    val parent = pending.single {
                        it.entityType == SyncEntityType.STUDENT_PACKAGE && it.localId == "11"
                    }
                    assertEquals(
                        "${parent.cloudId}_$first",
                        pending.single {
                            it.entityType == SyncEntityType.ATTENDANCE
                        }.cloudId,
                    )
                    pending
                } finally {
                    database.close()
                }
            }
            createAttendanceDatabase(path.toString()).let { database ->
                try {
                    assertEquals(original, RoomSyncOutbox(database).getPending())
                    database.seedSyncDefaults()
                    val afterSeed = RoomSyncOutbox(database).getPending()
                    original.forEach { change -> assertTrue(change in afterSeed) }
                } finally {
                    database.close()
                }
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
