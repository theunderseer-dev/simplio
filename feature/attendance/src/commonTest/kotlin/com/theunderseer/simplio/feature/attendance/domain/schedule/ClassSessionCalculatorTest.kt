package com.theunderseer.simplio.feature.attendance.domain.schedule

import com.theunderseer.simplio.feature.attendance.domain.model.WeeklyClassSlot
import com.theunderseer.simplio.feature.attendance.fixtures.testClassSchedule
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class ClassSessionCalculatorTest {
    @Test
    fun `legacy series contains one shared occurrence for each Monday and Friday`() {
        val sessions = DefaultClassSchedule.schedule.sessionsBetween(LocalDate(2026, 10, 1), LocalDate(2026, 10, 12))
        assertEquals(listOf(2, 5, 9, 12), sessions.map { it.date.day })
        assertEquals(sessions.size, sessions.map { it.id }.distinct().size)
        assertTrue(sessions.all { it.endsAt - it.startsAt == 1.25.hours })
    }

    @Test
    fun `start and end dates are inclusive and never extend a package or series`() {
        val schedule = testClassSchedule(endsOn = LocalDate(2026, 10, 9))
        val sessions = schedule.sessionsBetween(LocalDate(2026, 9, 1), LocalDate(2026, 11, 1))
        assertEquals(listOf(LocalDate(2026, 10, 2), LocalDate(2026, 10, 9)), sessions.map { it.date })
        assertEquals(emptyList(), schedule.sessionsBetween(LocalDate(2026, 10, 10), LocalDate(2026, 10, 20)))
    }

    @Test
    fun `same series and slot have stable identity but independent equal schedules do not merge`() {
        val schedule = testClassSchedule()
        val date = LocalDate(2026, 10, 2)
        val session = schedule.sessionsBetween(date, date).single()
        assertEquals(session.id, schedule.copy(name = "Renamed").sessionsBetween(date, date).single().id)
        assertNotEquals(session.id, schedule.copy(id = "separate-series").sessionsBetween(date, date).single().id)
        val shifted = schedule.copy(slots = listOf(schedule.slots.single().copy(startsAt = LocalTime(9, 30))))
        assertEquals(session.id, shifted.sessionsBetween(date, date).single().id)
    }

    @Test
    fun `two slots on a day remain separate sessions`() {
        val schedule = testClassSchedule(
            slots = listOf(
                WeeklyClassSlot("morning", DayOfWeek.FRIDAY, LocalTime(9, 0), LocalTime(10, 0)),
                WeeklyClassSlot("evening", DayOfWeek.FRIDAY, LocalTime(18, 0), LocalTime(19, 0)),
            ),
        )
        val date = LocalDate(2026, 10, 2)
        val sessions = schedule.sessionsBetween(date, date)
        assertEquals(2, sessions.size)
        assertNotEquals(sessions[0].id, sessions[1].id)
    }

    @Test
    fun `local start time survives daylight saving change without relying on device zone`() {
        val schedule = testClassSchedule(
            timeZoneId = "America/New_York",
            startsOn = null,
            slots = listOf(WeeklyClassSlot("sunday", DayOfWeek.SUNDAY, LocalTime(9, 0), LocalTime(10, 0))),
        )
        val sessions = schedule.sessionsBetween(LocalDate(2026, 3, 1), LocalDate(2026, 3, 8))
        assertEquals(listOf(14, 13), sessions.map { it.startsAt.toLocalDateTime(TimeZone.UTC).hour })
        assertTrue(sessions.all { it.startsAt.toLocalDateTime(TimeZone.of(schedule.timeZoneId)).hour == 9 })
    }

    @Test
    fun `nonexistent daylight saving time is skipped rather than silently moved`() {
        val schedule = testClassSchedule(
            timeZoneId = "America/New_York",
            startsOn = null,
            slots = listOf(WeeklyClassSlot("sunday", DayOfWeek.SUNDAY, LocalTime(2, 30), LocalTime(3, 30))),
        )
        assertEquals(emptyList(), schedule.sessionsBetween(LocalDate(2026, 3, 8), LocalDate(2026, 3, 8)))
    }

    @Test
    fun `invalid rules and unbounded projection requests are rejected`() {
        val slot = testClassSchedule().slots.single()
        assertFailsWith<IllegalArgumentException> { slot.copy(endsAt = slot.startsAt) }
        assertFailsWith<IllegalArgumentException> { slot.copy(startsAt = LocalTime(9, 0, 1)) }
        assertFailsWith<IllegalArgumentException> { testClassSchedule(slots = listOf(slot, slot)) }
        assertFailsWith<IllegalArgumentException> { testClassSchedule(id = "invalid:id") }
        assertFailsWith<IllegalArgumentException> { testClassSchedule(name = " ") }
        assertFailsWith<IllegalArgumentException> { testClassSchedule(timeZoneId = "Invalid/Zone") }
        assertFailsWith<IllegalArgumentException> { testClassSchedule(endsOn = LocalDate(2026, 1, 1)) }
        assertFailsWith<IllegalArgumentException> {
            testClassSchedule().sessionsBetween(LocalDate(2026, 1, 1), LocalDate(2046, 1, 1))
        }
    }
}
