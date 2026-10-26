package com.theunderseer.simplio.feature.attendance.presentation

import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScheduleInputValidationTest {
    @Test
    fun `clock values require exact twenty four hour minute precision`() {
        assertEquals(LocalTime(0, 0), parseScheduleTime("00:00"))
        assertEquals(LocalTime(9, 0), parseScheduleTime("09:00"))
        assertEquals(LocalTime(23, 59), parseScheduleTime("23:59"))
        listOf("", "9:00", "09:0", "09:00:00", "24:00", "12:60", "-1:00", "１２:００").forEach {
            assertNull(parseScheduleTime(it), it)
        }
    }
}
