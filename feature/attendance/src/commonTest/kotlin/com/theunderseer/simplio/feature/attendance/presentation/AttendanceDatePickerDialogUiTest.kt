package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AttendanceDatePickerDialogUiTest {
    @Test
    fun `confirms selected attendance date only after OK is clicked`() = runComposeUiTest {
        // Given
        val confirmedDates = mutableListOf<LocalDate>()

        setContent {
            AttendanceDatePickerDialog(
                initialDate = LocalDate(2026, 10, 5),
                currentDate = LocalDate(2026, 10, 8),
                onConfirm = { date ->
                    confirmedDates += date
                },
                onDismiss = {},
            )
        }

        assertEquals(
            emptyList(),
            confirmedDates,
        )

        // When: a different allowed date is selected
        onNodeWithText(
            text = "Friday, October 2, 2026",
            substring = true,
        )
            .performClick()

        // Then: selection alone does not confirm attendance
        assertEquals(
            emptyList(),
            confirmedDates,
        )

        // When
        onNodeWithText("OK")
            .performClick()

        // Then
        assertEquals(
            listOf(LocalDate(2026, 10, 2)),
            confirmedDates,
        )
    }
}
