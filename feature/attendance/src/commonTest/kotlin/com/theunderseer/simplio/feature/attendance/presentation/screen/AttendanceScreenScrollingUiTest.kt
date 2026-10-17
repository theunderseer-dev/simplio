package com.theunderseer.simplio.feature.attendance.presentation.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testAttendanceContentUiState
import com.theunderseer.simplio.feature.attendance.fixtures.testClassHistoryEntry
import com.theunderseer.simplio.feature.attendance.fixtures.testStudent
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentPackage
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.ClassHistoryUiState
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AttendanceScreenScrollingUiTest {
    @Test
    fun `last student can be reached and deleted in a short window`() = runComposeUiTest {
        // Given
        val state = manyStudentsState()
        val lastStudent = state.attendance.entries.last().student
        var deletedStudentId: Int? = null

        setContent {
            Box(
                modifier = Modifier.fillMaxWidth().height(240.dp),
            ) {
                AttendanceScreenForTest(
                    state = state,
                    onDeleteStudent = { studentId ->
                        deletedStudentId = studentId
                    },
                )
            }
        }

        // When
        onNodeWithTag("attendance-list")
            .performScrollToNode(hasTestTag("student-row-${lastStudent.id}"))

        onNodeWithContentDescription("Delete ${lastStudent.fullName}")
            .assertIsDisplayed()
            .performClick()

        onNodeWithText("Delete")
            .performClick()

        // Then
        assertEquals(lastStudent.id, deletedStudentId)
    }

    @Test
    fun `add student and history pagination remain reachable after a long student list`() = runComposeUiTest {
        // Given
        var nextPageRequests = 0

        setContent {
            Box(
                modifier = Modifier.fillMaxWidth().height(240.dp),
            ) {
                AttendanceScreenForTest(
                    state = manyStudentsState(),
                    onLoadNextClassHistoryPage = { nextPageRequests++ },
                )
            }
        }

        // When
        onNodeWithTag("attendance-list")
            .performScrollToNode(hasText("Add Student"))

        // Then
        onNodeWithText("Add Student")
            .assertIsDisplayed()

        // When
        onNodeWithTag("attendance-list")
            .performScrollToNode(hasTestTag("class-history"))

        // Then
        onNodeWithText("Class History")
            .assertIsDisplayed()

        // When
        onNodeWithTag("attendance-list")
            .performScrollToNode(hasText("Load more"))

        onNodeWithText("Load more")
            .assertIsDisplayed()
            .performClick()

        // Then
        assertEquals(1, nextPageRequests)
    }

    private fun manyStudentsState(): AttendanceUiState.Content {
        val packageType = AttendanceTestData.fourClassPackage
        val date = AttendanceTestData.registrationDate
        val entries = List(60) { index ->
            val student = testStudent(
                id = index + 1,
                fullName = "Student ${index + 1}",
            )

            RegisteredStudent(
                student = student,
                studentPackage = testStudentPackage(
                    id = student.id,
                    studentId = student.id,
                    packageType = packageType,
                    startedOn = date,
                ),
            )
        }

        return testAttendanceContentUiState(
            attendance = Attendance(entries = entries),
            packageTypes = listOf(packageType),
            currentDate = date,
        ).copy(
            classHistoryState = ClassHistoryUiState.Content(
                entries = listOf(testClassHistoryEntry(date = date)),
                nextBeforeDate = date,
                isLoadingNextPage = false,
                nextPageErrorMessage = null,
            ),
        )
    }
}
