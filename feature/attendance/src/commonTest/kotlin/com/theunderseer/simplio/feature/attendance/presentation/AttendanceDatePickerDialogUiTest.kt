package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
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
                maxVisitsPerAttendance = 1,
                remainingClasses = 3,
                onConfirm = { date, _ ->
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

    @Test
    fun `defaults to two people when duo package has sufficient remaining visits`() = runComposeUiTest {
        // Given
        setContent {
            AttendanceDatePickerDialog(
                initialDate = LocalDate(2026, 10, 5),
                currentDate = LocalDate(2026, 10, 8),
                maxVisitsPerAttendance = 2,
                remainingClasses = 6,
                onConfirm = { _, _ -> },
                onDismiss = {},
            )
        }

        // Then
        onNodeWithText("1 person")
            .assertIsDisplayed()

        onNodeWithText("2 people")
            .assertIsDisplayed()
            .assertIsSelected()
    }

    @Test
    fun `confirms two visits by default for duo attendance`() = runComposeUiTest {
        // Given
        val attendanceDate = LocalDate(2026, 10, 5)
        val confirmedAttendance = mutableListOf<Pair<LocalDate, Int>>()

        setContent {
            AttendanceDatePickerDialog(
                initialDate = attendanceDate,
                currentDate = LocalDate(2026, 10, 8),
                maxVisitsPerAttendance = 2,
                remainingClasses = 6,
                onConfirm = { date, visitCount ->
                    confirmedAttendance += date to visitCount
                },
                onDismiss = {},
            )
        }

        onNodeWithText("2 people")
            .assertIsSelected()

        assertEquals(emptyList(), confirmedAttendance)

        // When
        onNodeWithText("OK")
            .performClick()

        // Then
        assertEquals(
            listOf(attendanceDate to 2),
            confirmedAttendance,
        )
    }

    @Test
    fun `confirms one visit when one person is selected`() = runComposeUiTest {
        // Given
        val attendanceDate = LocalDate(2026, 10, 5)
        val confirmedAttendance = mutableListOf<Pair<LocalDate, Int>>()

        setContent {
            AttendanceDatePickerDialog(
                initialDate = attendanceDate,
                currentDate = LocalDate(2026, 10, 8),
                maxVisitsPerAttendance = 2,
                remainingClasses = 6,
                onConfirm = { date, visitCount ->
                    confirmedAttendance += date to visitCount
                },
                onDismiss = {},
            )
        }

        // When
        onNodeWithText("1 person")
            .performClick()

        // Then: changing the count does not dispatch attendance
        onNodeWithText("1 person")
            .assertIsSelected()

        assertEquals(emptyList(), confirmedAttendance)

        // When
        onNodeWithText("OK")
            .performClick()

        // Then
        assertEquals(
            listOf(attendanceDate to 1),
            confirmedAttendance,
        )
    }
}
