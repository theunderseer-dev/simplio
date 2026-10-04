package com.theunderseer.simplio.feature.attendance.domain.usecase

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.entity.AttendanceEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.PackageTypeEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentPackageEntity
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.transaction.RoomTransactionRunner
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentPackageRepository
import com.theunderseer.simplio.feature.attendance.domain.result.MarkStudentPresentResult
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarkStudentPresentUseCaseIntegrationTest {
    private lateinit var database: AttendanceDatabase
    private lateinit var markStudentPresent: MarkStudentPresentUseCase

    @BeforeTest
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder<AttendanceDatabase>()
                .setDriver(BundledSQLiteDriver())
                .build()

        markStudentPresent =
            MarkStudentPresentUseCase(
                repository =
                RoomStudentPackageRepository(
                    studentPackageDao = database.studentPackageDao(),
                    attendanceDao = database.attendanceDao(),
                ),
                transactionRunner =
                RoomTransactionRunner(
                    database = database,
                ),
            )
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun `marking student present persists attendance for active package`() = runTest {
        // Given
        val studentId =
            database
                .studentDao()
                .insert(
                    StudentEntity(
                        fullName = "Anna Smith",
                    ),
                ).toInt()

        val packageTypeId =
            database
                .packageTypeDao()
                .insert(
                    PackageTypeEntity(
                        name = "5-class package",
                        totalClasses = 5,
                        validityWeeks = 6,
                        price = 1700,
                    ),
                ).toInt()

        val startedOn = LocalDate(2026, 9, 25)

        val packageId =
            database
                .studentPackageDao()
                .insert(
                    StudentPackageEntity(
                        studentId = studentId,
                        packageTypeId = packageTypeId,
                        packageName = "5-class package",
                        totalClasses = 5,
                        validityWeeks = 6,
                        price = 1700,
                        startedOnEpochDay = startedOn.toEpochDays(),
                    ),
                ).toInt()

        database.attendanceDao().insert(
            AttendanceEntity(
                packageId = packageId,
                attendedOnEpochDay = startedOn.toEpochDays(),
            ),
        )

        val attendanceDate = LocalDate(2026, 9, 27)

        // When
        val result =
            markStudentPresent(
                studentId = studentId,
                attendanceDate = attendanceDate,
            )

        // Then
        val success =
            assertIs<MarkStudentPresentResult.Success>(result)

        assertEquals(
            packageId,
            success.studentPackage.id,
        )

        assertEquals(
            setOf(
                startedOn,
                attendanceDate,
            ),
            success.studentPackage.attendanceDates,
        )

        assertEquals(
            2,
            success.studentPackage.usedClasses,
        )

        // And
        // the new attendance is persisted in Room
        val persistedAttendance =
            database
                .attendanceDao()
                .getForPackage(packageId)

        assertEquals(
            2,
            persistedAttendance.size,
        )

        assertEquals(
            setOf(
                startedOn.toEpochDays(),
                attendanceDate.toEpochDays(),
            ),
            persistedAttendance
                .map { it.attendedOnEpochDay }
                .toSet(),
        )
    }

    @Test
    fun `marking student present on same date does not duplicate attendance`() = runTest {
        // Given
        val studentId =
            database
                .studentDao()
                .insert(
                    StudentEntity(
                        fullName = "Anna Smith",
                    ),
                ).toInt()

        val packageTypeId =
            database
                .packageTypeDao()
                .insert(
                    PackageTypeEntity(
                        name = "5-class package",
                        totalClasses = 5,
                        validityWeeks = 6,
                        price = 1700,
                    ),
                ).toInt()

        val attendanceDate = LocalDate(2026, 9, 25)

        val packageId =
            database
                .studentPackageDao()
                .insert(
                    StudentPackageEntity(
                        studentId = studentId,
                        packageTypeId = packageTypeId,
                        packageName = "5-class package",
                        totalClasses = 5,
                        validityWeeks = 6,
                        price = 1700,
                        startedOnEpochDay = attendanceDate.toEpochDays(),
                    ),
                ).toInt()

        database.attendanceDao().insert(
            AttendanceEntity(
                packageId = packageId,
                attendedOnEpochDay = attendanceDate.toEpochDays(),
            ),
        )

        // When
        val result =
            markStudentPresent(
                studentId = studentId,
                attendanceDate = attendanceDate,
            )

        // Then
        val success =
            assertIs<MarkStudentPresentResult.Success>(result)

        assertEquals(
            packageId,
            success.studentPackage.id,
        )

        assertEquals(
            setOf(attendanceDate),
            success.studentPackage.attendanceDates,
        )

        assertEquals(
            1,
            success.studentPackage.usedClasses,
        )

        // And
        // no duplicate attendance is persisted
        val persistedAttendance =
            database
                .attendanceDao()
                .getForPackage(packageId)

        assertEquals(
            1,
            persistedAttendance.size,
        )

        val persistedDate =
            persistedAttendance.single()

        assertEquals(
            packageId,
            persistedDate.packageId,
        )

        assertEquals(
            attendanceDate.toEpochDays(),
            persistedDate.attendedOnEpochDay,
        )
    }

    @Test
    fun `marking attendance on completed package returns completed and persists no changes`() = runTest {
        // Given
        val studentId =
            database
                .studentDao()
                .insert(
                    StudentEntity(
                        fullName = "Anna Smith",
                    ),
                ).toInt()

        val packageTypeId =
            database
                .packageTypeDao()
                .insert(
                    PackageTypeEntity(
                        name = "5-class package",
                        totalClasses = 5,
                        validityWeeks = 6,
                        price = 1700,
                    ),
                ).toInt()

        val startedOn = LocalDate(2026, 9, 20)

        val packageId =
            database
                .studentPackageDao()
                .insert(
                    StudentPackageEntity(
                        studentId = studentId,
                        packageTypeId = packageTypeId,
                        packageName = "5-class package",
                        totalClasses = 5,
                        validityWeeks = 6,
                        price = 1700,
                        startedOnEpochDay = startedOn.toEpochDays(),
                    ),
                ).toInt()

        val attendanceDates =
            listOf(
                LocalDate(2026, 9, 20),
                LocalDate(2026, 9, 22),
                LocalDate(2026, 9, 24),
                LocalDate(2026, 9, 26),
                LocalDate(2026, 9, 28),
            )

        attendanceDates.forEach { date ->
            database.attendanceDao().insert(
                AttendanceEntity(
                    packageId = packageId,
                    attendedOnEpochDay = date.toEpochDays(),
                ),
            )
        }

        val nextAttendanceDate = LocalDate(2026, 9, 30)

        // When
        val result =
            markStudentPresent(
                studentId = studentId,
                attendanceDate = nextAttendanceDate,
            )

        // Then
        val completed =
            assertIs<MarkStudentPresentResult.PackageCompleted>(result)

        assertEquals(
            packageId,
            completed.studentPackage.id,
        )

        assertEquals(
            attendanceDates.toSet(),
            completed.studentPackage.attendanceDates,
        )

        assertEquals(
            5,
            completed.studentPackage.usedClasses,
        )

        assertTrue(
            completed.studentPackage.isCompleted,
        )

        // And
        // no new attendance is persisted
        val persistedAttendance =
            database
                .attendanceDao()
                .getForPackage(packageId)

        assertEquals(
            5,
            persistedAttendance.size,
        )

        assertEquals(
            attendanceDates.map { it.toEpochDays() }.toSet(),
            persistedAttendance
                .map { it.attendedOnEpochDay }
                .toSet(),
        )
    }

    @Test
    fun `marking attendance on expired package returns expired and persists no changes`() = runTest {
        // Given
        val studentId =
            database
                .studentDao()
                .insert(
                    StudentEntity(
                        fullName = "Anna Smith",
                    ),
                ).toInt()

        val packageTypeId =
            database
                .packageTypeDao()
                .insert(
                    PackageTypeEntity(
                        name = "5-class package",
                        totalClasses = 5,
                        validityWeeks = 6,
                        price = 1700,
                    ),
                ).toInt()

        val startedOn = LocalDate(2026, 8, 1)

        val packageId =
            database
                .studentPackageDao()
                .insert(
                    StudentPackageEntity(
                        studentId = studentId,
                        packageTypeId = packageTypeId,
                        packageName = "5-class package",
                        totalClasses = 5,
                        validityWeeks = 6,
                        price = 1700,
                        startedOnEpochDay = startedOn.toEpochDays(),
                    ),
                ).toInt()

        val attendanceDates =
            listOf(
                LocalDate(2026, 8, 1),
                LocalDate(2026, 8, 8),
            )

        attendanceDates.forEach { date ->
            database.attendanceDao().insert(
                AttendanceEntity(
                    packageId = packageId,
                    attendedOnEpochDay = date.toEpochDays(),
                ),
            )
        }

        val attendanceDate = LocalDate(2026, 9, 20)

        // When
        val result =
            markStudentPresent(
                studentId = studentId,
                attendanceDate = attendanceDate,
            )

        // Then
        val expired =
            assertIs<MarkStudentPresentResult.PackageExpired>(result)

        assertEquals(
            packageId,
            expired.studentPackage.id,
        )

        assertEquals(
            attendanceDates.toSet(),
            expired.studentPackage.attendanceDates,
        )

        assertEquals(
            2,
            expired.studentPackage.usedClasses,
        )

        // And
        // no new attendance is persisted
        val persistedAttendance =
            database
                .attendanceDao()
                .getForPackage(packageId)

        assertEquals(
            2,
            persistedAttendance.size,
        )

        assertEquals(
            attendanceDates.map { it.toEpochDays() }.toSet(),
            persistedAttendance
                .map { it.attendedOnEpochDay }
                .toSet(),
        )
    }

    @Test
    fun `marking attendance without package returns package not found and persists no changes`() = runTest {
        // Given
        val studentId =
            database
                .studentDao()
                .insert(
                    StudentEntity(
                        fullName = "Anna Smith",
                    ),
                ).toInt()

        // When
        val result =
            markStudentPresent(
                studentId = studentId,
                attendanceDate = LocalDate(2026, 9, 30),
            )

        // Then
        assertIs<MarkStudentPresentResult.PackageNotFound>(result)

        // And
        // no package was created
        assertNull(
            database
                .studentPackageDao()
                .getLatestForStudent(studentId),
        )
    }

    @Test
    fun `failed attendance marking rolls back persisted attendance`() = runTest {
        // Given
        val studentId =
            database
                .studentDao()
                .insert(
                    StudentEntity(
                        fullName = "Anna Smith",
                    ),
                ).toInt()

        val packageTypeId =
            database
                .packageTypeDao()
                .insert(
                    PackageTypeEntity(
                        name = "5-class package",
                        totalClasses = 5,
                        validityWeeks = 6,
                        price = 1700,
                    ),
                ).toInt()

        val startedOn = LocalDate(2026, 9, 25)

        val packageId =
            database
                .studentPackageDao()
                .insert(
                    StudentPackageEntity(
                        studentId = studentId,
                        packageTypeId = packageTypeId,
                        packageName = "5-class package",
                        totalClasses = 5,
                        validityWeeks = 6,
                        price = 1700,
                        startedOnEpochDay = startedOn.toEpochDays(),
                    ),
                ).toInt()

        database.attendanceDao().insert(
            AttendanceEntity(
                packageId = packageId,
                attendedOnEpochDay = startedOn.toEpochDays(),
            ),
        )

        val attendanceDate = LocalDate(2026, 9, 27)

        val roomRepository =
            RoomStudentPackageRepository(
                studentPackageDao = database.studentPackageDao(),
                attendanceDao = database.attendanceDao(),
            )

        var attendanceWasPersisted = false

        val failingRepository =
            object : StudentPackageRepository by roomRepository {
                override suspend fun recordAttendance(packageId: Int, attendedOn: LocalDate) {
                    roomRepository.recordAttendance(
                        packageId = packageId,
                        attendedOn = attendedOn,
                    )

                    attendanceWasPersisted = true

                    error("Attendance marking failed")
                }
            }

        val failingMarkStudentPresent =
            MarkStudentPresentUseCase(
                repository = failingRepository,
                transactionRunner =
                RoomTransactionRunner(
                    database = database,
                ),
            )

        // When
        val exception =
            assertFailsWith<IllegalStateException> {
                failingMarkStudentPresent(
                    studentId = studentId,
                    attendanceDate = attendanceDate,
                )
            }

        // Then
        assertEquals(
            "Attendance marking failed",
            exception.message,
        )

        // the real Room insert completed before the later failure
        assertTrue(attendanceWasPersisted)

        // And
        // the new attendance was rolled back
        val persistedAttendance =
            database
                .attendanceDao()
                .getForPackage(packageId)

        assertEquals(
            1,
            persistedAttendance.size,
        )

        assertEquals(
            startedOn.toEpochDays(),
            persistedAttendance.single().attendedOnEpochDay,
        )
    }
}
