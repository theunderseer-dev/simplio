package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
            AttendanceDatePickerDialogForTest(
                onConfirm = { date, _ ->
                    confirmedDates += date
                },
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
            AttendanceDatePickerDialogForTest(
                maxVisitsPerAttendance = 2,
                remainingClasses = 6,
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
            AttendanceDatePickerDialogForTest(
                initialDate = attendanceDate,
                maxVisitsPerAttendance = 2,
                remainingClasses = 6,
                onConfirm = { date, visitCount ->
                    confirmedAttendance += date to visitCount
                },
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
            AttendanceDatePickerDialogForTest(
                initialDate = attendanceDate,
                maxVisitsPerAttendance = 2,
                remainingClasses = 6,
                onConfirm = { date, visitCount ->
                    confirmedAttendance += date to visitCount
                },
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

    @Test
    fun `recorded date is disabled and does not replace selected date`() = runComposeUiTest {
        // Given
        val confirmedDates = mutableListOf<LocalDate>()

        setContent {
            AttendanceDatePickerDialogForTest(
                recordedAttendanceDates = setOf(LocalDate(2026, 10, 2)),
                onConfirm = { date, _ ->
                    confirmedDates += date
                },
            )
        }

        // When
        onNodeWithText(
            text = "Friday, October 2, 2026",
            substring = true,
        )
            .assertIsNotEnabled()
            .performClick()

        // Then
        onNodeWithText(
            text = "Monday, October 5, 2026",
            substring = true,
        ).assertIsSelected()

        assertEquals(emptyList(), confirmedDates)

        // When
        onNodeWithText("OK").performClick()

        // Then
        assertEquals(
            listOf(LocalDate(2026, 10, 5)),
            confirmedDates,
        )
    }

    @Test
    fun `recorded initial date cannot be confirmed for duo attendance`() = runComposeUiTest {
        // Given
        val recordedDate = LocalDate(2026, 10, 5)
        val confirmedAttendance = mutableListOf<Pair<LocalDate, Int>>()

        setContent {
            AttendanceDatePickerDialogForTest(
                initialDate = recordedDate,
                maxVisitsPerAttendance = 2,
                remainingClasses = 6,
                recordedAttendanceDates = setOf(recordedDate),
                onConfirm = { date, visitCount ->
                    confirmedAttendance += date to visitCount
                },
            )
        }

        // Then
        onNodeWithText(
            text = "Monday, October 5, 2026",
            substring = true,
        ).assertIsNotEnabled()

        // When
        onNodeWithText("OK")
            .assertIsNotEnabled()
            .performClick()

        // Then
        assertEquals(emptyList(), confirmedAttendance)

        // When: a date without attendance is selected
        onNodeWithText(
            text = "Friday, October 2, 2026",
            substring = true,
        ).performClick()

        onNodeWithText("OK")
            .assertIsEnabled()
            .performClick()

        // Then
        assertEquals(
            listOf(LocalDate(2026, 10, 2) to 2),
            confirmedAttendance,
        )
    }

    @Test
    fun `updated history disables selected date without reopening dialog`() = runComposeUiTest {
        // Given
        val selectedDate = LocalDate(2026, 10, 5)
        val recordedDates = mutableStateOf(emptySet<LocalDate>())
        val confirmedDates = mutableListOf<LocalDate>()

        setContent {
            AttendanceDatePickerDialogForTest(
                initialDate = selectedDate,
                recordedAttendanceDates = recordedDates.value,
                onConfirm = { date, _ ->
                    confirmedDates += date
                },
            )
        }

        onNodeWithText("OK").assertIsEnabled()

        // When
        runOnIdle {
            recordedDates.value = setOf(selectedDate)
        }

        // Then
        onNodeWithText(
            text = "Monday, October 5, 2026",
            substring = true,
        ).assertIsNotEnabled()

        onNodeWithText("OK")
            .assertIsNotEnabled()
            .performClick()

        assertEquals(emptyList(), confirmedDates)
    }
}
