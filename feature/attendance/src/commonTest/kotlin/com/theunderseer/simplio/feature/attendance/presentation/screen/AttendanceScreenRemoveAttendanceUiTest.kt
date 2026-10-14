package com.theunderseer.simplio.feature.attendance.presentation.screen

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.firstClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.secondClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.student
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentAttendanceUiState
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AttendanceScreenRemoveAttendanceUiTest {
    private val checkedAttendanceState =
        testStudentAttendanceUiState(
            visitsByDate = mapOf(
                firstClassDate to 1,
                secondClassDate to 1,
            ),
        )

    @Test
    fun `requests attendance removal with student id and date`() = runComposeUiTest {
        // Given
        val removedAttendance = mutableListOf<Pair<Int, LocalDate>>()

        setContent {
            AttendanceScreenForTest(
                state = checkedAttendanceState,
                onRemoveStudentAttendance = { studentId, date ->
                    removedAttendance += studentId to date
                },
            )
        }

        // When
        onNodeWithContentDescription(
            "Mark ${student.fullName} present",
        ).performClick()

        // Then
        onNodeWithText("Remove attendance?")
            .assertIsDisplayed()

        // Opening the dialog does not remove attendance
        assertEquals(
            emptyList(),
            removedAttendance,
        )

        // When
        onNodeWithText("Remove")
            .performClick()

        // Then
        onNodeWithText("Remove attendance?")
            .assertIsDisplayed()

        assertEquals(
            listOf(student.id to secondClassDate),
            removedAttendance,
        )
    }

    @Test
    fun `opens attendance removal confirmation when checked attendance is clicked`() = runComposeUiTest {
        // Given
        setContent {
            AttendanceScreenForTest(
                state = checkedAttendanceState,
            )
        }

        // When
        onNodeWithContentDescription(
            "Mark ${student.fullName} present",
        ).performClick()

        // Then
        onNodeWithText("Remove attendance?")
            .assertIsDisplayed()

        onNodeWithText(
            "Remove attendance for Anna Smith on Oct 5?",
        ).assertIsDisplayed()

        onNodeWithText("Remove")
            .assertIsDisplayed()

        onNodeWithText("Cancel")
            .assertIsDisplayed()

        onNodeWithText("Select attendance date")
            .assertDoesNotExist()
    }
}
