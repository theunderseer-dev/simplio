package com.theunderseer.simplio.feature.attendance.domain.usecase

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.transaction.RoomTransactionRunner
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.duoSpecialPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.registrationDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.secondClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.testPackageTypeEntity
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentEntity
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentGroupEntity
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarkStudentPresentActivationIntegrationTest {
    @Test
    fun `failed first attendance rolls back activation and both visits`() = runTest {
        val database = Room
            .inMemoryDatabaseBuilder<AttendanceDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()

        try {
            // Given
            database.studentGroupDao().insert(testStudentGroupEntity())

            val studentId = database.studentDao()
                .insert(testStudentEntity())
                .toInt()

            val packageTypeId = database.packageTypeDao()
                .insert(testPackageTypeEntity(packageType = duoSpecialPackage))
                .toInt()

            val packageType = duoSpecialPackage.copy(id = packageTypeId)

            val roomRepository = RoomStudentPackageRepository(database)

            val purchasedPackage = roomRepository.create(
                studentId = studentId,
                packageType = packageType,
                purchasedOn = registrationDate,
                initialAttendance = null,
            )

            var attendanceWasRecorded = false

            val failingRepository =
                object : StudentPackageRepository by roomRepository {
                    override suspend fun recordAttendance(packageId: Int, attendedOn: LocalDate, visitCount: Int) {
                        roomRepository.recordAttendance(
                            packageId = packageId,
                            attendedOn = attendedOn,
                            visitCount = visitCount,
                        )

                        val savedPackage = assertNotNull(
                            roomRepository.getLatestPackageForStudent(studentId),
                        )

                        assertEquals(attendedOn, savedPackage.startedOn)
                        assertEquals(
                            mapOf(attendedOn to visitCount),
                            savedPackage.attendanceHistory.visitsByDate,
                        )

                        attendanceWasRecorded = true

                        error("Attendance save failed")
                    }
                }

            val markStudentPresent = MarkStudentPresentUseCase(
                repository = failingRepository,
                transactionRunner = RoomTransactionRunner(
                    database = database,
                ),
            )

            // When
            val error = assertFailsWith<IllegalStateException> {
                markStudentPresent(
                    studentId = studentId,
                    attendanceDate = secondClassDate,
                    visitCount = 2,
                )
            }

            // Then: both writes completed before the failure
            assertEquals("Attendance save failed", error.message)
            assertTrue(attendanceWasRecorded)

            // And: the original purchased package is restored
            assertEquals(
                purchasedPackage,
                roomRepository.getLatestPackageForStudent(studentId),
            )

            val persistedPackage = assertNotNull(
                database.studentPackageDao().getLatestForStudent(studentId),
            )

            assertNull(persistedPackage.startedOnEpochDay)
            assertTrue(
                database.attendanceDao()
                    .getForPackage(purchasedPackage.id)
                    .isEmpty(),
            )
        } finally {
            database.close()
        }
    }
}
