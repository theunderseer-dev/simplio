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
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.firstClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.fourClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.secondClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.student
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.thirdClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.testAttendanceContentUiState
import com.theunderseer.simplio.feature.attendance.fixtures.testStudent
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentAttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceUiState
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AttendanceScreenUiTest {
    @Test
    fun `displays registered student attendance`() = runComposeUiTest {
        // Given
        val state = testStudentAttendanceUiState(
            visitsByDate = mapOf(
                firstClassDate to 1,
                secondClassDate to 1,
                thirdClassDate to 1,
            ),
            currentDate = thirdClassDate,
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
            groupId = DefaultStudentGroups.GROUP_A.id,
        )
        val groupBStudent = testStudent(
            id = 8,
            fullName = "Bob Lee",
            groupId = DefaultStudentGroups.GROUP_B.id,
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
        val groupAHeader = onNode(
            hasText(DefaultStudentGroups.GROUP_A.name) and
                hasAnyAncestor(
                    hasTestTag("student-group-${groupAStudent.groupId}"),
                ),
        ).assertIsDisplayed()

        val groupARow = onNode(
            hasText(groupAStudent.fullName) and
                hasAnyAncestor(
                    hasTestTag("student-row-${groupAStudent.id}"),
                ),
        ).assertIsDisplayed()

        val groupBHeader = onNode(
            hasText(DefaultStudentGroups.GROUP_B.name) and
                hasAnyAncestor(
                    hasTestTag("student-group-${groupBStudent.groupId}"),
                ),
        ).assertIsDisplayed()

        val groupBRow = onNode(
            hasText(groupBStudent.fullName) and
                hasAnyAncestor(
                    hasTestTag("student-row-${groupBStudent.id}"),
                ),
        ).assertIsDisplayed()

        val groupAHeaderBounds = groupAHeader.fetchSemanticsNode().boundsInRoot
        val groupARowBounds = groupARow.fetchSemanticsNode().boundsInRoot
        val groupBHeaderBounds = groupBHeader.fetchSemanticsNode().boundsInRoot
        val groupBRowBounds = groupBRow.fetchSemanticsNode().boundsInRoot

        assertTrue(
            groupAHeaderBounds.bottom <= groupARowBounds.top,
            "Group A student must appear below the Group A header",
        )
        assertTrue(
            groupARowBounds.bottom <= groupBHeaderBounds.top,
            "Group A students must appear before the Group B header",
        )
        assertTrue(
            groupBHeaderBounds.bottom <= groupBRowBounds.top,
            "Group B student must appear below the Group B header",
        )
    }

    @Test
    fun `hides group sections without students`() = runComposeUiTest {
        // Given
        val state = testStudentAttendanceUiState(
            student = student.copy(
                groupId = DefaultStudentGroups.GROUP_A.id,
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
