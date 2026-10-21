package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.currentDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.firstClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.secondClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.thirdClassDate
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegistrationDateSelectionTest {
    @Test
    fun `allows past Mondays and Fridays but blocks future scheduled dates`() {
        assertTrue(isRegistrationDateSelectable(firstClassDate, currentDate))
        assertTrue(isRegistrationDateSelectable(secondClassDate, currentDate))
        assertFalse(isRegistrationDateSelectable(thirdClassDate, currentDate))
        assertFalse(isRegistrationDateSelectable(secondClassDate, firstClassDate))
    }

    @Test
    fun `allows today only when it is scheduled`() {
        assertTrue(isRegistrationDateSelectable(firstClassDate, firstClassDate))
        assertTrue(isRegistrationDateSelectable(secondClassDate, secondClassDate))
        assertFalse(isRegistrationDateSelectable(currentDate, currentDate))
    }

    @Test
    fun `blocks every other weekday even in the past`() {
        for (day in listOf(3, 4, 6, 7, 8)) {
            assertFalse(isRegistrationDateSelectable(LocalDate(2026, 10, day), thirdClassDate))
        }
    }
}
