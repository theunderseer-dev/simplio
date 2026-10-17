package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.database.createAttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.data.local.transaction.RoomTransactionRunner
import com.theunderseer.simplio.feature.attendance.domain.model.InitialAttendance
import com.theunderseer.simplio.feature.attendance.domain.result.RemoveStudentAttendanceResult
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.duoSpecialPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.firstClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.secondClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.testPackageTypeEntity
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentGroupEntity
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class RemoveStudentAttendancePersistenceTest {
    @Test
    fun `removed attendance stays deleted and both visits remain restored after reopening database`() = runTest {
        // Given
        val temporaryDirectory =
            Files.createTempDirectory("simplio-attendance-removal-")
        val databasePath =
            temporaryDirectory.resolve("attendance.db").toString()
        val attendanceToRemove = secondClassDate

        try {
            val studentId = withDatabase(databasePath) { database ->
                val packageTypeId = database.packageTypeDao().insert(
                    testPackageTypeEntity(
                        packageType = duoSpecialPackage,
                    ).copy(id = 0),
                ).toInt()

                val packageType = duoSpecialPackage.copy(
                    id = packageTypeId,
                )

                val groupId = database.studentGroupDao().insert(
                    testStudentGroupEntity(),
                ).toInt()

                val student = RoomStudentRepository(
                    studentDao = database.studentDao(),
                ).create(
                    fullName = AttendanceTestData.student.fullName,
                    groupId = groupId,
                )

                val repository = RoomStudentPackageRepository(
                    studentPackageDao = database.studentPackageDao(),
                    attendanceDao = database.attendanceDao(),
                )

                val studentPackage = repository.create(
                    studentId = student.id,
                    packageType = packageType,
                    purchasedOn = firstClassDate,
                    initialAttendance = InitialAttendance(
                        date = firstClassDate,
                        visitCount = 2,
                    ),
                )

                repository.recordAttendance(
                    packageId = studentPackage.id,
                    attendedOn = attendanceToRemove,
                    visitCount = 2,
                )

                val before = assertNotNull(
                    repository.getLatestPackageForStudent(student.id),
                )

                assertEquals(
                    mapOf(
                        firstClassDate to 2,
                        attendanceToRemove to 2,
                    ),
                    before.attendanceHistory.visitsByDate,
                )
                assertEquals(4, before.remainingClasses)

                val removeAttendance = RemoveStudentAttendanceUseCase(
                    repository = repository,
                    transactionRunner = RoomTransactionRunner(
                        database = database,
                    ),
                )

                // When
                assertIs<RemoveStudentAttendanceResult.Success>(
                    removeAttendance(
                        studentId = student.id,
                        attendanceDate = attendanceToRemove,
                    ),
                )

                student.id
            }

            // And: the database is reopened
            withDatabase(databasePath) { database ->
                val repository = RoomStudentPackageRepository(
                    studentPackageDao = database.studentPackageDao(),
                    attendanceDao = database.attendanceDao(),
                )

                val restoredPackage = assertNotNull(
                    repository.getLatestPackageForStudent(studentId),
                )

                // Then: only the first attendance remains
                assertEquals(
                    mapOf(firstClassDate to 2),
                    restoredPackage.attendanceHistory.visitsByDate,
                )
                assertEquals(2, restoredPackage.usedClasses)
                assertEquals(6, restoredPackage.remainingClasses)
                assertEquals(firstClassDate, restoredPackage.purchasedOn)
                assertEquals(firstClassDate, restoredPackage.startedOn)
            }
        } finally {
            temporaryDirectory.toFile().deleteRecursively()
        }
    }

    private suspend fun <T> withDatabase(path: String, block: suspend (AttendanceDatabase) -> T): T {
        val database = createAttendanceDatabase(path)

        try {
            return block(database)
        } finally {
            database.close()
        }
    }
}
