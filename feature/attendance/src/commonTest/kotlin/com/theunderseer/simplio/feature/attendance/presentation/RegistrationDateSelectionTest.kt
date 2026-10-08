package com.theunderseer.simplio.feature.attendance.presentation

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegistrationDateSelectionTest {
    @Test
    fun `does not allow registration date after today`() {
        // Given
        val today = LocalDate(2026, 10, 5)

        // Then
        assertTrue(
            isRegistrationDateSelectable(
                date = today,
                today = today,
            ),
        )

        assertTrue(
            isRegistrationDateSelectable(
                date = LocalDate(2026, 10, 4),
                today = today,
            ),
        )

        assertFalse(
            isRegistrationDateSelectable(
                date = LocalDate(2026, 10, 6),
                today = today,
            ),
        )
    }
}
