package com.theunderseer.simplio.feature.attendance.data.remote

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomUploadStore
import com.theunderseer.simplio.feature.attendance.data.local.transaction.RoomTransactionRunner
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceSyncStatus
import com.theunderseer.simplio.feature.attendance.data.sync.UploadProblem
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.result.RenewStudentPackageResult
import com.theunderseer.simplio.feature.attendance.domain.usecase.RenewStudentPackageUseCase
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.registerCompletedSyncStudent
import com.theunderseer.simplio.feature.attendance.fixtures.seedSyncDefaults
import com.theunderseer.simplio.feature.attendance.fixtures.testFirestoreSyncCoordinator
import com.theunderseer.simplio.feature.attendance.fixtures.withFirestoreEmulator
import com.theunderseer.simplio.feature.attendance.fixtures.withSyncDatabases
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FirestorePackageRenewalEmulatorTest {
    @Test
    fun `renewal with two first visits syncs to another database without losing old history`() = runTest {
        verifyRoundTrip(recordFirstAttendance = true)
    }

    @Test
    fun `renewal awaiting activation syncs as latest purchase with no expiration`() = runTest {
        verifyRoundTrip(recordFirstAttendance = false)
    }

    @Test
    fun `two device renewals conflict instead of overwriting either purchase`() = runTest {
        withFirestoreEmulator { client, target ->
            withSyncDatabases { first, second ->
                val original = first.registerCompletedSyncStudent()
                val firstSync = testFirestoreSyncCoordinator(first, client, target)
                repeat(4) { firstSync.syncOnce() }
                second.seedSyncDefaults()
                val secondSync = testFirestoreSyncCoordinator(second, client, target)
                assertEquals(AttendanceSyncStatus.Idle, secondSync.syncOnce())
                val secondStudent = second.studentDao().getAll().single()
                val secondPackage = assertNotNull(
                    RoomStudentPackageRepository(second)
                        .getLatestPackageForStudent(secondStudent.id),
                )
                renew(first, original, true, "First device")
                renew(
                    second,
                    RegisteredStudent(original.student.copy(id = secondStudent.id), secondPackage),
                    false,
                    "Second device",
                )
                assertEquals(AttendanceSyncStatus.Syncing, firstSync.syncOnce())
                assertEquals(AttendanceSyncStatus.Paused(UploadProblem.CONFLICT), secondSync.syncOnce())
                assertEquals("Second device", second.studentDao().getById(secondStudent.id)?.note)
                val latest = assertNotNull(
                    RoomStudentPackageRepository(second)
                        .getLatestPackageForStudent(secondStudent.id),
                )
                assertEquals(0, latest.usedClasses)
                assertEquals(4, second.attendanceDao().getClassHistoryPage(null, 100).size)
            }
        }
    }

    private suspend fun verifyRoundTrip(recordFirstAttendance: Boolean) {
        withFirestoreEmulator { client, target ->
            withSyncDatabases { first, second ->
                val original = first.registerCompletedSyncStudent("Return change")
                val firstSync = testFirestoreSyncCoordinator(first, client, target)
                repeat(4) { firstSync.syncOnce() }
                second.seedSyncDefaults()
                val secondSync = testFirestoreSyncCoordinator(second, client, target)
                assertEquals(AttendanceSyncStatus.Idle, secondSync.syncOnce())
                renew(first, original, recordFirstAttendance, "Reviewed note")
                assertEquals(AttendanceSyncStatus.Syncing, firstSync.syncOnce())
                assertEquals(AttendanceSyncStatus.Idle, secondSync.syncOnce())
                val student = second.studentDao().getAll().single()
                assertEquals(original.student.fullName, student.fullName)
                assertEquals(original.student.groupId, student.groupId)
                assertEquals("Reviewed note", student.note)
                val latest = assertNotNull(RoomStudentPackageRepository(second).getLatestPackageForStudent(student.id))
                assertEquals(AttendanceTestData.sixthClassDate, latest.purchasedOn)
                assertEquals(8, latest.packageType.totalClasses)
                assertEquals(if (recordFirstAttendance) 2 else 0, latest.usedClasses)
                if (recordFirstAttendance) {
                    assertEquals(AttendanceTestData.sixthClassDate, latest.startedOn)
                    assertNotNull(latest.expiresOn)
                } else {
                    assertNull(latest.startedOn)
                    assertNull(latest.expiresOn)
                }
                val history = second.attendanceDao().getClassHistoryPage(null, 100)
                assertEquals(if (recordFirstAttendance) 5 else 4, history.size)
                assertEquals(if (recordFirstAttendance) 6 else 4, history.sumOf { it.visitCount })
                assertNull(RoomUploadStore(second).prepare(target))
                assertEquals(4, first.attendanceDao().getForPackage(original.studentPackage.id).size)
            }
        }
    }

    private suspend fun renew(
        database: AttendanceDatabase,
        original: RegisteredStudent,
        recordFirstAttendance: Boolean,
        note: String,
    ) {
        val type = AttendanceTestData.duoSpecialPackage.copy(
            id = database.packageTypeDao().getAll()
                .single { it.maxVisitsPerAttendance == 2 }.id,
        )
        assertIs<RenewStudentPackageResult.Success>(
            RenewStudentPackageUseCase(
                RoomStudentPackageRepository(database),
                RoomStudentRepository(database),
                RoomTransactionRunner(database),
            )(
                studentId = original.student.id,
                previousPackageId = original.studentPackage.id,
                packageType = type,
                renewedOn = AttendanceTestData.sixthClassDate,
                note = note,
                visitCount = 2,
                recordFirstAttendance = recordFirstAttendance,
            ),
        )
    }
}
