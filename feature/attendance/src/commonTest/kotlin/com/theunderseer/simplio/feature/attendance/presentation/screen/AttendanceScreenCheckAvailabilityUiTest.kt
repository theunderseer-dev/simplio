package com.theunderseer.simplio.feature.attendance.presentation.screen

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
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
class AttendanceScreenCheckAvailabilityUiTest {
    @Test
    fun `first attendance check is enabled and removal requires confirmation`() = runComposeUiTest {
        // Given
        val state = testStudentAttendanceUiState(
            currentDate = firstClassDate,
        )
        val markedAttendance = mutableListOf<Pair<Int, LocalDate>>()
        val removedAttendance = mutableListOf<Pair<Int, LocalDate>>()

        setContent {
            AttendanceScreenForTest(
                state = state,
                onMarkStudentPresent = { studentId, date, _ ->
                    markedAttendance += studentId to date
                },
                onRemoveStudentAttendance = { studentId, date ->
                    removedAttendance += studentId to date
                },
            )
        }

        // Then: the first attendance is checked and its action is enabled
        val check = onNodeWithContentDescription(
            "Mark ${student.fullName} present",
        )

        check.assertIsEnabled()
        check.assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "Checked",
            ),
        )
        onNodeWithText("1 / 4").assertIsDisplayed()

        // When
        check.performClick()

        // Then: opening confirmation does not change attendance
        onNodeWithText("Remove attendance?").assertIsDisplayed()
        onNodeWithText("Select attendance date").assertDoesNotExist()
        assertEquals(emptyList(), markedAttendance)
        assertEquals(emptyList(), removedAttendance)

        // When
        onNodeWithText("Remove").performClick()

        // Then: only the confirmed removal is dispatched
        assertEquals(listOf(student.id to firstClassDate), removedAttendance)
        assertEquals(emptyList(), markedAttendance)
    }

    @Test
    fun `shows checked state when latest scheduled attendance is recorded`() = runComposeUiTest {
        // Given
        val state = testStudentAttendanceUiState(
            visitsByDate = mapOf(
                firstClassDate to 1,
                secondClassDate to 1,
            ),
        )

        // When
        setContent {
            AttendanceScreenForTest(
                state = state,
            )
        }

        // Then
        onNodeWithContentDescription("Mark ${student.fullName} present")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "Checked",
                ),
            )
    }
}
