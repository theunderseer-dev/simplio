package com.theunderseer.simplio.feature.attendance.presentation.screen

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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
    fun `first attendance check is disabled and cannot open an attendance dialog`() = runComposeUiTest {
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

        // Then: the first attendance still appears checked
        val check = onNodeWithContentDescription(
            "Mark ${student.fullName} present",
        )

        check.assertIsNotEnabled()
        check.assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "Checked",
            ),
        )
        onNodeWithText("1 / 4").assertIsDisplayed()

        // When: a click is attempted
        check.performClick()

        // Then: neither attendance action is opened or dispatched
        onNodeWithText("Remove attendance?").assertDoesNotExist()
        onNodeWithText("Select attendance date").assertDoesNotExist()
        assertEquals(emptyList(), markedAttendance)
        assertEquals(emptyList(), removedAttendance)
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
        onNodeWithText("✓")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "Checked",
                ),
            )
    }
}
