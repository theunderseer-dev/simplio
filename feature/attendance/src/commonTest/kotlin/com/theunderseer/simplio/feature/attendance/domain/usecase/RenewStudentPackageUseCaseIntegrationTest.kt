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
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentPackageRepository
import com.theunderseer.simplio.feature.attendance.domain.result.RenewStudentPackageResult
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RenewStudentPackageUseCaseIntegrationTest {
    private lateinit var database: AttendanceDatabase
    private lateinit var renewStudentPackage: RenewStudentPackageUseCase

    @BeforeTest
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder<AttendanceDatabase>()
                .setDriver(BundledSQLiteDriver())
                .build()

        renewStudentPackage =
            RenewStudentPackageUseCase(
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
    fun `renewing completed package persists new package and first attendance`() =
        runTest {
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

            val packageType =
                PackageType(
                    id = packageTypeId,
                    name = "5-class package",
                    totalClasses = 5,
                    validityWeeks = 6,
                    price = 1700,
                )

            val oldStartedOn = LocalDate(2026, 8, 1)

            val oldPackageId =
                database
                    .studentPackageDao()
                    .insert(
                        StudentPackageEntity(
                            studentId = studentId,
                            packageTypeId = packageTypeId,
                            packageName = packageType.name,
                            totalClasses = packageType.totalClasses,
                            validityWeeks = packageType.validityWeeks,
                            price = packageType.price,
                            startedOnEpochDay = oldStartedOn.toEpochDays(),
                        ),
                    ).toInt()

            val oldAttendanceDates =
                listOf(
                    LocalDate(2026, 8, 1),
                    LocalDate(2026, 8, 8),
                    LocalDate(2026, 8, 15),
                    LocalDate(2026, 8, 22),
                    LocalDate(2026, 8, 29),
                )

            oldAttendanceDates.forEach { date ->
                database.attendanceDao().insert(
                    AttendanceEntity(
                        packageId = oldPackageId,
                        attendedOnEpochDay = date.toEpochDays(),
                    ),
                )
            }

            val renewedOn = LocalDate(2026, 8, 30)

            // When
            val result =
                renewStudentPackage(
                    studentId = studentId,
                    packageType = packageType,
                    renewedOn = renewedOn,
                )

            // Then
            val success =
                assertIs<RenewStudentPackageResult.Success>(result)

            assertTrue(
                success.studentPackage.id != oldPackageId,
            )

            assertEquals(
                studentId,
                success.studentPackage.studentId,
            )

            assertEquals(
                packageType,
                success.studentPackage.packageType,
            )

            assertEquals(
                renewedOn,
                success.studentPackage.startedOn,
            )

            assertEquals(
                setOf(renewedOn),
                success.studentPackage.attendanceDates,
            )

            // And
            // the new package is now the latest persisted package
            val persistedLatest =
                assertNotNull(
                    database
                        .studentPackageDao()
                        .getLatestForStudent(studentId),
                )

            assertEquals(
                success.studentPackage.id,
                persistedLatest.id,
            )

            // And
            // the first attendance of the renewed package is persisted
            val newAttendance =
                database
                    .attendanceDao()
                    .getForPackage(success.studentPackage.id)

            assertEquals(
                1,
                newAttendance.size,
            )

            // And
            // the previous package attendance remains unchanged
            assertEquals(
                oldAttendanceDates.map { it.toEpochDays() }.toSet(),
                database
                    .attendanceDao()
                    .getForPackage(oldPackageId)
                    .map { it.attendedOnEpochDay }
                    .toSet(),
            )

            val persistedAttendance =
                newAttendance.single()

            assertEquals(
                success.studentPackage.id,
                persistedAttendance.packageId,
            )

            assertEquals(
                renewedOn.toEpochDays(),
                persistedAttendance.attendedOnEpochDay,
            )
        }

    @Test
    fun `renewing active package returns active package and persists no changes`() =
        runTest {
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

            val packageType =
                PackageType(
                    id = packageTypeId,
                    name = "5-class package",
                    totalClasses = 5,
                    validityWeeks = 6,
                    price = 1700,
                )

            val startedOn = LocalDate(2026, 8, 1)

            val packageId =
                database
                    .studentPackageDao()
                    .insert(
                        StudentPackageEntity(
                            studentId = studentId,
                            packageTypeId = packageTypeId,
                            packageName = packageType.name,
                            totalClasses = packageType.totalClasses,
                            validityWeeks = packageType.validityWeeks,
                            price = packageType.price,
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

            val renewedOn = LocalDate(2026, 8, 30)

            // When
            val result =
                renewStudentPackage(
                    studentId = studentId,
                    packageType = packageType,
                    renewedOn = renewedOn,
                )

            // Then
            val activePackageExists =
                assertIs<RenewStudentPackageResult.ActivePackageExists>(result)

            assertEquals(
                packageId,
                activePackageExists.studentPackage.id,
            )

            assertEquals(
                packageType,
                activePackageExists.studentPackage.packageType,
            )

            assertEquals(
                startedOn,
                activePackageExists.studentPackage.startedOn,
            )

            assertEquals(
                studentId,
                activePackageExists.studentPackage.studentId,
            )

            assertEquals(
                setOf(
                    LocalDate(2026, 8, 1),
                    LocalDate(2026, 8, 8),
                ),
                activePackageExists.studentPackage.attendanceDates,
            )

            // And
            // the existing package remains the latest package
            val persistedLatest =
                assertNotNull(
                    database
                        .studentPackageDao()
                        .getLatestForStudent(studentId),
                )

            assertEquals(
                packageId,
                persistedLatest.id,
            )

            // And
            // no attendance was added
            assertEquals(
                attendanceDates.map { it.toEpochDays() }.toSet(),
                database
                    .attendanceDao()
                    .getForPackage(packageId)
                    .map { it.attendedOnEpochDay }
                    .toSet(),
            )
        }

    @Test
    fun `renewing expired incomplete package persists new package and first attendance`() =
        runTest {
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

            val packageType =
                PackageType(
                    id = packageTypeId,
                    name = "5-class package",
                    totalClasses = 5,
                    validityWeeks = 6,
                    price = 1700,
                )

            val oldStartedOn = LocalDate(2026, 7, 1)

            val oldPackageId =
                database
                    .studentPackageDao()
                    .insert(
                        StudentPackageEntity(
                            studentId = studentId,
                            packageTypeId = packageTypeId,
                            packageName = packageType.name,
                            totalClasses = packageType.totalClasses,
                            validityWeeks = packageType.validityWeeks,
                            price = packageType.price,
                            startedOnEpochDay = oldStartedOn.toEpochDays(),
                        ),
                    ).toInt()

            val oldAttendanceDates =
                listOf(
                    LocalDate(2026, 7, 1),
                    LocalDate(2026, 7, 8),
                )

            oldAttendanceDates.forEach { date ->
                database.attendanceDao().insert(
                    AttendanceEntity(
                        packageId = oldPackageId,
                        attendedOnEpochDay = date.toEpochDays(),
                    ),
                )
            }

            val renewedOn = LocalDate(2026, 9, 30)

            // When
            val result =
                renewStudentPackage(
                    studentId = studentId,
                    packageType = packageType,
                    renewedOn = renewedOn,
                )

            // Then
            val success =
                assertIs<RenewStudentPackageResult.Success>(result)

            assertTrue(
                success.studentPackage.id != oldPackageId,
            )

            assertEquals(
                studentId,
                success.studentPackage.studentId,
            )

            assertEquals(
                packageType,
                success.studentPackage.packageType,
            )

            assertEquals(
                renewedOn,
                success.studentPackage.startedOn,
            )

            assertEquals(
                setOf(renewedOn),
                success.studentPackage.attendanceDates,
            )

            // And
            // the renewed package is now the latest persisted package
            val persistedLatest =
                assertNotNull(
                    database
                        .studentPackageDao()
                        .getLatestForStudent(studentId),
                )

            assertEquals(
                success.studentPackage.id,
                persistedLatest.id,
            )

            // And
            // its first attendance is persisted
            val persistedAttendance =
                database
                    .attendanceDao()
                    .getForPackage(success.studentPackage.id)
                    .single()

            assertEquals(
                success.studentPackage.id,
                persistedAttendance.packageId,
            )

            assertEquals(
                renewedOn.toEpochDays(),
                persistedAttendance.attendedOnEpochDay,
            )

            // And
            // the expired package attendance remains unchanged
            assertEquals(
                oldAttendanceDates.map { it.toEpochDays() }.toSet(),
                database
                    .attendanceDao()
                    .getForPackage(oldPackageId)
                    .map { it.attendedOnEpochDay }
                    .toSet(),
            )
        }

    @Test
    fun `failed renewal rolls back new package and first attendance`() =
        runTest {
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

            val packageType =
                PackageType(
                    id = packageTypeId,
                    name = "5-class package",
                    totalClasses = 5,
                    validityWeeks = 6,
                    price = 1700,
                )

            val oldStartedOn = LocalDate(2026, 7, 1)

            val oldPackageId =
                database
                    .studentPackageDao()
                    .insert(
                        StudentPackageEntity(
                            studentId = studentId,
                            packageTypeId = packageTypeId,
                            packageName = packageType.name,
                            totalClasses = packageType.totalClasses,
                            validityWeeks = packageType.validityWeeks,
                            price = packageType.price,
                            startedOnEpochDay = oldStartedOn.toEpochDays(),
                        ),
                    ).toInt()

            database.attendanceDao().insert(
                AttendanceEntity(
                    packageId = oldPackageId,
                    attendedOnEpochDay = oldStartedOn.toEpochDays(),
                ),
            )

            val renewedOn = LocalDate(2026, 9, 30)

            val roomRepository =
                RoomStudentPackageRepository(
                    studentPackageDao = database.studentPackageDao(),
                    attendanceDao = database.attendanceDao(),
                )

            var createdPackageId: Int? = null

            val failingRepository =
                object : StudentPackageRepository by roomRepository {
                    override suspend fun create(
                        studentId: Int,
                        packageType: PackageType,
                        startedOn: LocalDate,
                    ): StudentPackage {
                        val studentPackage =
                            roomRepository.create(
                                studentId = studentId,
                                packageType = packageType,
                                startedOn = startedOn,
                            )

                        createdPackageId = studentPackage.id

                        error("Renewal failed")
                    }
                }

            val failingRenewStudentPackage =
                RenewStudentPackageUseCase(
                    repository = failingRepository,
                    transactionRunner =
                        RoomTransactionRunner(
                            database = database,
                        ),
                )

            // When
            val exception =
                assertFailsWith<IllegalStateException> {
                    failingRenewStudentPackage(
                        studentId = studentId,
                        packageType = packageType,
                        renewedOn = renewedOn,
                    )
                }

            // Then
            assertEquals(
                "Renewal failed",
                exception.message,
            )

            val rolledBackPackageId =
                assertNotNull(createdPackageId)

            // the real repository completed package + attendance creation
            // before the later failure
            assertTrue(
                rolledBackPackageId != oldPackageId,
            )

            // And
            // the old package is still the latest persisted package
            val persistedLatest =
                assertNotNull(
                    database
                        .studentPackageDao()
                        .getLatestForStudent(studentId),
                )

            assertEquals(
                oldPackageId,
                persistedLatest.id,
            )

            // And
            // the first attendance of the new package was rolled back
            assertEquals(
                emptyList(),
                database
                    .attendanceDao()
                    .getForPackage(rolledBackPackageId),
            )

            // And
            // the old package attendance is still persisted
            assertEquals(
                listOf(oldStartedOn.toEpochDays()),
                database
                    .attendanceDao()
                    .getForPackage(oldPackageId)
                    .map { it.attendedOnEpochDay },
            )
        }
}
