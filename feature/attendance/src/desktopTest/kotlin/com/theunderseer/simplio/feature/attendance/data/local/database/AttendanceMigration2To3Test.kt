package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.theunderseer.simplio.feature.attendance.data.local.entity.AttendanceEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.PackageTypeEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentEntity
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.domain.model.PackageAttendanceHistory
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AttendanceMigration2To3Test {
    private val studentId = 7
    private val packageTypeId = 3
    private val packageId = 11
    private val firstAttendance = LocalDate(2026, 10, 2)
    private val secondAttendance = LocalDate(2026, 10, 5)

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
    fun `migration preserves existing packages and defaults visit limit to one`() = runTest {
        // Given
        createVersion2Database()

        // When: migrate and validate the complete database schema
        migrationHelper.runMigrationsAndValidate(
            version = 3,
            migrations = listOf(AttendanceMigration2To3),
        ).close()

        val database = createAttendanceDatabase(databasePath.toString())

        try {
            val repository =
                RoomStudentPackageRepository(
                    studentPackageDao = database.studentPackageDao(),
                    attendanceDao = database.attendanceDao(),
                )

            val studentPackage =
                assertNotNull(
                    repository.getLatestPackageForStudent(studentId),
                )

            // Then: the student and package catalog are preserved
            assertEquals(
                StudentEntity(
                    id = studentId,
                    fullName = "Anna Smith",
                    groupId = 1,
                ),
                database.studentDao().getById(studentId),
            )

            assertEquals(
                PackageTypeEntity(
                    id = packageTypeId,
                    name = "4-class package",
                    totalClasses = 4,
                    validityWeeks = 4,
                    price = 1400,
                    maxVisitsPerAttendance = 1,
                ),
                database.packageTypeDao().getById(packageTypeId),
            )

            // And: purchase terms and attendance history are preserved
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
                    startedOn = firstAttendance,
                    attendanceHistory =
                    PackageAttendanceHistory(
                        visitsByDate =
                        mapOf(
                            firstAttendance to 1,
                            secondAttendance to 1,
                        ),
                    ),
                ),
                studentPackage,
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun `migration preserves previously recorded visit counts`() {
        // Given
        createVersion2Database(secondVisitCount = 2)

        // When
        migrationHelper.runMigrationsAndValidate(
            version = 3,
            migrations = listOf(AttendanceMigration2To3),
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
                        attendedOnEpochDay = firstAttendance.toEpochDays(),
                        visitCount = 1,
                    ),
                    AttendanceEntity(
                        packageId = packageId,
                        attendedOnEpochDay = secondAttendance.toEpochDays(),
                        visitCount = 2,
                    ),
                ),
                attendance,
            )
        }
    }

    private fun createVersion2Database(secondVisitCount: Int = 1) {
        migrationHelper.createDatabase(version = 2).use { connection ->
            connection.execSQL(
                """
            INSERT INTO students (id, fullName)
            VALUES ($studentId, 'Anna Smith')
                """.trimIndent(),
            )

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
                4, 4, 1400, ${firstAttendance.toEpochDays()}
            )
                """.trimIndent(),
            )

            connection.execSQL(
                """
            INSERT INTO attendance (
                packageId, attendedOnEpochDay, visitCount
            )
            VALUES
                ($packageId, ${firstAttendance.toEpochDays()}, 1),
                ($packageId, ${secondAttendance.toEpochDays()}, $secondVisitCount)
                """.trimIndent(),
            )
        }
    }
}
