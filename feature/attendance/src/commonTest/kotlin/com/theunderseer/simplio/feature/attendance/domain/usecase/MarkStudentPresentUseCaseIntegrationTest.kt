package com.theunderseer.simplio.feature.attendance.domain.usecase

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.entity.AttendanceEntity
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.transaction.RoomTransactionRunner
import com.theunderseer.simplio.feature.attendance.domain.model.DefaultStudentGroups
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentPackageRepository
import com.theunderseer.simplio.feature.attendance.domain.result.MarkStudentPresentResult
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.firstClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.fiveClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.registrationDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.secondClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.thirdClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.testPackageTypeEntity
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentEntity
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarkStudentPresentUseCaseIntegrationTest {
    private val groupId = DefaultStudentGroups.GROUP_A.id
    private lateinit var database: AttendanceDatabase
    private lateinit var repository: RoomStudentPackageRepository
    private lateinit var markStudentPresent: MarkStudentPresentUseCase

    @BeforeTest
    fun setUp() = runTest {
        database = Room
            .inMemoryDatabaseBuilder<AttendanceDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()

        database.studentGroupDao().insert(
            testStudentGroupEntity(
                id = groupId,
                name = DefaultStudentGroups.GROUP_A.name,
            ),
        )

        repository = RoomStudentPackageRepository(
            studentPackageDao = database.studentPackageDao(),
            attendanceDao = database.attendanceDao(),
        )

        markStudentPresent = MarkStudentPresentUseCase(
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
    fun `marking student present persists attendance for active package`() = runTest {
        // Given
        val studentId = createStudentId()
        val packageId = seedPackage(studentId = studentId)

        // When
        val result = markStudentPresent(
            studentId = studentId,
            attendanceDate = secondClassDate,
        )

        // Then
        val success = assertIs<MarkStudentPresentResult.Success>(result)

        assertEquals(packageId, success.studentPackage.id)
        assertEquals(
            setOf(registrationDate, secondClassDate),
            success.studentPackage.attendanceDates,
        )
        assertEquals(2, success.studentPackage.usedClasses)

        // And: both attendance rows are persisted
        assertEquals(
            listOf(
                AttendanceEntity(
                    packageId = packageId,
                    attendedOnEpochDay = registrationDate.toEpochDays(),
                    visitCount = 1,
                ),
                AttendanceEntity(
                    packageId = packageId,
                    attendedOnEpochDay = secondClassDate.toEpochDays(),
                    visitCount = 1,
                ),
            ),
            database.attendanceDao().getForPackage(packageId),
        )
    }

    @Test
    fun `marking student present on same date does not duplicate attendance`() = runTest {
        // Given
        val studentId = createStudentId()
        val packageId = seedPackage(studentId = studentId)

        // When
        val result = markStudentPresent(
            studentId = studentId,
            attendanceDate = registrationDate,
        )

        // Then
        val success = assertIs<MarkStudentPresentResult.Success>(result)

        assertEquals(packageId, success.studentPackage.id)
        assertEquals(
            setOf(registrationDate),
            success.studentPackage.attendanceDates,
        )
        assertEquals(1, success.studentPackage.usedClasses)

        // And: the original attendance row is preserved
        assertEquals(
            listOf(
                AttendanceEntity(
                    packageId = packageId,
                    attendedOnEpochDay = registrationDate.toEpochDays(),
                    visitCount = 1,
                ),
            ),
            database.attendanceDao().getForPackage(packageId),
        )
    }

    @Test
    fun `marking attendance on completed package returns completed and persists no changes`() = runTest {
        // Given
        val studentId = createStudentId()
        val attendanceDates = setOf(
            firstClassDate,
            secondClassDate,
            thirdClassDate,
            LocalDate(2026, 10, 12),
            LocalDate(2026, 10, 16),
        )
        val packageId = seedPackage(
            studentId = studentId,
            attendanceDates = attendanceDates,
        )
        val attendanceDate = LocalDate(2026, 10, 19)

        // When
        val result = markStudentPresent(
            studentId = studentId,
            attendanceDate = attendanceDate,
        )

        // Then
        val completed = assertIs<MarkStudentPresentResult.PackageCompleted>(result)

        assertEquals(packageId, completed.studentPackage.id)
        assertEquals(attendanceDates, completed.studentPackage.attendanceDates)
        assertEquals(5, completed.studentPackage.usedClasses)
        assertTrue(completed.studentPackage.isCompleted)

        // And: the stored history is unchanged
        val persistedAttendance = database.attendanceDao().getForPackage(packageId)

        assertEquals(5, persistedAttendance.size)
        assertEquals(
            attendanceDates.map { it.toEpochDays() }.toSet(),
            persistedAttendance.map { it.attendedOnEpochDay }.toSet(),
        )
    }

    @Test
    fun `marking attendance on expired package returns expired and persists no changes`() = runTest {
        // Given
        val studentId = createStudentId()
        val startedOn = LocalDate(2026, 8, 1)
        val attendanceDates = setOf(
            startedOn,
            LocalDate(2026, 8, 8),
        )
        val packageId = seedPackage(
            studentId = studentId,
            startedOn = startedOn,
            attendanceDates = attendanceDates,
        )

        // When
        val result = markStudentPresent(
            studentId = studentId,
            attendanceDate = secondClassDate,
        )

        // Then
        val expired = assertIs<MarkStudentPresentResult.PackageExpired>(result)

        assertEquals(packageId, expired.studentPackage.id)
        assertEquals(attendanceDates, expired.studentPackage.attendanceDates)
        assertEquals(2, expired.studentPackage.usedClasses)

        // And: the stored history is unchanged
        val persistedAttendance = database.attendanceDao().getForPackage(packageId)

        assertEquals(2, persistedAttendance.size)
        assertEquals(
            attendanceDates.map { it.toEpochDays() }.toSet(),
            persistedAttendance.map { it.attendedOnEpochDay }.toSet(),
        )
    }

    @Test
    fun `marking attendance without package returns package not found and persists no changes`() = runTest {
        // Given
        val studentId = createStudentId()

        // When
        val result = markStudentPresent(
            studentId = studentId,
            attendanceDate = secondClassDate,
        )

        // Then
        assertIs<MarkStudentPresentResult.PackageNotFound>(result)

        // And: no package was created
        assertNull(
            database.studentPackageDao().getLatestForStudent(studentId),
        )
    }

    @Test
    fun `failed attendance marking rolls back persisted attendance`() = runTest {
        // Given
        val studentId = createStudentId()
        val packageId = seedPackage(studentId = studentId)

        var attendanceWasPersisted = false

        val failingRepository =
            object : StudentPackageRepository by repository {
                override suspend fun recordAttendance(packageId: Int, attendedOn: LocalDate, visitCount: Int) {
                    repository.recordAttendance(
                        packageId = packageId,
                        attendedOn = attendedOn,
                        visitCount = visitCount,
                    )

                    attendanceWasPersisted = true
                    error("Attendance marking failed")
                }
            }

        val failingMarkStudentPresent = MarkStudentPresentUseCase(
            repository = failingRepository,
            transactionRunner = RoomTransactionRunner(
                database = database,
            ),
        )

        // When
        val exception = assertFailsWith<IllegalStateException> {
            failingMarkStudentPresent(
                studentId = studentId,
                attendanceDate = secondClassDate,
            )
        }

        // Then
        assertEquals("Attendance marking failed", exception.message)

        // And: the real insert completed before the failure
        assertTrue(attendanceWasPersisted)

        // And: the new attendance was rolled back
        assertEquals(
            listOf(
                AttendanceEntity(
                    packageId = packageId,
                    attendedOnEpochDay = registrationDate.toEpochDays(),
                    visitCount = 1,
                ),
            ),
            database.attendanceDao().getForPackage(packageId),
        )
    }

    private suspend fun createStudentId(): Int = database.studentDao().insert(
        testStudentEntity(groupId = groupId),
    ).toInt()

    private suspend fun seedPackage(
        studentId: Int,
        startedOn: LocalDate = registrationDate,
        attendanceDates: Set<LocalDate> = setOf(startedOn),
    ): Int {
        val template = fiveClassPackage.copy(validityWeeks = 6)

        val packageTypeId = database.packageTypeDao().insert(
            testPackageTypeEntity(
                packageType = template,
            ).copy(id = 0),
        ).toInt()

        val packageId = database.studentPackageDao().insert(
            testStudentPackageEntity(
                studentId = studentId,
                packageType = template.copy(id = packageTypeId),
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
