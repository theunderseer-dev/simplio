package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.data.local.transaction.RoomTransactionRunner
import com.theunderseer.simplio.feature.attendance.domain.model.InitialAttendance
import com.theunderseer.simplio.feature.attendance.domain.result.MarkStudentPresentResult
import com.theunderseer.simplio.feature.attendance.domain.result.RenewStudentPackageResult
import com.theunderseer.simplio.feature.attendance.domain.schedule.latestScheduledClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.registerCompletedSyncStudent
import com.theunderseer.simplio.feature.attendance.fixtures.withSyncDatabases
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RenewalAttendanceBoundaryTest {
    private val plans = listOf(
        AttendanceTestData.fourClassPackage,
        AttendanceTestData.fiveClassPackage,
        AttendanceTestData.duoSpecialPackage,
    )

    @Test
    fun `renewal cannot record first attendance on a date already charged to the previous package`() = runTest {
        for (plan in plans) {
            withSyncDatabases { db, _ ->
                val old = db.registerCompletedSyncStudent("Keep note", plan)
                val last = old.studentPackage.attendanceDates.maxOrNull()!!
                val packages = RoomStudentPackageRepository(db)
                val students = RoomStudentRepository(db)
                val queue = db.syncDao().getPending(100)
                val result = RenewStudentPackageUseCase(packages, students, RoomTransactionRunner(db))(
                    old.student.id,
                    old.studentPackage.id,
                    old.studentPackage.packageType,
                    last,
                    "Must not save",
                    visitCount = plan.maxVisitsPerAttendance,
                )
                assertEquals(RenewStudentPackageResult.AttendanceAlreadyRecorded, result)
                assertEquals(old.studentPackage, packages.getLatestPackageForStudent(old.student.id))
                assertEquals("Keep note", db.studentDao().getById(old.student.id)?.note)
                assertEquals(queue, db.syncDao().getPending(100))
            }
        }
    }

    @Test
    fun `same day purchase stays unactivated and ordinary check cannot bypass the boundary`() = runTest {
        for (plan in plans) {
            withSyncDatabases { db, _ ->
                val old = db.registerCompletedSyncStudent(packageType = plan)
                val last = old.studentPackage.attendanceDates.maxOrNull()!!
                val packages = RoomStudentPackageRepository(db)
                val students = RoomStudentRepository(db)
                val runner = RoomTransactionRunner(db)
                val renewed = assertIs<RenewStudentPackageResult.Success>(
                    RenewStudentPackageUseCase(packages, students, runner)(
                        old.student.id,
                        old.studentPackage.id,
                        old.studentPackage.packageType,
                        last,
                        "",
                        recordFirstAttendance = false,
                    ),
                ).studentPackage
                assertEquals(last, renewed.purchasedOn)
                assertEquals(0, renewed.usedClasses)
                assertNull(renewed.startedOn)
                assertNull(renewed.expiresOn)
                val loaded = GetAttendanceUseCase(students, packages)().entries.single()
                assertTrue(last in loaded.recordedAttendanceDates)
                val queue = db.syncDao().getPending(100)
                val mark = MarkStudentPresentUseCase(packages, runner)
                assertEquals(
                    MarkStudentPresentResult.AttendanceAlreadyRecorded,
                    mark(old.student.id, last, plan.maxVisitsPerAttendance),
                )
                assertEquals(queue, db.syncDao().getPending(100))
                val next = latestScheduledClassDate(last.plus(DatePeriod(days = 4)))
                val marked = assertIs<MarkStudentPresentResult.Success>(
                    mark(old.student.id, next, plan.maxVisitsPerAttendance),
                ).studentPackage
                assertEquals(next, marked.startedOn)
                assertEquals(plan.maxVisitsPerAttendance, marked.usedClasses)
                assertIs<MarkStudentPresentResult.Success>(mark(old.student.id, next, plan.maxVisitsPerAttendance))
                assertEquals(1, db.attendanceDao().getForPackage(renewed.id).size)
                assertEquals(
                    old.studentPackage.attendanceDates + next,
                    packages.getAttendanceDatesForStudent(old.student.id),
                )
            }
        }
    }

    @Test
    fun `direct repository writes cannot create cross package duplicate or queue partial changes`() = runTest {
        withSyncDatabases { db, _ ->
            val old = db.registerCompletedSyncStudent(packageType = AttendanceTestData.duoSpecialPackage)
            val last = old.studentPackage.attendanceDates.maxOrNull()!!
            val repository = RoomStudentPackageRepository(db)
            val queue = db.syncDao().getPending(100)
            assertFailsWith<IllegalArgumentException> {
                repository.create(old.student.id, old.studentPackage.packageType, last, InitialAttendance(last, 2))
            }
            assertEquals(1, db.studentPackageDao().getForStudent(old.student.id).size)
            assertEquals(queue, db.syncDao().getPending(100))
            val new = repository.create(old.student.id, old.studentPackage.packageType, last, null)
            val afterPurchase = db.syncDao().getPending(100)
            assertFailsWith<IllegalArgumentException> { repository.recordAttendance(new.id, last, 2) }
            assertEquals(emptyList(), db.attendanceDao().getForPackage(new.id))
            assertNull(repository.getLatestPackageForStudent(old.student.id)?.startedOn)
            assertEquals(afterPurchase, db.syncDao().getPending(100))
        }
    }
}
