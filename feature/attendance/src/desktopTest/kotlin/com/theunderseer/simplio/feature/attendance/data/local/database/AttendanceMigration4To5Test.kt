package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentPackageEntity
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.firstClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.secondClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.student
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttendanceMigration4To5Test {
    private val studentId = 7
    private val packageTypeId = 3
    private val packageId = 11
    private val deletedPackageId = 20

    private lateinit var temporaryDirectory: Path
    private lateinit var databasePath: Path
    private lateinit var migrationHelper: MigrationTestHelper

    @BeforeTest
    fun setUp() {
        temporaryDirectory = Files.createTempDirectory(
            "simplio-attendance-migration-",
        )
        databasePath = temporaryDirectory.resolve("attendance.db")

        val schemaDirectory = checkNotNull(
            System.getProperty("simplio.room.schemaDirectory"),
        ) {
            "Room schema directory must be configured for migration tests"
        }

        migrationHelper = MigrationTestHelper(
            schemaDirectoryPath = Paths.get(schemaDirectory),
            databasePath = databasePath,
            driver = BundledSQLiteDriver(),
            databaseClass = AttendanceDatabase::class,
        )
    }

    @AfterTest
    fun tearDown() {
        temporaryDirectory.toFile().deleteRecursively()
    }

    @Test
    fun `migration preserves package terms activation and attendance`() = runTest {
        val database = openMigratedDatabase()

        try {
            val entity = assertNotNull(
                database.studentPackageDao().getLatestForStudent(studentId),
            )

            assertEquals(
                StudentPackageEntity(
                    id = packageId,
                    studentId = studentId,
                    packageTypeId = packageTypeId,
                    packageName = "Duo 8 · Special rate",
                    totalClasses = 8,
                    validityWeeks = 4,
                    price = 2400,
                    purchasedOnEpochDay = firstClassDate.toEpochDays(),
                    startedOnEpochDay = firstClassDate.toEpochDays(),
                    maxVisitsPerAttendance = 2,
                ),
                entity,
            )

            val repository = RoomStudentPackageRepository(database)

            val restoredPackage = assertNotNull(
                repository.getLatestPackageForStudent(studentId),
            )

            assertEquals(firstClassDate, restoredPackage.startedOn)
            assertEquals(
                mapOf(
                    firstClassDate to 2,
                    secondClassDate to 1,
                ),
                restoredPackage.attendanceHistory.visitsByDate,
            )
            assertEquals(3, restoredPackage.usedClasses)
        } finally {
            database.close()
        }
    }

    @Test
    fun `unactivated package is selected as latest and preserves id sequence`() = runTest {
        val database = openMigratedDatabase()

        try {
            val id = database.studentPackageDao().insert(
                StudentPackageEntity(
                    studentId = studentId,
                    packageTypeId = packageTypeId,
                    packageName = "Duo 8 · Special rate",
                    totalClasses = 8,
                    validityWeeks = 4,
                    price = 2400,
                    purchasedOnEpochDay = secondClassDate.toEpochDays(),
                    maxVisitsPerAttendance = 2,
                ),
            ).toInt()

            val latest = assertNotNull(
                database.studentPackageDao().getLatestForStudent(studentId),
            )

            assertTrue(id > deletedPackageId)
            assertEquals(id, latest.id)
            assertEquals(
                secondClassDate.toEpochDays(),
                latest.purchasedOnEpochDay,
            )
            assertNull(latest.startedOnEpochDay)
            assertEquals(
                emptyList(),
                database.attendanceDao().getForPackage(id),
            )
        } finally {
            database.close()
        }
    }

    private fun openMigratedDatabase(): AttendanceDatabase {
        createVersion4Database()

        migrationHelper.runMigrationsAndValidate(
            version = 5,
            migrations = listOf(AttendanceMigration4To5),
        ).use { connection ->
            connection.prepare("PRAGMA foreign_key_check").use { statement ->
                assertFalse(statement.step())
            }
        }

        return createAttendanceDatabase(databasePath.toString())
    }

    private fun createVersion4Database() {
        migrationHelper.createDatabase(version = 4).use { connection ->
            connection.execSQL(
                """
                INSERT INTO student_groups (id, name)
                VALUES (1, 'Group A')
                """.trimIndent(),
            )

            connection.prepare(
                """
                INSERT INTO students (id, fullName, groupId)
                VALUES (?, ?, 1)
                """.trimIndent(),
            ).use { statement ->
                statement.bindLong(1, studentId.toLong())
                statement.bindText(2, student.fullName)
                statement.step()
            }

            connection.execSQL(
                """
                INSERT INTO package_types (
                    id, name, totalClasses, validityWeeks,
                    price, maxVisitsPerAttendance
                )
                VALUES (
                    $packageTypeId, 'Duo 8 · Special rate',
                    8, 4, 2400, 2
                )
                """.trimIndent(),
            )

            listOf(packageId, deletedPackageId).forEach { id ->
                connection.execSQL(
                    """
                    INSERT INTO student_packages (
                        id, studentId, packageTypeId, packageName,
                        totalClasses, validityWeeks, price,
                        startedOnEpochDay, maxVisitsPerAttendance
                    )
                    VALUES (
                        $id, $studentId, $packageTypeId,
                        'Duo 8 · Special rate', 8, 4, 2400,
                        ${firstClassDate.toEpochDays()}, 2
                    )
                    """.trimIndent(),
                )
            }

            connection.execSQL(
                "DELETE FROM student_packages WHERE id = $deletedPackageId",
            )

            connection.execSQL(
                """
                INSERT INTO attendance (
                    packageId, attendedOnEpochDay, visitCount
                )
                VALUES
                    ($packageId, ${firstClassDate.toEpochDays()}, 2),
                    ($packageId, ${secondClassDate.toEpochDays()}, 1)
                """.trimIndent(),
            )
        }
    }
}
