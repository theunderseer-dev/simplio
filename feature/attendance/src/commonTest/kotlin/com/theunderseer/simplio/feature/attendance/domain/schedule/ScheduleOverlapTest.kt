package com.theunderseer.simplio.feature.attendance.domain.schedule

import com.theunderseer.simplio.feature.attendance.domain.model.ClassSchedule
import com.theunderseer.simplio.feature.attendance.domain.model.WeeklyClassSlot
import com.theunderseer.simplio.feature.attendance.fixtures.testClassSchedule
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ScheduleOverlapTest {
    private fun schedule(
        id: String,
        start: String = "09:00",
        end: String = "10:00",
        zone: String = "Asia/Bangkok",
        day: DayOfWeek = DayOfWeek.MONDAY,
        from: LocalDate? = LocalDate(2026, 10, 5),
        through: LocalDate? = null,
    ): ClassSchedule = testClassSchedule(
        id = id,
        timeZoneId = zone,
        startsOn = from,
        endsOn = through,
        slots = listOf(WeeklyClassSlot("slot", day, LocalTime.parse(start), LocalTime.parse(end))),
    )

    @Test
    fun `partial nested identical and enclosing intervals collide in both directions`() = runTest {
        val first = schedule("a")
        for ((start, end) in listOf("09:30" to "10:30", "09:15" to "09:45", "09:00" to "10:00", "08:00" to "11:00")) {
            val second = schedule("b", start, end)
            assertNotNull(findScheduleOverlap(first, listOf(second)))
            assertNotNull(findScheduleOverlap(second, listOf(first)))
        }
    }

    @Test
    fun `touching boundaries and different weekdays are allowed`() = runTest {
        val first = schedule("a")
        for (second in listOf(
            schedule("b", "10:00", "11:00"),
            schedule("b", "08:00", "09:00"),
            schedule("b", day = DayOfWeek.TUESDAY),
        )) {
            assertNull(findScheduleOverlap(first, listOf(second)))
        }
        assertNotNull(findScheduleOverlap(first, listOf(schedule("b", "09:59", "11:00"))))
    }

    @Test
    fun `same shared series is not a second class`() = runTest {
        val shared = schedule("shared")
        assertNull(findScheduleOverlap(shared, listOf(shared)))
        assertEquals(emptyList(), findScheduleOverlaps(listOf(shared, shared)))
    }

    @Test
    fun `date boundaries are inclusive but must contain an actual common weekday`() = runTest {
        val date = LocalDate(2026, 10, 5)
        val first = schedule("a", through = date)
        assertNotNull(findScheduleOverlap(first, listOf(schedule("b", from = date))))
        assertNull(findScheduleOverlap(first, listOf(schedule("b", from = LocalDate(2026, 10, 6)))))
        assertNull(
            findScheduleOverlap(
                schedule("a", from = LocalDate(2026, 10, 6), through = LocalDate(2026, 10, 8)),
                listOf(schedule("b")),
            ),
        )
    }

    @Test
    fun `equal clocks in different zones can be disjoint`() = runTest {
        assertNull(findScheduleOverlap(schedule("a"), listOf(schedule("b", zone = "UTC"))))
    }

    @Test
    fun `simultaneous classes collide across different weekdays and local date bounds`() = runTest {
        val first = schedule("a", "00:15", "00:45", through = LocalDate(2026, 10, 5))
        val second = schedule(
            "b",
            "17:00",
            "18:00",
            "UTC",
            DayOfWeek.SUNDAY,
            LocalDate(2026, 10, 4),
            LocalDate(2026, 10, 4),
        )
        val collision = assertNotNull(findScheduleOverlap(first, listOf(second)))
        assertEquals(LocalDate(2026, 10, 5), collision.firstSession.date)
        assertEquals(LocalDate(2026, 10, 4), collision.secondSession.date)
    }

    @Test
    fun `seasonal collision months after creation is found for bounded and unbounded schedules`() = runTest {
        val first = schedule("a", zone = "Europe/London", from = LocalDate(2026, 11, 1))
        for (end in listOf(null, LocalDate(2027, 3, 31))) {
            val second = schedule("b", "08:30", "08:45", "UTC", from = LocalDate(2026, 11, 1), through = end)
            val collision = assertNotNull(findScheduleOverlap(first, listOf(second)))
            assertEquals(LocalDate(2027, 3, 29), collision.firstSession.date)
        }
    }

    @Test
    fun `nonexistent spring class is skipped rather than shifted into another time`() = runTest {
        val day = LocalDate(2026, 3, 8)
        val first = schedule("a", "02:15", "02:45", "America/New_York", DayOfWeek.SUNDAY, day, day)
        val second = schedule("b", "06:00", "08:00", "UTC", DayOfWeek.SUNDAY, day, day)
        assertNull(findScheduleOverlap(first, listOf(second)))
        assertNotNull(
            findScheduleOverlap(
                first.copy(endsOn = day.plus(DatePeriod(days = 7))),
                listOf(second.copy(endsOn = day.plus(DatePeriod(days = 7)))),
            ),
        )
    }

    @Test
    fun `fall overlap uses the same earlier offset policy as attendance sessions`() = runTest {
        val day = LocalDate(2026, 11, 1)
        val first = schedule("a", "01:30", "02:15", "America/New_York", DayOfWeek.SUNDAY, day, day)
        val second = schedule("b", "06:30", "06:45", "UTC", DayOfWeek.SUNDAY, day, day)
        assertNotNull(findScheduleOverlap(first, listOf(second)))
    }

    @Test
    fun `unbounded past and dates before the first zone transition are checked`() = runTest {
        val first = schedule("a", zone = "UTC", from = null, through = LocalDate(1800, 1, 31))
        val second = schedule("b", zone = "UTC", from = null, through = LocalDate(1800, 1, 31))
        assertNotNull(findScheduleOverlap(first, listOf(second)))
    }

    @Test
    fun `disjoint seasonal patterns do not produce a false conflict for an indefinite schedule`() = runTest {
        assertNull(
            findScheduleOverlap(
                schedule("a", "00:00", "01:00", "Europe/London"),
                listOf(schedule("b", "00:00", "01:00", "America/New_York")),
            ),
        )
    }

    @Test
    fun `transition probes agree with exhaustive occurrences over bounded calendars`() = runTest {
        val random = Random(2468)
        val zones = listOf("UTC", "Asia/Bangkok", "Europe/London", "America/New_York", "Australia/Sydney")
        repeat(60) { index ->
            val from = LocalDate(2026, if (index % 2 == 0) 3 else 10, 1)
            val through = from.plus(DatePeriod(days = 45))
            fun sample(id: String): ClassSchedule {
                val hour = random.nextInt(0, 22)
                return schedule(
                    id,
                    hourText(hour),
                    hourText(hour + 2),
                    zones.random(random),
                    DayOfWeek.entries.random(random),
                    from,
                    through,
                )
            }
            val a = sample("a")
            val b = sample("b")
            val expected = a.sessionsBetween(from, through).any { x ->
                b.sessionsBetween(from, through).any { y -> x.startsAt < y.endsAt && y.startsAt < x.endsAt }
            }
            assertEquals(expected, findScheduleOverlap(a, listOf(b)) != null, "case $index: $a / $b")
        }
    }

    private fun hourText(hour: Int): String = hour.toString().padStart(2, '0') + ":00"
}
