package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.model.WeeklyClassSlot
import com.theunderseer.simplio.feature.attendance.domain.schedule.latestClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.testClassSchedule
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroupCalendarTest {
    private val schedules = listOf(
        testClassSchedule(
            slots = listOf(
                WeeklyClassSlot("tuesday", DayOfWeek.TUESDAY, LocalTime(9, 0), LocalTime(10, 0)),
                WeeklyClassSlot("thursday", DayOfWeek.THURSDAY, LocalTime(9, 0), LocalTime(10, 0)),
            ),
        ),
    )

    @Test
    fun `registration and renewal allow past weekdays before series creation`() {
        val today = LocalDate(2026, 10, 8)
        assertTrue(isPackagePurchaseDateSelectable(LocalDate(2026, 10, 6), today, null, schedules))
        assertFalse(isPackagePurchaseDateSelectable(LocalDate(2026, 10, 5), today, null, schedules))
        assertFalse(isPackagePurchaseDateSelectable(LocalDate(2026, 10, 13), today, null, schedules))
        assertTrue(isPackagePurchaseDateSelectable(LocalDate(2026, 10, 1), today, null, schedules))
        assertTrue(isPackagePurchaseDateSelectable(LocalDate(2025, 9, 2), today, null, schedules))
        assertEquals(today, initialPackagePurchaseDate(today, null, schedules))
        assertEquals(LocalDate(2026, 10, 6), schedules.latestClassDate(LocalDate(2026, 10, 7)))
    }

    @Test
    fun `new Monday Friday series defaults to previous Monday on Wednesday`() {
        val today = LocalDate(2026, 10, 21)
        val newSchedules = listOf(
            testClassSchedule(
                startsOn = today,
                slots = listOf(
                    WeeklyClassSlot("monday", DayOfWeek.MONDAY, LocalTime(9, 0), LocalTime(10, 15)),
                    WeeklyClassSlot("friday", DayOfWeek.FRIDAY, LocalTime(9, 0), LocalTime(10, 15)),
                ),
            ),
        )
        assertEquals(LocalDate(2026, 10, 19), initialPackagePurchaseDate(today, null, newSchedules))
        assertTrue(isPackagePurchaseDateSelectable(LocalDate(2026, 9, 4), today, null, newSchedules))
        assertFalse(isPackagePurchaseDateSelectable(today, today, null, newSchedules))
        assertFalse(isPackagePurchaseDateSelectable(LocalDate(2026, 10, 23), today, null, newSchedules))
        assertEquals(null, newSchedules.latestClassDate(today))
    }

    @Test
    fun `backdating retains series end and earliest renewal constraints`() {
        val today = LocalDate(2026, 10, 21)
        val ended = schedules.map { it.copy(endsOn = LocalDate(2026, 10, 8)) }
        assertEquals(LocalDate(2026, 10, 8), initialPackagePurchaseDate(today, null, ended))
        assertFalse(isPackagePurchaseDateSelectable(LocalDate(2026, 10, 13), today, null, ended))
        assertFalse(
            isPackagePurchaseDateSelectable(LocalDate(2026, 10, 1), today, LocalDate(2026, 10, 6), schedules),
        )
    }
}
