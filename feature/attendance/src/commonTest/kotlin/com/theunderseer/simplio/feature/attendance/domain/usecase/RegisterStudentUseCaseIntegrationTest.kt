package com.theunderseer.simplio.feature.attendance.domain.usecase

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.entity.AttendanceEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.PackageTypeEntity
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.data.local.transaction.RoomTransactionRunner
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentRepository
import com.theunderseer.simplio.feature.attendance.presentation.testFiveClassPackage
import com.theunderseer.simplio.feature.attendance.presentation.testFourClassPackage
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RegisterStudentUseCaseIntegrationTest {
    private lateinit var database: AttendanceDatabase
    private lateinit var registerStudent: RegisterStudentUseCase

    @BeforeTest
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder<AttendanceDatabase>()
                .setDriver(BundledSQLiteDriver())
                .build()

        registerStudent =
            RegisterStudentUseCase(
                studentRepository =
                RoomStudentRepository(
                    studentDao = database.studentDao(),
                ),
                studentPackageRepository =
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
    fun `registering student persists student package and first attendance`() = runTest {
        // Given
        val packageTemplate = testFourClassPackage()

        val packageTypeId =
            database
                .packageTypeDao()
                .insert(
                    PackageTypeEntity(
                        name = packageTemplate.name,
                        totalClasses = packageTemplate.totalClasses,
                        validityWeeks = packageTemplate.validityWeeks,
                        price = packageTemplate.price,
                    ),
                ).toInt()

        val packageType = testFiveClassPackage(id = packageTypeId)

        val registeredOn = LocalDate(2026, 9, 30)

        // When
        val registeredStudent =
            registerStudent(
                fullName = "Anna Smith",
                packageType = packageType,
                registeredOn = registeredOn,
            )

        // Then
        assertTrue(registeredStudent.student.id > 0)

        assertEquals(
            "Anna Smith",
            registeredStudent.student.fullName,
        )

        val persistedStudent =
            assertNotNull(
                database
                    .studentDao()
                    .getById(registeredStudent.student.id),
            )

        assertEquals(
            "Anna Smith",
            persistedStudent.fullName,
        )

        // And
        // the package is persisted for the created student
        val persistedPackage =
            assertNotNull(
                database
                    .studentPackageDao()
                    .getLatestForStudent(
                        registeredStudent.student.id,
                    ),
            )

        assertEquals(
            registeredStudent.studentPackage.id,
            persistedPackage.id,
        )

        assertEquals(
            registeredStudent.student.id,
            persistedPackage.studentId,
        )

        assertEquals(
            packageType.id,
            persistedPackage.packageTypeId,
        )

        assertEquals(
            registeredOn.toEpochDays(),
            persistedPackage.startedOnEpochDay,
        )

        // And
        // the first attendance is persisted
        val attendance =
            database
                .attendanceDao()
                .getForPackage(persistedPackage.id)

        assertEquals(
            1,
            attendance.size,
        )

        assertEquals(
            setOf(registeredOn),
            registeredStudent.studentPackage.attendanceDates,
        )

        val persistedAttendance =
            attendance.single()

        assertEquals(
            persistedPackage.id,
            persistedAttendance.packageId,
        )

        assertEquals(
            registeredOn.toEpochDays(),
            persistedAttendance.attendedOnEpochDay,
        )
    }

    @Test
    fun `failed registration rolls back created student`() = runTest {
        // Given
        // a package type that does not exist in Room
        val packageType = testFiveClassPackage(999)

        val registeredOn = LocalDate(2026, 9, 30)

        assertEquals(
            0,
            database.studentDao().count(),
        )

        var studentWasCreated = false

        val roomStudentRepository =
            RoomStudentRepository(
                studentDao = database.studentDao(),
            )

        val studentRepository =
            object : StudentRepository by roomStudentRepository {
                override suspend fun create(fullName: String): Student {
                    val student =
                        roomStudentRepository.create(
                            fullName = fullName,
                        )

                    studentWasCreated = true

                    return student
                }
            }

        val failingRegisterStudent =
            RegisterStudentUseCase(
                studentRepository = studentRepository,
                studentPackageRepository =
                RoomStudentPackageRepository(
                    studentPackageDao = database.studentPackageDao(),
                    attendanceDao = database.attendanceDao(),
                ),
                transactionRunner =
                RoomTransactionRunner(
                    database = database,
                ),
            )

        // When
        assertFails {
            failingRegisterStudent(
                fullName = "Anna Smith",
                packageType = packageType,
                registeredOn = registeredOn,
            )
        }

        // Then
        // student creation completed before the later failure
        assertTrue(studentWasCreated)

        // And
        // the transaction rolled the created student back
        assertEquals(
            0,
            database.studentDao().count(),
        )
    }

    @Test
    fun `registering duo package persists two visits for first attendance`() = runTest {
        // Given
        val packageTypeId =
            database.packageTypeDao().insert(
                PackageTypeEntity(
                    name = "Duo 8 · Special rate",
                    totalClasses = 8,
                    validityWeeks = 4,
                    price = 2400,
                    maxVisitsPerAttendance = 2,
                ),
            ).toInt()

        val packageType =
            PackageType(
                id = packageTypeId,
                name = "Duo 8 · Special rate",
                totalClasses = 8,
                validityWeeks = 4,
                price = 2400,
                maxVisitsPerAttendance = 2,
            )

        val registeredOn = LocalDate(2026, 10, 9)

        // When
        val result =
            registerStudent(
                fullName = "Anna & John",
                packageType = packageType,
                registeredOn = registeredOn,
                visitCount = 2,
            )

        // Then
        assertEquals(
            mapOf(registeredOn to 2),
            result.studentPackage.attendanceHistory.visitsByDate,
        )

        assertEquals(2, result.studentPackage.usedClasses)
        assertEquals(6, result.studentPackage.remainingClasses)

        assertEquals(
            listOf(
                AttendanceEntity(
                    packageId = result.studentPackage.id,
                    attendedOnEpochDay = registeredOn.toEpochDays(),
                    visitCount = 2,
                ),
            ),
            database.attendanceDao().getForPackage(result.studentPackage.id),
        )

        // And: reading the package restores its terms and visit count
        val repository =
            RoomStudentPackageRepository(
                studentPackageDao = database.studentPackageDao(),
                attendanceDao = database.attendanceDao(),
            )

        val reloadedPackage =
            assertNotNull(
                repository.getLatestPackageForStudent(result.student.id),
            )

        assertEquals(packageType, reloadedPackage.packageType)
        assertEquals(result.studentPackage, reloadedPackage)
    }
}
