package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.theunderseer.simplio.feature.attendance.data.local.entity.AttendanceEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentEntity
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.domain.model.PackageAttendanceHistory
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.firstClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.secondClassDate
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AttendanceMigration1To2Test {
    private val studentId = 7
    private val packageTypeId = 3
    private val packageId = 11
    private val studentFullName = AttendanceTestData.student.fullName
    private lateinit var temporaryDirectory: Path
    private lateinit var databasePath: Path
    private lateinit var migrationHelper: MigrationTestHelper

    @BeforeTest
    fun setUp() {
        temporaryDirectory =
            Files.createTempDirectory("simplio-attendance-migration-")
        databasePath = temporaryDirectory.resolve("attendance.db")

        val schemaDirectory =
            checkNotNull(
                System.getProperty("simplio.room.schemaDirectory"),
            ) {
                "Room schema directory must be configured for migration tests"
            }

        migrationHelper =
            MigrationTestHelper(
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
    fun `migration preserves attendance and assigns one visit to each existing date`() {
        // Given
        createVersion1Database()

        // When: migrate and validate the complete database schema
        migrationHelper.runMigrationsAndValidate(
            version = 2,
            migrations = listOf(AttendanceMigration1To2),
        ).use { connection ->
            val attendance = mutableListOf<AttendanceEntity>()

            connection.prepare(
                """
                SELECT packageId, attendedOnEpochDay, visitCount
                FROM attendance
                ORDER BY packageId, attendedOnEpochDay
                """.trimIndent(),
            ).use { statement ->
                while (statement.step()) {
                    attendance +=
                        AttendanceEntity(
                            packageId = statement.getLong(0).toInt(),
                            attendedOnEpochDay = statement.getLong(1),
                            visitCount = statement.getLong(2).toInt(),
                        )
                }
            }

            // Then
            assertEquals(
                listOf(
                    AttendanceEntity(
                        packageId = packageId,
                        attendedOnEpochDay = firstClassDate.toEpochDays(),
                        visitCount = 1,
                    ),
                    AttendanceEntity(
                        packageId = packageId,
                        attendedOnEpochDay = secondClassDate.toEpochDays(),
                        visitCount = 1,
                    ),
                ),
                attendance,
            )
        }
    }

    @Test
    fun `opening existing database migrates and restores student package history`() = runTest {
        // Given
        createVersion1Database()

        // When: use the application's database factory
        val database = createAttendanceDatabase(databasePath.toString())

        try {
            val persistedStudent =
                database.studentDao().getById(studentId)

            val repository =
                RoomStudentPackageRepository(
                    studentPackageDao = database.studentPackageDao(),
                    attendanceDao = database.attendanceDao(),
                )

            val studentPackage =
                assertNotNull(
                    repository.getLatestPackageForStudent(studentId),
                )

            // Then: the student and purchased package terms are preserved
            assertEquals(
                StudentEntity(
                    id = studentId,
                    fullName = studentFullName,
                    groupId = 1,
                ),
                persistedStudent,
            )

            assertEquals(
                StudentPackage(
                    id = packageId,
                    studentId = studentId,
                    packageType =
                    PackageType(
                        id = packageTypeId,
                        name = "4-class package",
                        totalClasses = 4,
                        validityWeeks = 4,
                        price = 1400,
                        maxVisitsPerAttendance = 1,
                    ),
                    purchasedOn = firstClassDate,
                    startedOn = firstClassDate,
                    attendanceHistory =
                    PackageAttendanceHistory(
                        visitsByDate =
                        mapOf(
                            firstClassDate to 1,
                            secondClassDate to 1,
                        ),
                    ),
                ),
                studentPackage,
            )

            assertEquals(2, studentPackage.usedClasses)
        } finally {
            database.close()
        }
    }

    private fun createVersion1Database() {
        migrationHelper.createDatabase(version = 1).use { connection ->
            connection.prepare(
                """
                INSERT INTO students (id, fullName)
                VALUES (?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.bindLong(1, studentId.toLong())
                statement.bindText(2, studentFullName)
                statement.step()
            }

            connection.execSQL(
                """
                INSERT INTO package_types (
                    id, name, totalClasses, validityWeeks, price
                )
                VALUES ($packageTypeId, '4-class package', 4, 4, 1400)
                """.trimIndent(),
            )

            connection.execSQL(
                """
                INSERT INTO student_packages (
                    id, studentId, packageTypeId, packageName,
                    totalClasses, validityWeeks, price, startedOnEpochDay
                )
                VALUES (
                    $packageId, $studentId, $packageTypeId, '4-class package',
                    4, 4, 1400, ${firstClassDate.toEpochDays()}
                )
                """.trimIndent(),
            )

            connection.execSQL(
                """
                INSERT INTO attendance (packageId, attendedOnEpochDay)
                VALUES
                    ($packageId, ${firstClassDate.toEpochDays()}),
                    ($packageId, ${secondClassDate.toEpochDays()})
                """.trimIndent(),
            )
        }
    }
}
