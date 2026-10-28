package com.theunderseer.simplio.feature.attendance.data.local.repository

import com.theunderseer.simplio.feature.attendance.data.local.seed.StudentGroupSeeder
import com.theunderseer.simplio.feature.attendance.domain.model.ClassSessionStatus
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupRequest
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupResult
import com.theunderseer.simplio.feature.attendance.domain.repository.DeleteStudentGroupResult
import com.theunderseer.simplio.feature.attendance.domain.schedule.sessionsBetween
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.ClassAttendanceTestClock
import com.theunderseer.simplio.feature.attendance.fixtures.ClassAttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.GroupDeletionTestData
import com.theunderseer.simplio.feature.attendance.fixtures.registerGroupStudent
import com.theunderseer.simplio.feature.attendance.fixtures.seedDeletableGroup
import com.theunderseer.simplio.feature.attendance.fixtures.seedSyncDefaults
import com.theunderseer.simplio.feature.attendance.fixtures.withSyncDatabases
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class StudentGroupDeletionTest {
    @Test
    fun `a class already starting at the retirement instant remains in history`() = runTest {
        withSyncDatabases { db, _ ->
            val group = db.seedDeletableGroup()
            val original = GroupDeletionTestData.schedule
            val session = original.sessionsBetween(
                AttendanceTestData.firstClassDate,
                AttendanceTestData.firstClassDate,
            ).single()
            val clock = ClassAttendanceTestClock(session.startsAt)
            val classes = RoomClassAttendanceRepository(db, clock)
            classes.save(classes.load(session.id), emptyMap(), false)
            RoomStudentGroupDeletionRepository(db, clock).delete(group.id)
            val archived = RoomClassScheduleRepository(db).getAll().single { it.id == original.id }
            assertEquals(listOf(session), archived.sessionsBetween(session.date, session.date))
            val history =
                checkNotNull(
                    RoomClassHistoryRepository(
                        db.attendanceDao(),
                        db,
                    ).getScheduledPage(session.date, null, 20, clock.now()),
                )
            assertTrue(history.entries.any { it.sessionId == session.id && !it.canTrackAttendance })
        }
    }

    @Test
    fun `missing and occupied groups leave students schedules packages and pending work unchanged`() = runTest {
        withSyncDatabases { db, _ ->
            val group = db.seedDeletableGroup()
            db.registerGroupStudent(group.id)
            val before = db.syncDao().getPending(100)
            val schedules = db.classScheduleDao().schedules()
            val repo = RoomStudentGroupDeletionRepository(db, ClassAttendanceTestClock())
            assertEquals(DeleteStudentGroupResult.NOT_EMPTY, repo.delete(group.id))
            assertEquals(DeleteStudentGroupResult.NOT_FOUND, repo.delete(Int.MAX_VALUE))
            assertEquals(before, db.syncDao().getPending(100))
            assertEquals(schedules, db.classScheduleDao().schedules())
            assertEquals(1, db.studentDao().getAll().size)
            assertEquals(1, db.syncPullDao().packages().size)
        }
    }

    @Test
    fun `removing one shared group leaves the schedule active for the others`() = runTest {
        withSyncDatabases { db, _ ->
            db.seedSyncDefaults()
            val clock = ClassAttendanceTestClock()
            val repo = RoomStudentGroupDeletionRepository(db, clock)
            assertEquals(DeleteStudentGroupResult.DELETED, repo.delete(AttendanceTestData.studentGroups.first().id))
            assertNull(db.classScheduleDao().schedules().single().archivedAtEpochMillis)
            assertEquals(1, db.classScheduleDao().groupSchedules().size)
            assertTrue(RoomClassAttendanceRepository(db, clock).load(ClassAttendanceTestData.session.id).rows.isEmpty())
        }
    }

    @Test
    fun `last group retirement keeps history excludes future classes and releases its interval and name`() = runTest {
        withSyncDatabases { db, _ ->
            val group = db.seedDeletableGroup()
            val clock = ClassAttendanceTestClock()
            val original = GroupDeletionTestData.schedule
            val session = original.sessionsBetween(
                AttendanceTestData.firstClassDate,
                AttendanceTestData.firstClassDate,
            ).single()
            val tracking = RoomClassAttendanceRepository(db, clock)
            tracking.save(tracking.load(session.id), emptyMap(), false)
            clock.instant = ClassAttendanceTestData.nextSession.endsAt // Before today's private class begins.
            assertEquals(
                DeleteStudentGroupResult.DELETED,
                RoomStudentGroupDeletionRepository(db, clock).delete(group.id),
            )
            val archived = RoomClassScheduleRepository(db).getAll().single { it.id == original.id }
            assertEquals(clock.now(), archived.archivedAt)
            assertEquals(
                listOf(session),
                archived.sessionsBetween(AttendanceTestData.firstClassDate, AttendanceTestData.fifthClassDate),
            )
            val later = clock.now() + 7.days
            val history =
                checkNotNull(
                    RoomClassHistoryRepository(
                        db.attendanceDao(),
                        db,
                    ).getScheduledPage(AttendanceTestData.fifthClassDate, null, 20, later),
                )
            val old = history.entries.single { it.sessionId == session.id }
            assertEquals(ClassSessionStatus.HELD, old.status)
            assertFalse(old.canTrackAttendance)
            assertTrue(
                history.entries.none {
                    it.sessionId?.startsWith(original.id + ":") == true &&
                        it.sessionId != session.id
                },
            )
            assertIs<CreateStudentGroupResult.Success>(
                RoomStudentGroupCreationRepository(db).create(
                    CreateStudentGroupRequest(group.name, newSchedule = original.copy(id = "replacement-series")),
                ),
            )
            assertEquals(
                CreateStudentGroupResult.InvalidSchedule,
                RoomStudentGroupCreationRepository(db).create(
                    CreateStudentGroupRequest("Cannot reuse retired series", existingScheduleId = original.id),
                ),
            )
        }
    }

    @Test
    fun `deleted default groups never return when defaults are seeded again`() = runTest {
        withSyncDatabases { db, _ ->
            db.seedSyncDefaults()
            val repo = RoomStudentGroupDeletionRepository(db, ClassAttendanceTestClock())
            AttendanceTestData.studentGroups.forEach {
                assertEquals(DeleteStudentGroupResult.DELETED, repo.delete(it.id))
            }
            val pending = db.syncDao().getPending(100)
            StudentGroupSeeder(db).seedDefaults()
            assertTrue(db.studentGroupDao().getAll().isEmpty())
            assertEquals(pending, db.syncDao().getPending(100))
            assertTrue(db.classScheduleDao().schedules().all { it.archivedAtEpochMillis != null })
        }
    }
}
