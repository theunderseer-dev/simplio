package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.database.createAttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.data.local.transaction.RoomTransactionRunner
import com.theunderseer.simplio.feature.attendance.domain.model.InitialAttendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentPackageRepository
import com.theunderseer.simplio.feature.attendance.domain.result.RenewStudentPackageResult
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.registerCompletedSyncStudent
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

class RenewStudentPackagePersistenceTest {
    @Test
    fun `renewed duo purchase note and old class history survive database reopening`() = runTest {
        withDatabasePath { path ->
            val oldId = withDatabase(path) { database ->
                val old = database.registerCompletedSyncStudent("Return change")
                val duo = AttendanceTestData.duoSpecialPackage.copy(
                    id = database.packageTypeDao().getAll()
                        .single { it.maxVisitsPerAttendance == 2 }.id,
                )
                val result = renew(database)(
                    old.student.id,
                    old.studentPackage.id,
                    duo,
                    AttendanceTestData.sixthClassDate,
                    note = "Reviewed note",
                    visitCount = 2,
                )
                assertIs<RenewStudentPackageResult.Success>(result)
                old.studentPackage.id
            }
            withDatabase(path) { database ->
                val student = database.studentDao().getAll().single()
                val pack = assertNotNull(RoomStudentPackageRepository(database).getLatestPackageForStudent(student.id))
                assertTrue(pack.id != oldId)
                assertEquals("Reviewed note", student.note)
                assertEquals(AttendanceTestData.sixthClassDate, pack.purchasedOn)
                assertEquals(AttendanceTestData.sixthClassDate, pack.startedOn)
                assertEquals(2, pack.usedClasses)
                assertEquals(6, pack.remainingClasses)
                assertNotNull(pack.expiresOn)
                assertEquals(4, database.attendanceDao().getForPackage(oldId).size)
                val history = database.attendanceDao().getClassHistoryPage(null, 100)
                assertEquals(5, history.size)
                assertEquals(6, history.sumOf { it.visitCount })
                assertTrue(database.syncDao().getPending(100).isNotEmpty())
            }
        }
    }

    @Test
    fun `prepaid renewal stays latest with empty attendance and no expiration after reopening`() = runTest {
        withDatabasePath { path ->
            val oldId = withDatabase(path) { database ->
                val old = database.registerCompletedSyncStudent("Return change")
                val result = renew(database)(
                    old.student.id,
                    old.studentPackage.id,
                    old.studentPackage.packageType,
                    AttendanceTestData.sixthClassDate,
                    note = "",
                    recordFirstAttendance = false,
                )
                val pack = assertIs<RenewStudentPackageResult.Success>(result).studentPackage
                assertNull(pack.startedOn)
                old.studentPackage.id
            }
            withDatabase(path) { database ->
                val student = database.studentDao().getAll().single()
                assertEquals("", student.note)
                val pack = assertNotNull(RoomStudentPackageRepository(database).getLatestPackageForStudent(student.id))
                assertTrue(pack.id != oldId)
                assertEquals(AttendanceTestData.sixthClassDate, pack.purchasedOn)
                assertEquals(0, pack.usedClasses)
                assertEquals(4, pack.remainingClasses)
                assertNull(pack.startedOn)
                assertNull(pack.expiresOn)
                assertEquals(4, database.attendanceDao().getClassHistoryPage(null, 100).size)
            }
        }
    }

    @Test
    fun `failed renewal rolls back note purchase attendance and sync queue together`() = runTest {
        withDatabasePath { path ->
            withDatabase(path) { database ->
                val old = database.registerCompletedSyncStudent("Return change")
                val before = database.syncDao().getPending(100)
                val real = RoomStudentPackageRepository(database)
                val failing = object : StudentPackageRepository by real {
                    override suspend fun create(
                        studentId: Int,
                        packageType: PackageType,
                        purchasedOn: LocalDate,
                        initialAttendance: InitialAttendance?,
                    ): StudentPackage {
                        real.create(studentId, packageType, purchasedOn, initialAttendance)
                        error("Injected failure after creating purchase")
                    }
                }
                assertFailsWith<IllegalStateException> {
                    renew(database, failing)(
                        old.student.id,
                        old.studentPackage.id,
                        old.studentPackage.packageType,
                        AttendanceTestData.sixthClassDate,
                        note = "Changed",
                    )
                }
                assertEquals(before, database.syncDao().getPending(100))
                assertEquals("Return change", database.studentDao().getById(old.student.id)?.note)
                assertEquals(old.studentPackage, real.getLatestPackageForStudent(old.student.id))
            }
            withDatabase(path) { database ->
                val student = database.studentDao().getAll().single()
                assertEquals("Return change", student.note)
                assertEquals(
                    4,
                    assertNotNull(
                        RoomStudentPackageRepository(database)
                            .getLatestPackageForStudent(student.id),
                    ).usedClasses,
                )
            }
        }
    }

    private fun renew(
        database: AttendanceDatabase,
        packages: StudentPackageRepository = RoomStudentPackageRepository(database),
    ) = RenewStudentPackageUseCase(packages, RoomStudentRepository(database), RoomTransactionRunner(database))

    private suspend fun withDatabasePath(block: suspend (String) -> Unit) {
        val directory = Files.createTempDirectory("simplio-renewal-")
        try {
            block(directory.resolve("attendance.db").toString())
        } finally {
            directory.toFile().deleteRecursively()
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
