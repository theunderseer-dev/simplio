package com.theunderseer.simplio.feature.attendance.data.local.repository

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.domain.model.WeeklyClassSlot
import com.theunderseer.simplio.feature.attendance.domain.repository.DeleteStudentGroupResult
import com.theunderseer.simplio.feature.attendance.domain.schedule.DefaultClassSchedule
import com.theunderseer.simplio.feature.attendance.fixtures.ClassAttendanceTestClock
import com.theunderseer.simplio.feature.attendance.fixtures.ClassAttendanceTestData.nextSession
import com.theunderseer.simplio.feature.attendance.fixtures.ClassAttendanceTestData.previousSession
import com.theunderseer.simplio.feature.attendance.fixtures.ClassAttendanceTestData.session
import com.theunderseer.simplio.feature.attendance.fixtures.seedClassAttendanceStudents
import com.theunderseer.simplio.feature.attendance.fixtures.seedSyncDefaults
import com.theunderseer.simplio.feature.attendance.fixtures.testSyncDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RoomClassAttendanceReminderSourceTest {
    @Test
    fun `returns each class once at its end even when several groups share it`() = runTest {
        withDatabase { database ->
            database.seedClassAttendanceStudents()
            val source = RoomClassAttendanceReminderSource(database)

            val catchUp = source.getCandidates(previousSession.endsAt - 8.days, session.endsAt)
            assertEquals(listOf(previousSession, session), catchUp.map { it.session })
            assertEquals(DefaultClassSchedule.schedule.name, catchUp.last().scheduleName)
            assertEquals(emptyList(), source.getCandidates(previousSession.endsAt, session.endsAt - 1.seconds))
            assertEquals(
                listOf(nextSession),
                source.getCandidates(session.endsAt, nextSession.endsAt).map { it.session },
            )
        }
    }

    @Test
    fun `ignores cancelled held and legacy attendance classes`() = runTest {
        withDatabase { database ->
            val students = database.seedClassAttendanceStudents()
            val tracking = RoomClassAttendanceRepository(database, ClassAttendanceTestClock(nextSession.endsAt))
            val source = RoomClassAttendanceReminderSource(database)

            val packageId = students.first().studentPackage.id
            RoomStudentPackageRepository(database).recordAttendance(packageId, session.date, visitCount = 1)
            assertNull(database.attendanceDao().getForPackage(packageId).single().sessionId)
            tracking.save(tracking.load(previousSession.id), emptyMap(), cancel = true)
            tracking.save(tracking.load(nextSession.id), emptyMap(), cancel = false)

            assertEquals(emptyList(), source.getCandidates(previousSession.endsAt - 1.seconds, nextSession.endsAt))
        }
    }

    @Test
    fun `attendance for another schedule on the same date does not suppress the reminder`() = runTest {
        withDatabase { database ->
            val student = database.seedClassAttendanceStudents().last()
            val schedules = RoomClassScheduleRepository(database)
            val other = DefaultClassSchedule.schedule.copy(id = "other-reminder-schedule")
            schedules.save(other)
            schedules.assignToGroup(student.student.groupId, setOf(other.id))
            val packageId = student.studentPackage.id

            RoomStudentPackageRepository(database).recordAttendance(packageId, session.date, visitCount = 1)
            val recorded = database.attendanceDao().getForPackage(packageId).single()
            assertEquals(other.id, recorded.sessionId?.substringBefore(':'))

            val candidates = RoomClassAttendanceReminderSource(database).getCandidates(
                session.endsAt - 1.seconds,
                session.endsAt,
            )
            assertEquals(listOf(session.id), candidates.map { it.session.id })
        }
    }

    @Test
    fun `uses venue time and excludes empty and unassigned schedules`() = runTest {
        withDatabase { database ->
            database.seedSyncDefaults()
            val source = RoomClassAttendanceReminderSource(database)
            assertEquals(emptyList(), source.getCandidates(previousSession.endsAt, nextSession.endsAt))

            val student = database.seedClassAttendanceStudents().first()
            val early = DefaultClassSchedule.schedule.copy(
                id = "early-reminder-schedule",
                slots = listOf(
                    WeeklyClassSlot(session.slotId, session.date.dayOfWeek, LocalTime(1, 0), LocalTime(2, 0)),
                ),
            )
            val schedules = RoomClassScheduleRepository(database)
            schedules.save(early)
            val endsAt = Instant.parse("2026-10-04T19:00:00Z") // Monday 02:00 at the venue, Sunday in UTC.
            assertEquals(emptyList(), source.getCandidates(endsAt - 1.seconds, endsAt))
            schedules.assignToGroup(student.student.groupId, setOf(early.id))

            val candidate = source.getCandidates(endsAt - 1.seconds, endsAt).single()
            assertEquals(session.date, candidate.session.date)
            assertEquals(endsAt, candidate.session.endsAt)
        }
    }

    @Test
    fun `a retired schedule stops producing both catch-up and upcoming candidates`() = runTest {
        withDatabase { database ->
            val student = database.seedClassAttendanceStudents().last()
            val schedules = RoomClassScheduleRepository(database)
            val schedule = DefaultClassSchedule.schedule.copy(id = "retired-reminder-schedule")
            schedules.save(schedule)
            schedules.assignToGroup(student.student.groupId, setOf(schedule.id))
            val source = RoomClassAttendanceReminderSource(database)
            val from = previousSession.endsAt - 1.seconds
            val until = nextSession.endsAt
            assertTrue(source.getCandidates(from, until).any { it.session.scheduleId == schedule.id })

            RoomStudentRepository(database).delete(student.student.id)
            val deletion = RoomStudentGroupDeletionRepository(database, ClassAttendanceTestClock(session.endsAt))
            assertEquals(DeleteStudentGroupResult.DELETED, deletion.delete(student.student.groupId))

            assertFalse(source.getCandidates(from, until).any { it.session.scheduleId == schedule.id })
        }
    }

    @Test
    fun `does not project an unbounded history for reminders`() = runTest {
        withDatabase { database ->
            val source = RoomClassAttendanceReminderSource(database)
            assertFailsWith<IllegalArgumentException> {
                source.getCandidates(session.endsAt - 32.days, session.endsAt)
            }
        }
    }

    private suspend fun withDatabase(block: suspend (AttendanceDatabase) -> Unit) {
        val database = testSyncDatabase()
        try {
            block(database)
        } finally {
            database.close()
        }
    }
}
