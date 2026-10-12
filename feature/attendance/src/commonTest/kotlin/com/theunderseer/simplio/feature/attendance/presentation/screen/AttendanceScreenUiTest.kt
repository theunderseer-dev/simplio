package com.theunderseer.simplio.feature.attendance.presentation.screen

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.DefaultStudentGroups
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.currentDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.firstAttendanceDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.fourClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.nextAttendanceDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.nextClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.student
import com.theunderseer.simplio.feature.attendance.fixtures.testAttendanceContentUiState
import com.theunderseer.simplio.feature.attendance.fixtures.testStudent
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentAttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceUiState
import kotlinx.datetime.LocalDate
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AttendanceScreenUiTest {
    @Test
    fun `displays registered student attendance`() = runComposeUiTest {
        // Given
        val state = testStudentAttendanceUiState(
            visitsByDate = mapOf(
                firstAttendanceDate to 1,
                nextAttendanceDate to 1,
                nextClassDate to 1,
            ),
            currentDate = nextClassDate,
        )

        // When
        setContent {
            AttendanceScreenForTest(
                state = state,
            )
        }

        // Then
        onNodeWithText("Anna Smith")
            .assertIsDisplayed()

        onNodeWithText("3 / 4")
            .assertIsDisplayed()

        onNodeWithText("Oct 2")
            .assertIsDisplayed()

        onNodeWithText("Oct 30")
            .assertIsDisplayed()

        onNodeWithText("4-class package")
            .assertIsDisplayed()
    }

    @Test
    fun `displays empty attendance state`() = runComposeUiTest {
        // Given
        val state = emptyAttendanceState()

        // When
        setContent {
            AttendanceScreenForTest(
                state = state,
            )
        }

        // Then
        onNodeWithText("No students yet")
            .assertIsDisplayed()

        onNodeWithText("Add Student")
            .assertIsDisplayed()
    }

    @Test
    fun `displays attendance error message`() = runComposeUiTest {
        // Given
        val message = "Not enough visits remaining"

        val state =
            AttendanceUiState.Error(
                message = message,
            )

        // When
        setContent {
            AttendanceScreenForTest(
                state = state,
            )
        }

        // Then
        onNodeWithText(message)
            .assertIsDisplayed()
    }

    @Test
    fun `displays students in their corresponding group sections`() = runComposeUiTest {
        // Given
        val groupAStudent = student.copy(
            groupId = DefaultStudentGroups.GROUP_A_ID,
        )
        val groupBStudent = testStudent(
            id = 8,
            fullName = "Bob Lee",
            groupId = DefaultStudentGroups.GROUP_B_ID,
        )

        // Group B deliberately appears first in the source attendance.
        val entries = listOf(groupBStudent, groupAStudent).map { student ->
            testStudentAttendanceUiState(student = student).attendance.entries.single()
        }

        val state = testAttendanceContentUiState(
            attendance = Attendance(entries = entries),
            packageTypes = listOf(fourClassPackage),
            currentDate = currentDate,
        )

        // When
        setContent {
            AttendanceScreenForTest(state = state)
        }

        // Then
        onNode(
            hasText(DefaultStudentGroups.GROUP_A.name) and
                hasAnyAncestor(
                    hasTestTag("student-group-${groupAStudent.groupId}"),
                ),
        ).assertIsDisplayed()

        onNode(
            hasText(groupAStudent.fullName) and
                hasAnyAncestor(
                    hasTestTag("student-group-${groupAStudent.groupId}"),
                ),
        ).assertIsDisplayed()

        onNode(
            hasText(DefaultStudentGroups.GROUP_B.name) and
                hasAnyAncestor(
                    hasTestTag("student-group-${groupBStudent.groupId}"),
                ),
        ).assertIsDisplayed()

        onNode(
            hasText(groupBStudent.fullName) and
                hasAnyAncestor(
                    hasTestTag("student-group-${groupBStudent.groupId}"),
                ),
        ).assertIsDisplayed()
    }

    @Test
    fun `hides group sections without students`() = runComposeUiTest {
        // Given
        val state = testStudentAttendanceUiState(
            student = student.copy(
                groupId = DefaultStudentGroups.GROUP_A_ID,
            ),
        )

        // When
        setContent {
            AttendanceScreenForTest(state = state)
        }

        // Then
        onNodeWithText(DefaultStudentGroups.GROUP_A.name)
            .assertIsDisplayed()

        onNodeWithText(DefaultStudentGroups.GROUP_B.name)
            .assertDoesNotExist()
    }

    private fun emptyAttendanceState(
        packageTypes: List<PackageType> = emptyList(),
        currentDate: LocalDate = AttendanceTestData.currentDate,
    ): AttendanceUiState.Content = testAttendanceContentUiState(
        attendance = Attendance(entries = emptyList()),
        packageTypes = packageTypes,
        currentDate = currentDate,
    )
}
