package com.theunderseer.simplio.feature.attendance.data.local.repository

import com.theunderseer.simplio.feature.attendance.data.local.entity.AttendanceEntity
import com.theunderseer.simplio.feature.attendance.domain.model.ClassAttendanceFailure
import com.theunderseer.simplio.feature.attendance.domain.model.ClassAttendanceProblem
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.ClassAttendanceTestClock
import com.theunderseer.simplio.feature.attendance.fixtures.ClassAttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.classStudentActions
import com.theunderseer.simplio.feature.attendance.fixtures.seedClassAttendanceStudents
import com.theunderseer.simplio.feature.attendance.fixtures.withSyncDatabases
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClassStudentActionsTest {
    @Test
    fun `registration saves a duo first visit on the class date without finalizing the class`() = runTest {
        withSyncDatabases { db, _ ->
            val students = db.seedClassAttendanceStudents()
            val repo = RoomClassAttendanceRepository(db, ClassAttendanceTestClock())
            val before = repo.load(ClassAttendanceTestData.session.id)
            val updated = db.classStudentActions().register(
                before,
                ClassAttendanceTestData.companionName,
                students.last().student.groupId,
                students.last().studentPackage.packageType,
                2,
                true,
                "Change next time",
            )
            val added = updated.rows.single { it.student.id !in students.map { it.student.id } }
            assertEquals(ClassAttendanceTestData.date, added.studentPackage?.purchasedOn)
            assertEquals(ClassAttendanceTestData.date, added.studentPackage?.startedOn)
            assertEquals(2, added.recordedVisitCount)
            assertEquals("Change next time", added.student.note)
            assertNull(db.classSessionStateDao().get(before.session.id))
            repo.save(updated, mapOf(added.student.id to 2), false)
            assertEquals(2, RoomStudentPackageRepository(db).getLatestPackageForStudent(added.student.id)?.usedClasses)
        }
    }

    @Test
    fun `registration without first attendance remains saved when the roster is discarded`() = runTest {
        withSyncDatabases { db, _ ->
            val student = db.seedClassAttendanceStudents().first()
            val repo = RoomClassAttendanceRepository(db, ClassAttendanceTestClock())
            val updated = db.classStudentActions().register(
                repo.load(ClassAttendanceTestData.session.id),
                ClassAttendanceTestData.companionName,
                student.student.groupId,
                student.studentPackage.packageType,
                1,
                false,
                "",
            )
            val added = updated.rows.last { it.student.id != student.student.id }
            assertEquals(3, db.studentDao().getAll().size)
            assertEquals(0, added.recordedVisitCount)
            assertNull(added.studentPackage?.startedOn)
            assertNull(db.classSessionStateDao().get(updated.session.id))
        }
    }

    @Test
    fun `unrelated group is rejected without student package or outbox changes`() = runTest {
        withSyncDatabases { db, _ ->
            val student = db.seedClassAttendanceStudents().first()
            val sheet = RoomClassAttendanceRepository(
                db,
                ClassAttendanceTestClock(),
            ).load(ClassAttendanceTestData.session.id)
            val pending = db.syncDao().getPending(100)
            assertEquals(
                ClassAttendanceProblem.INVALID_SELECTION,
                assertFailsWith<ClassAttendanceFailure> {
                    db.classStudentActions().register(
                        sheet,
                        ClassAttendanceTestData.companionName,
                        Int.MAX_VALUE,
                        student.studentPackage.packageType,
                        1,
                        true,
                        "",
                    )
                }.problem,
            )
            assertEquals(2, db.studentDao().getAll().size)
            assertEquals(pending, db.syncDao().getPending(100))
        }
    }

    @Test
    fun `renewal preserves old purchase and clears note while recording one new visit`() = runTest {
        withSyncDatabases { db, _ ->
            val student = db.seedClassAttendanceStudents(standardCapacity = 1).first()
            RoomStudentRepository(db).updateNote(student.student.id, "Old note")
            RoomStudentPackageRepository(
                db,
            ).recordAttendance(student.studentPackage.id, AttendanceTestData.firstClassDate, 1)
            val repo = RoomClassAttendanceRepository(db, ClassAttendanceTestClock())
            val sheet = repo.load(ClassAttendanceTestData.session.id)
            assertTrue(sheet.rows.single { it.student.id == student.student.id }.canRenew)
            val updated = db.classStudentActions().renew(
                sheet,
                student.student.id,
                student.studentPackage.packageType,
                1,
                true,
                "",
            )
            val row = updated.rows.single { it.student.id == student.student.id }
            assertEquals("", row.student.note)
            assertEquals(1, row.recordedVisitCount)
            assertFalse(row.canRenew)
            assertEquals(2, db.syncPullDao().packages().count { it.studentId == student.student.id })
            assertEquals(1, db.attendanceDao().getForPackage(student.studentPackage.id).single().visitCount)
            repo.save(
                updated,
                updated.rows.filter { it.recordedVisitCount > 0 }.associate {
                    it.student.id to
                        it.recordedVisitCount
                },
                false,
            )
            assertEquals(
                1,
                RoomStudentPackageRepository(db).getLatestPackageForStudent(student.student.id)?.usedClasses,
            )
        }
    }

    @Test
    fun `active package cannot be renewed and final visit retains attendance control`() = runTest {
        withSyncDatabases { db, _ ->
            val student = db.seedClassAttendanceStudents(standardCapacity = 1).first()
            val repo = RoomClassAttendanceRepository(db, ClassAttendanceTestClock())
            var sheet = repo.load(ClassAttendanceTestData.session.id)
            assertFailsWith<ClassAttendanceFailure> {
                db.classStudentActions().renew(
                    sheet,
                    student.student.id,
                    student.studentPackage.packageType,
                    1,
                    true,
                    "",
                )
            }
            db.attendanceDao().save(AttendanceEntity(student.studentPackage.id, sheet.session.date.toEpochDays(), 1))
            sheet = repo.load(sheet.session.id)
            val row = sheet.rows.single { it.student.id == student.student.id }
            assertTrue(row.canRecord)
            assertFalse(row.canRenew)
        }
    }

    @Test
    fun `external attendance and expired class window reject registration atomically`() = runTest {
        withSyncDatabases { db, _ ->
            val student = db.seedClassAttendanceStudents().first()
            val clock = ClassAttendanceTestClock()
            val repo = RoomClassAttendanceRepository(db, clock)
            val sheet = repo.load(ClassAttendanceTestData.session.id)
            RoomStudentPackageRepository(db).recordAttendance(student.studentPackage.id, sheet.session.date, 1)
            val pending = db.syncDao().getPending(100)
            assertEquals(
                ClassAttendanceProblem.STALE,
                assertFailsWith<ClassAttendanceFailure> {
                    db.classStudentActions(clock).register(
                        sheet,
                        ClassAttendanceTestData.companionName,
                        student.student.groupId,
                        student.studentPackage.packageType,
                        1,
                        true,
                        "",
                    )
                }.problem,
            )
            clock.instant = ClassAttendanceTestData.nextSession.startsAt
            assertFailsWith<ClassAttendanceFailure> {
                db.classStudentActions(clock).register(
                    sheet,
                    ClassAttendanceTestData.companionName,
                    student.student.groupId,
                    student.studentPackage.packageType,
                    1,
                    false,
                    "",
                )
            }
            assertEquals(2, db.studentDao().getAll().size)
            assertEquals(pending, db.syncDao().getPending(100))
        }
    }
}
