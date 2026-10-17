package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.database.createAttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.data.local.transaction.RoomTransactionRunner
import com.theunderseer.simplio.feature.attendance.domain.model.InitialAttendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageAttendanceHistory
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentPackageRepository
import com.theunderseer.simplio.feature.attendance.domain.result.MarkStudentPresentResult
import com.theunderseer.simplio.feature.attendance.domain.result.RemoveStudentAttendanceResult
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.duoSpecialPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.firstClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.secondClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.thirdClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.testPackageTypeEntity
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentGroupEntity
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoveFirstAttendancePersistenceTest {
    @Test
    fun `removing only attendance stays unactivated after reopening and can activate again`() = runTest {
        withTemporaryDatabase { path ->
            // Given
            val registration = withDatabase(path) { database ->
                val registration = seedPackage(database, firstAttendanceDate = secondClassDate)
                val repository = repository(database)
                val removeAttendance = RemoveStudentAttendanceUseCase(
                    repository = repository,
                    transactionRunner = RoomTransactionRunner(database),
                )

                // When
                val result = removeAttendance(registration.student.id, secondClassDate)

                // Then
                assertEquals(
                    registration.studentPackage.copy(
                        startedOn = null,
                        attendanceHistory = PackageAttendanceHistory(visitsByDate = emptyMap()),
                    ),
                    assertIs<RemoveStudentAttendanceResult.Success>(result).studentPackage,
                )
                registration
            }

            withDatabase(path) { database ->
                val repository = repository(database)
                val stored = assertNotNull(repository.getLatestPackageForStudent(registration.student.id))

                assertEquals(registration.studentPackage.id, stored.id)
                assertEquals(firstClassDate, stored.purchasedOn)
                assertEquals(registration.studentPackage.packageType, stored.packageType)
                assertEquals(0, stored.usedClasses)
                assertEquals(8, stored.remainingClasses)
                assertNull(stored.startedOn)
                assertNull(stored.expiresOn)
                assertTrue(stored.attendanceDates.isEmpty())

                // When: a later attendance activates the same package again
                val markAttendance = MarkStudentPresentUseCase(
                    repository = repository,
                    transactionRunner = RoomTransactionRunner(database),
                )
                assertIs<MarkStudentPresentResult.Success>(
                    markAttendance(registration.student.id, thirdClassDate, visitCount = 2),
                )
            }

            // Then: reactivation also persists
            withDatabase(path) { database ->
                val stored = assertNotNull(repository(database).getLatestPackageForStudent(registration.student.id))
                assertEquals(firstClassDate, stored.purchasedOn)
                assertEquals(thirdClassDate, stored.startedOn)
                assertEquals(LocalDate(2026, 11, 6), stored.expiresOn)
                assertEquals(mapOf(thirdClassDate to 2), stored.attendanceHistory.visitsByDate)
                assertEquals(2, stored.usedClasses)
                assertEquals(6, stored.remainingClasses)
            }
        }
    }

    @Test
    fun `removing first attendance persists earliest remaining activation and visit counts`() = runTest {
        withTemporaryDatabase { path ->
            // Given
            val registration = withDatabase(path) { database ->
                val registration = seedPackage(database)
                val repository = repository(database)
                repository.recordAttendance(registration.studentPackage.id, thirdClassDate, visitCount = 2)
                repository.recordAttendance(registration.studentPackage.id, secondClassDate, visitCount = 1)
                val removeAttendance = RemoveStudentAttendanceUseCase(
                    repository = repository,
                    transactionRunner = RoomTransactionRunner(database),
                )

                // When
                assertIs<RemoveStudentAttendanceResult.Success>(
                    removeAttendance(registration.student.id, firstClassDate),
                )
                registration
            }

            // Then
            withDatabase(path) { database ->
                val stored = assertNotNull(repository(database).getLatestPackageForStudent(registration.student.id))
                assertEquals(firstClassDate, stored.purchasedOn)
                assertEquals(registration.studentPackage.packageType, stored.packageType)
                assertEquals(secondClassDate, stored.startedOn)
                assertEquals(LocalDate(2026, 11, 2), stored.expiresOn)
                assertEquals(
                    mapOf(secondClassDate to 1, thirdClassDate to 2),
                    stored.attendanceHistory.visitsByDate,
                )
                assertEquals(3, stored.usedClasses)
                assertEquals(5, stored.remainingClasses)
            }
        }
    }

    @Test
    fun `failed removal rolls back attendance and activation together`() = runTest {
        withTemporaryDatabase { path ->
            withDatabase(path) { database ->
                // Given
                val registration = seedPackage(database)
                val repository = repository(database)
                var activationWasReset = false
                val failingRepository = object : StudentPackageRepository by repository {
                    override suspend fun removeAttendance(packageId: Int, attendedOn: LocalDate) {
                        repository.removeAttendance(packageId, attendedOn)
                        val removed = assertNotNull(repository.getLatestPackageForStudent(registration.student.id))
                        assertNull(removed.startedOn)
                        assertNull(removed.expiresOn)
                        assertTrue(removed.attendanceDates.isEmpty())
                        activationWasReset = true
                        error("Removal failed")
                    }
                }
                val removeAttendance = RemoveStudentAttendanceUseCase(
                    repository = failingRepository,
                    transactionRunner = RoomTransactionRunner(database),
                )

                // When
                val error = assertFailsWith<IllegalStateException> {
                    removeAttendance(registration.student.id, firstClassDate)
                }

                // Then
                assertTrue(activationWasReset)
                assertEquals("Removal failed", error.message)
                assertEquals(
                    registration.studentPackage,
                    repository.getLatestPackageForStudent(registration.student.id),
                )
            }
        }
    }

    private suspend fun seedPackage(
        database: AttendanceDatabase,
        firstAttendanceDate: LocalDate = firstClassDate,
    ): RegisteredStudent {
        val groupId = database.studentGroupDao().insert(testStudentGroupEntity()).toInt()
        val packageTypeId = database.packageTypeDao().insert(
            testPackageTypeEntity(packageType = duoSpecialPackage),
        ).toInt()
        val student = RoomStudentRepository(database.studentDao()).create(
            fullName = AttendanceTestData.student.fullName,
            groupId = groupId,
        )
        val studentPackage = repository(database).create(
            studentId = student.id,
            packageType = duoSpecialPackage.copy(id = packageTypeId),
            purchasedOn = firstClassDate,
            initialAttendance = InitialAttendance(date = firstAttendanceDate, visitCount = 2),
        )

        return RegisteredStudent(student = student, studentPackage = studentPackage)
    }

    private fun repository(database: AttendanceDatabase): RoomStudentPackageRepository = RoomStudentPackageRepository(
        studentPackageDao = database.studentPackageDao(),
        attendanceDao = database.attendanceDao(),
    )

    private suspend fun <T> withDatabase(path: String, block: suspend (AttendanceDatabase) -> T): T {
        val database = createAttendanceDatabase(path)

        try {
            return block(database)
        } finally {
            database.close()
        }
    }

    private suspend fun <T> withTemporaryDatabase(block: suspend (String) -> T): T {
        val directory = Files.createTempDirectory("simplio-first-attendance-removal-")

        try {
            return block(directory.resolve("attendance.db").toString())
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
