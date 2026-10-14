package com.theunderseer.simplio.feature.attendance.domain.usecase

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.entity.AttendanceEntity
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.transaction.RoomTransactionRunner
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentPackageRepository
import com.theunderseer.simplio.feature.attendance.domain.result.RenewStudentPackageResult
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.firstClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.fiveClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.registrationDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.secondClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.studentEntity
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.thirdClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.testPackageTypeEntity
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentGroupEntity
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentPackageEntity
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
    private lateinit var repository: RoomStudentPackageRepository
    private lateinit var renewStudentPackage: RenewStudentPackageUseCase

    @BeforeTest
    fun setUp() = runTest {
        database =
            Room
                .inMemoryDatabaseBuilder<AttendanceDatabase>()
                .setDriver(BundledSQLiteDriver())
                .build()

        database.studentGroupDao().insert(testStudentGroupEntity())

        repository = RoomStudentPackageRepository(
            studentPackageDao = database.studentPackageDao(),
            attendanceDao = database.attendanceDao(),
        )

        renewStudentPackage = RenewStudentPackageUseCase(
            repository = repository,
            transactionRunner = RoomTransactionRunner(
                database = database,
            ),
        )
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun `renewing completed package persists new package and first attendance`() = runTest {
        // Given
        val studentId = createStudentId()
        val packageType = persistPackageType()
        val oldStartedOn = registrationDate
        val oldAttendanceDates = listOf(
            firstClassDate,
            secondClassDate,
            thirdClassDate,
            LocalDate(2026, 10, 12),
            LocalDate(2026, 10, 16),
        )
        val oldPackageId = seedPackage(
            studentId = studentId,
            packageType = packageType,
            startedOn = oldStartedOn,
            attendanceDates = oldAttendanceDates,
        )
        val renewedOn = LocalDate(2026, 10, 19)

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
    fun `renewing active package returns active package and persists no changes`() = runTest {
        // Given
        val studentId = createStudentId()
        val packageType = persistPackageType()
        val startedOn = registrationDate
        val attendanceDates = listOf(
            firstClassDate,
            secondClassDate,
        )
        val packageId = seedPackage(
            studentId = studentId,
            packageType = packageType,
            startedOn = startedOn,
            attendanceDates = attendanceDates,
        )
        val renewedOn = thirdClassDate

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
            attendanceDates.toSet(),
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
    fun `renewing expired incomplete package persists new package and first attendance`() = runTest {
        // Given
        // Given
        val studentId = createStudentId()
        val packageType = persistPackageType()
        val oldStartedOn = LocalDate(2026, 7, 1)
        val oldAttendanceDates = listOf(
            oldStartedOn,
            LocalDate(2026, 7, 8),
        )
        val oldPackageId = seedPackage(
            studentId = studentId,
            packageType = packageType,
            startedOn = oldStartedOn,
            attendanceDates = oldAttendanceDates,
        )
        val renewedOn = registrationDate

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
    fun `failed renewal rolls back new package and first attendance`() = runTest {
        // Given
        // Given
        val studentId = createStudentId()
        val packageType = persistPackageType()
        val oldStartedOn = LocalDate(2026, 7, 1)
        val oldPackageId = seedPackage(
            studentId = studentId,
            packageType = packageType,
            startedOn = oldStartedOn,
        )
        val renewedOn = registrationDate
        val roomRepository = repository
        var createdPackageId: Int? = null

        val failingRepository =
            object : StudentPackageRepository by roomRepository {
                override suspend fun create(
                    studentId: Int,
                    packageType: PackageType,
                    startedOn: LocalDate,
                    visitCount: Int,
                ): StudentPackage {
                    val studentPackage =
                        roomRepository.create(
                            studentId = studentId,
                            packageType = packageType,
                            startedOn = startedOn,
                            visitCount = visitCount,
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

    private suspend fun createStudentId(): Int = database.studentDao().insert(
        studentEntity.copy(id = 0),
    ).toInt()

    private suspend fun persistPackageType(packageType: PackageType = fiveClassPackage): PackageType {
        val id = database.packageTypeDao().insert(
            testPackageTypeEntity(
                packageType = packageType,
            ).copy(id = 0),
        ).toInt()

        return packageType.copy(id = id)
    }

    private suspend fun seedPackage(
        studentId: Int,
        packageType: PackageType,
        startedOn: LocalDate,
        attendanceDates: List<LocalDate> = listOf(startedOn),
    ): Int {
        val packageId = database.studentPackageDao().insert(
            testStudentPackageEntity(
                studentId = studentId,
                packageType = packageType,
                startedOnEpochDay = startedOn.toEpochDays(),
            ).copy(id = 0),
        ).toInt()

        attendanceDates.forEach { date ->
            database.attendanceDao().insert(
                AttendanceEntity(
                    packageId = packageId,
                    attendedOnEpochDay = date.toEpochDays(),
                ),
            )
        }

        return packageId
    }
}
