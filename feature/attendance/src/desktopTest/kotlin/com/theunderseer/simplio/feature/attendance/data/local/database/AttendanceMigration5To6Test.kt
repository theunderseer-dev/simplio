package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AttendanceMigration5To6Test {
    @Test
    fun `migration adds empty notes while preserving students packages and attendance`() = runTest {
        val directory = Files.createTempDirectory("simplio-note-migration-")
        val path = directory.resolve("attendance.db")
        val schemaDirectory = checkNotNull(System.getProperty("simplio.room.schemaDirectory"))
        val helper = MigrationTestHelper(
            schemaDirectoryPath = Paths.get(schemaDirectory),
            databasePath = path,
            driver = BundledSQLiteDriver(),
            databaseClass = AttendanceDatabase::class,
        )

        try {
            helper.createDatabase(version = 5).use { connection ->
                connection.execSQL("INSERT INTO student_groups (id, name) VALUES (1, 'Group A')")
                connection.prepare("INSERT INTO students (id, fullName, groupId) VALUES (7, ?, 1)").use { statement ->
                    statement.bindText(1, AttendanceTestData.student.fullName)
                    statement.step()
                }
                connection.execSQL(
                    """
                    INSERT INTO package_types
                        (id, name, totalClasses, validityWeeks, price, maxVisitsPerAttendance)
                    VALUES (3, 'Duo 8 · Special rate', 8, 4, 2400, 2)
                    """.trimIndent(),
                )
                val first = AttendanceTestData.firstClassDate.toEpochDays()
                val second = AttendanceTestData.secondClassDate.toEpochDays()
                connection.execSQL(
                    """
                    INSERT INTO student_packages
                        (id, studentId, packageTypeId, packageName, totalClasses,
                         validityWeeks, price, purchasedOnEpochDay, startedOnEpochDay, maxVisitsPerAttendance)
                    VALUES (11, 7, 3, 'Duo 8 · Special rate', 8, 4, 2400, $first, $first, 2)
                    """.trimIndent(),
                )
                connection.execSQL(
                    "INSERT INTO attendance (packageId, attendedOnEpochDay, visitCount) " +
                        "VALUES (11, $first, 2), (11, $second, 1)",
                )
            }

            helper.runMigrationsAndValidate(version = 6, migrations = listOf(AttendanceMigration5To6)).close()

            val database = createAttendanceDatabase(path.toString())
            try {
                val students = RoomStudentRepository(database.studentDao())
                val student = students.getAll().single()
                assertEquals(7, student.id)
                assertEquals(AttendanceTestData.student.fullName, student.fullName)
                assertEquals(1, student.groupId)
                assertEquals("", student.note)

                val studentPackage = assertNotNull(
                    RoomStudentPackageRepository(database.studentPackageDao(), database.attendanceDao())
                        .getLatestPackageForStudent(student.id),
                )
                assertEquals(11, studentPackage.id)
                assertEquals(AttendanceTestData.firstClassDate, studentPackage.purchasedOn)
                assertEquals(AttendanceTestData.firstClassDate, studentPackage.startedOn)
                assertEquals(
                    mapOf(AttendanceTestData.firstClassDate to 2, AttendanceTestData.secondClassDate to 1),
                    studentPackage.attendanceHistory.visitsByDate,
                )
                assertEquals(3, studentPackage.usedClasses)
                assertEquals(5, studentPackage.remainingClasses)

                students.updateNote(student.id, AttendanceTestData.studentNote)
                assertEquals(AttendanceTestData.studentNote, students.getAll().single().note)
            } finally {
                database.close()
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
