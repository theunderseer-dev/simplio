package com.theunderseer.simplio.feature.attendance.domain.usecase

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.entity.AttendanceEntity
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.data.local.transaction.RoomTransactionRunner
import com.theunderseer.simplio.feature.attendance.domain.model.DefaultStudentGroups
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentRepository
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.duoSpecialPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.fiveClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.fourClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.registrationDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.student
import com.theunderseer.simplio.feature.attendance.fixtures.testPackageTypeEntity
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentEntity
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentGroupEntity
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentPackageEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RegisterStudentUseCaseIntegrationTest {
    private val groupId = DefaultStudentGroups.GROUP_A.id
    private lateinit var database: AttendanceDatabase
    private lateinit var studentRepository: RoomStudentRepository
    private lateinit var studentPackageRepository: RoomStudentPackageRepository
    private lateinit var registerStudent: RegisterStudentUseCase

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

        studentRepository = RoomStudentRepository(
            studentDao = database.studentDao(),
        )
        studentPackageRepository = RoomStudentPackageRepository(
            studentPackageDao = database.studentPackageDao(),
            attendanceDao = database.attendanceDao(),
        )

        registerStudent = RegisterStudentUseCase(
            studentRepository = studentRepository,
            studentPackageRepository = studentPackageRepository,
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
    fun `registering student persists student package and first attendance`() = runTest {
        // Given
        val packageType = persistPackageType(fourClassPackage)

        // When
        val registeredStudent = registerStudent(
            fullName = student.fullName,
            packageType = packageType,
            registeredOn = registrationDate,
            groupId = groupId,
        )

        // Then
        val createdStudent = registeredStudent.student

        assertTrue(createdStudent.id > 0)
        assertEquals(student.fullName, createdStudent.fullName)
        assertEquals(groupId, createdStudent.groupId)

        val persistedStudent = assertNotNull(
            database.studentDao().getById(createdStudent.id),
        )

        assertEquals(
            testStudentEntity(
                fullName = student.fullName,
                groupId = groupId,
            ).copy(id = createdStudent.id),
            persistedStudent,
        )

        // And: the purchased package is persisted for this student
        val persistedPackage = assertNotNull(
            database.studentPackageDao().getLatestForStudent(
                createdStudent.id,
            ),
        )

        assertEquals(
            testStudentPackageEntity(
                studentId = createdStudent.id,
                packageType = packageType,
                startedOnEpochDay = registrationDate.toEpochDays(),
            ).copy(id = registeredStudent.studentPackage.id),
            persistedPackage,
        )

        // And: the first attendance is returned and persisted
        assertEquals(
            setOf(registrationDate),
            registeredStudent.studentPackage.attendanceDates,
        )
        assertEquals(
            listOf(
                AttendanceEntity(
                    packageId = persistedPackage.id,
                    attendedOnEpochDay = registrationDate.toEpochDays(),
                    visitCount = 1,
                ),
            ),
            database.attendanceDao().getForPackage(persistedPackage.id),
        )
    }

    @Test
    fun `failed registration rolls back created student`() = runTest {
        // Given: this package type does not exist in Room
        val packageType = fiveClassPackage.copy(id = 999)

        assertEquals(0, database.studentDao().count())

        var studentWasCreated = false

        val trackingStudentRepository =
            object : StudentRepository by studentRepository {
                override suspend fun create(fullName: String, groupId: Int): Student {
                    val createdStudent = studentRepository.create(
                        fullName = fullName,
                        groupId = groupId,
                    )

                    studentWasCreated = true

                    return createdStudent
                }
            }

        val failingRegisterStudent = RegisterStudentUseCase(
            studentRepository = trackingStudentRepository,
            studentPackageRepository = studentPackageRepository,
            transactionRunner = RoomTransactionRunner(
                database = database,
            ),
        )

        // When
        assertFails {
            failingRegisterStudent(
                fullName = student.fullName,
                packageType = packageType,
                registeredOn = registrationDate,
                groupId = groupId,
            )
        }

        // Then: student creation completed before the failure
        assertTrue(studentWasCreated)

        // And: the transaction rolled the created student back
        assertEquals(0, database.studentDao().count())
    }

    @Test
    fun `registering duo package persists two visits for first attendance`() = runTest {
        // Given
        val packageType = persistPackageType(duoSpecialPackage)

        // When
        val result = registerStudent(
            fullName = student.fullName,
            packageType = packageType,
            registeredOn = registrationDate,
            visitCount = 2,
            groupId = groupId,
        )

        // Then
        assertEquals(
            mapOf(registrationDate to 2),
            result.studentPackage.attendanceHistory.visitsByDate,
        )
        assertEquals(2, result.studentPackage.usedClasses)
        assertEquals(6, result.studentPackage.remainingClasses)

        assertEquals(
            listOf(
                AttendanceEntity(
                    packageId = result.studentPackage.id,
                    attendedOnEpochDay = registrationDate.toEpochDays(),
                    visitCount = 2,
                ),
            ),
            database.attendanceDao().getForPackage(result.studentPackage.id),
        )

        // And: reading the package restores its terms and visit count
        val reloadedPackage = assertNotNull(
            studentPackageRepository.getLatestPackageForStudent(
                result.student.id,
            ),
        )

        assertEquals(packageType, reloadedPackage.packageType)
        assertEquals(result.studentPackage, reloadedPackage)
    }

    private suspend fun persistPackageType(packageType: PackageType): PackageType {
        val id = database.packageTypeDao().insert(
            testPackageTypeEntity(
                packageType = packageType,
            ).copy(id = 0),
        ).toInt()

        return packageType.copy(id = id)
    }
}
