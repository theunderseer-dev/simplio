package com.theunderseer.simplio.feature.attendance.domain.schedule

import com.theunderseer.simplio.feature.attendance.domain.model.WeeklyClassSlot
import com.theunderseer.simplio.feature.attendance.fixtures.ClassAttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testClassSchedule
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class LatestStartedClassTest {
    @Test
    fun `another schedule starting earlier does not expire this schedule tracking window`() {
        val shared = DefaultClassSchedule.schedule
        val evening = testClassSchedule(
            slots = listOf(
                WeeklyClassSlot("monday-evening", DayOfWeek.MONDAY, LocalTime(18, 0), LocalTime(19, 0)),
            ),
        )
        val now = ClassAttendanceTestData.session.endsAt
        assertEquals(ClassAttendanceTestData.session, shared.latestStartedClass(now))
        assertEquals(null, evening.latestStartedClass(now))
        val eveningStart = evening.sessionsBetween(
            ClassAttendanceTestData.date,
            ClassAttendanceTestData.date,
        ).single().startsAt
        assertEquals(ClassAttendanceTestData.session, shared.latestStartedClass(eveningStart))
        assertEquals(eveningStart, evening.latestStartedClass(eveningStart)?.startsAt)
    }

    @Test
    fun `multiple slots on the same day move the window at the next start`() {
        val schedule = testClassSchedule(
            slots = listOf(
                WeeklyClassSlot("morning", DayOfWeek.MONDAY, LocalTime(9, 0), LocalTime(10, 0)),
                WeeklyClassSlot("evening", DayOfWeek.MONDAY, LocalTime(18, 0), LocalTime(19, 0)),
            ),
        )
        val sessions = schedule.sessionsBetween(ClassAttendanceTestData.date, ClassAttendanceTestData.date)
        assertEquals(sessions[0], schedule.latestStartedClass(sessions[1].startsAt - 1.seconds))
        assertEquals(sessions[1], schedule.latestStartedClass(sessions[1].startsAt))
        assertNotEquals(sessions[0].id, sessions[1].id)
    }

    @Test
    fun `schedule venue time determines whether a class has begun across a UTC date boundary`() {
        val beforeBangkokMorning = Instant.parse("2026-10-08T23:00:00Z")
        assertEquals(
            ClassAttendanceTestData.session,
            DefaultClassSchedule.schedule.latestStartedClass(beforeBangkokMorning),
        )
        assertEquals(
            ClassAttendanceTestData.nextSession,
            DefaultClassSchedule.schedule.latestStartedClass(ClassAttendanceTestData.nextSession.startsAt),
        )
    }
}
