package com.theunderseer.simplio.feature.attendance.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentGroupEntity
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

class AttendanceMigration3To4Test {
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

        val schemaDirectory: String =
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
    fun `opening version 3 database assigns students to group A and preserves package attendance`() = runTest {
        // Given
        createVersion3Database()

        // When
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

            // Then
            assertEquals(
                listOf(
                    StudentGroupEntity(id = 1, name = "Group A"),
                    StudentGroupEntity(id = 2, name = "Group B"),
                ),
                database.studentGroupDao().getAll(),
            )

            assertEquals(
                StudentEntity(
                    id = studentId,
                    fullName = "Anna & John",
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
                        name = "Duo 8 · Special rate",
                        totalClasses = 8,
                        validityWeeks = 4,
                        price = 2400,
                        maxVisitsPerAttendance = 2,
                    ),
                    startedOn = firstAttendance,
                    attendanceHistory =
                    PackageAttendanceHistory(
                        visitsByDate =
                        mapOf(
                            firstAttendance to 2,
                            secondAttendance to 1,
                        ),
                    ),
                ),
                studentPackage,
            )

            assertEquals(3, studentPackage.usedClasses)
            assertEquals(5, studentPackage.remainingClasses)
        } finally {
            database.close()
        }
    }

    private fun createVersion3Database() {
        migrationHelper.createDatabase(version = 3).use { connection ->
            connection.execSQL(
                """
                INSERT INTO students (id, fullName)
                VALUES ($studentId, 'Anna & John')
                """.trimIndent(),
            )

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

            connection.execSQL(
                """
                INSERT INTO student_packages (
                    id, studentId, packageTypeId, packageName,
                    totalClasses, validityWeeks, price,
                    startedOnEpochDay, maxVisitsPerAttendance
                )
                VALUES (
                    $packageId, $studentId, $packageTypeId,
                    'Duo 8 · Special rate', 8, 4, 2400,
                    ${firstAttendance.toEpochDays()}, 2
                )
                """.trimIndent(),
            )

            connection.execSQL(
                """
                INSERT INTO attendance (
                    packageId, attendedOnEpochDay, visitCount
                )
                VALUES
                    ($packageId, ${firstAttendance.toEpochDays()}, 2),
                    ($packageId, ${secondAttendance.toEpochDays()}, 1)
                """.trimIndent(),
            )
        }
    }
}
