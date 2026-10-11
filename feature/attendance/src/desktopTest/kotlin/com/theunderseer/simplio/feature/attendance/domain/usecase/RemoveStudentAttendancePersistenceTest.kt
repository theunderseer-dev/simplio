package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.database.createAttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.entity.PackageTypeEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentGroupEntity
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.data.local.transaction.RoomTransactionRunner
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.result.RemoveStudentAttendanceResult
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class RemoveStudentAttendancePersistenceTest {
    @Test
    fun `removed attendance stays deleted and both visits remain restored after reopening database`() = runTest {
        // Given
        val temporaryDirectory: Path =
            Files.createTempDirectory("simplio-attendance-removal-")
        val databasePath: String =
            temporaryDirectory.resolve("attendance.db").toString()

        val firstAttendance = LocalDate(2026, 10, 2)
        val attendanceToRemove = LocalDate(2026, 10, 5)

        try {
            val studentId = withDatabase(databasePath) { database ->
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

                val packageType = PackageType(
                    id = packageTypeId,
                    name = "Duo 8 · Special rate",
                    totalClasses = 8,
                    validityWeeks = 4,
                    price = 2400,
                    maxVisitsPerAttendance = 2,
                )

                val groupId =
                    database.studentGroupDao().insert(
                        StudentGroupEntity(
                            id = 1,
                            name = "Group A",
                        ),
                    ).toInt()

                val student =
                    RoomStudentRepository(
                        studentDao = database.studentDao(),
                    ).create(
                        fullName = "Anna and John",
                        groupId = groupId,
                    )

                val repository = RoomStudentPackageRepository(
                    studentPackageDao = database.studentPackageDao(),
                    attendanceDao = database.attendanceDao(),
                )

                val studentPackage = repository.create(
                    studentId = student.id,
                    packageType = packageType,
                    startedOn = firstAttendance,
                    visitCount = 2,
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
                        firstAttendance to 2,
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
                val repository =
                    RoomStudentPackageRepository(
                        studentPackageDao = database.studentPackageDao(),
                        attendanceDao = database.attendanceDao(),
                    )

                val restoredPackage =
                    assertNotNull(
                        repository.getLatestPackageForStudent(studentId),
                    )

                // Then: only registration attendance remains
                assertEquals(
                    mapOf(firstAttendance to 2),
                    restoredPackage.attendanceHistory.visitsByDate,
                )
                assertEquals(2, restoredPackage.usedClasses)
                assertEquals(6, restoredPackage.remainingClasses)
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
