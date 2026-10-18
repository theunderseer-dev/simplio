package com.theunderseer.simplio.feature.attendance.presentation.screen

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentAttendanceUiState
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AttendanceScreenFirstAttendanceRemovalUiTest {
    @Test
    fun `checked action allows removing first attendance after confirmation`() = runComposeUiTest {
        // Given
        val student = AttendanceTestData.student
        val date = AttendanceTestData.firstClassDate
        val requests = mutableListOf<Pair<Int, LocalDate>>()

        setContent {
            AttendanceScreenForTest(
                state = testStudentAttendanceUiState(
                    student = student,
                    startedOn = date,
                    currentDate = date,
                ),
                onRemoveStudentAttendance = { studentId, attendanceDate ->
                    requests += studentId to attendanceDate
                },
            )
        }

        // When
        onNodeWithContentDescription("Mark ${student.fullName} present")
            .performClick()

        // Then
        onNodeWithText("Remove attendance?")
            .assertIsDisplayed()
        assertEquals(emptyList(), requests)

        // When
        onNodeWithText("Remove")
            .performClick()

        // Then
        assertEquals(listOf(student.id to date), requests)
    }

    @Test
    fun `package history allows removing first attendance after confirmation`() = runComposeUiTest {
        // Given
        val student = AttendanceTestData.student
        val date = AttendanceTestData.firstClassDate
        val requests = mutableListOf<Pair<Int, LocalDate>>()

        setContent {
            AttendanceScreenForTest(
                state = testStudentAttendanceUiState(
                    student = student,
                    startedOn = date,
                    currentDate = date,
                ),
                onRemoveStudentAttendance = { studentId, attendanceDate ->
                    requests += studentId to attendanceDate
                },
            )
        }

        // When
        onNodeWithContentDescription("View package history for ${student.fullName}")
            .performClick()

        onNodeWithContentDescription("Remove attendance on Oct 2 for ${student.fullName}")
            .assertIsDisplayed()
            .performClick()

        // Then
        onNodeWithText("Remove attendance?")
            .assertIsDisplayed()
        assertEquals(emptyList(), requests)

        // When
        onNodeWithText("Remove")
            .performClick()

        // Then
        assertEquals(listOf(student.id to date), requests)
    }
}
