package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.model.ClassHistoryStudent
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testClassHistoryEntry
import com.theunderseer.simplio.feature.attendance.fixtures.testStudent
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class ClassHistoryDayUiStateTest {
    private val date = AttendanceTestData.secondClassDate
    private val morning = Instant.parse("2026-10-05T02:00:00Z")
    private val morningEnd = Instant.parse("2026-10-05T03:15:00Z")

    @Test
    fun `independent series at same interval form one block without changing source identities`() {
        val anna = ClassHistoryStudent(testStudent(groupId = 1), 2)
        val bob = ClassHistoryStudent(testStudent(id = 2, fullName = "Bob", groupId = 2), 1)
        val entries = listOf(
            testClassHistoryEntry(
                date,
                listOf(anna),
                "series-a:monday",
                "09:00–10:15 · Asia/Bangkok",
                morning,
                morningEnd,
            ),
            testClassHistoryEntry(
                date,
                listOf(bob),
                "series-b:monday",
                "09:00–10:15 · Asia/Bangkok",
                morning,
                morningEnd,
            ),
        )
        val day = entries.toClassHistoryDays().single()
        assertEquals(date, day.date)
        assertEquals(3, day.visitCount)
        assertEquals(listOf(anna, bob), day.blocks.single().students)
        assertEquals(listOf("series-a:monday", "series-b:monday"), entries.map { it.sessionId })
    }

    @Test
    fun `days and times sort newest first regardless of input order`() {
        val evening = Instant.parse("2026-10-05T11:00:00Z")
        val entries = listOf(
            testClassHistoryEntry(date, sessionId = "morning", startsAt = morning, endsAt = morningEnd),
            testClassHistoryEntry(LocalDate(2026, 10, 2)),
            testClassHistoryEntry(
                date,
                sessionId = "evening",
                startsAt = evening,
                endsAt = Instant.parse("2026-10-05T12:00:00Z"),
            ),
        )
        val days = entries.toClassHistoryDays()
        assertEquals(listOf(date, LocalDate(2026, 10, 2)), days.map { it.date })
        assertEquals(listOf(evening, morning), days.first().blocks.map { it.startsAt })
    }

    @Test
    fun `same start different end and same wall time in different zones stay separate`() {
        val entries = listOf(
            testClassHistoryEntry(date, sessionId = "a", startsAt = morning, endsAt = morningEnd),
            testClassHistoryEntry(
                date,
                sessionId = "b",
                startsAt = morning,
                endsAt = Instant.parse("2026-10-05T03:00:00Z"),
            ),
            testClassHistoryEntry(
                date,
                sessionId = "london",
                timeLabel = "09:00–10:15 · Europe/London",
                startsAt = Instant.parse("2026-10-05T08:00:00Z"),
                endsAt = Instant.parse("2026-10-05T09:15:00Z"),
            ),
        )
        assertEquals(3, entries.toClassHistoryDays().single().blocks.size)
    }

    @Test
    fun `legacy attendance without time remains separate and counted`() {
        val student = ClassHistoryStudent(testStudent(), 1)
        val entries = listOf(
            testClassHistoryEntry(date, listOf(student)),
            testClassHistoryEntry(date, sessionId = "known", startsAt = morning, endsAt = morningEnd),
        )
        val day = entries.toClassHistoryDays().single()
        assertEquals(2, day.blocks.size)
        assertNull(day.blocks.last().timeLabel)
        assertEquals(listOf(student), day.blocks.last().students)
        assertEquals(1, day.visitCount)
    }

    @Test
    fun `merged block retains visits across packages and suppresses empty duplicate series`() {
        val student = ClassHistoryStudent(testStudent(), 1)
        val entries = listOf(
            testClassHistoryEntry(date, listOf(student), "a", startsAt = morning, endsAt = morningEnd),
            testClassHistoryEntry(
                date,
                listOf(student.copy(visitCount = 2)),
                "b",
                startsAt = morning,
                endsAt = morningEnd,
            ),
            testClassHistoryEntry(date, sessionId = "empty", startsAt = morning, endsAt = morningEnd),
        )
        val day = entries.toClassHistoryDays().single()
        assertEquals(3, day.visitCount)
        assertEquals(listOf(student.copy(visitCount = 3)), day.blocks.single().students)
    }
}
