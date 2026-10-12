package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.currentDate
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegistrationDateSelectionTest {
    @Test
    fun `does not allow registration date after today`() {
        assertTrue(
            isRegistrationDateSelectable(
                date = currentDate,
                today = currentDate,
            ),
        )

        assertTrue(
            isRegistrationDateSelectable(
                date = currentDate - DatePeriod(days = 1),
                today = currentDate,
            ),
        )

        assertFalse(
            isRegistrationDateSelectable(
                date = currentDate + DatePeriod(days = 1),
                today = currentDate,
            ),
        )
    }
}
