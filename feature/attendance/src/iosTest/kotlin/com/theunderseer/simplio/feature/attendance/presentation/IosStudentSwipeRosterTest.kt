package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.AttendanceRoster
import com.theunderseer.simplio.feature.attendance.domain.model.StudentGroup
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentAttendanceUiState
import kotlinx.coroutines.CompletableDeferred
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class IosStudentSwipeRosterTest {
    @Test
    fun `left swipe reveals one square and tapping the list closes it`() = runComposeUiTest {
        var refreshes = 0
        setContent {
            AttendanceRosterScreen(
                state = AttendanceRosterState.Content(studentSwipeRoster()),
                now = Instant.parse("2026-10-02T10:00:00Z"),
                onRefresh = { refreshes++ },
                onSignOut = {},
                onEdit = { null },
                onDeleteStudent = { null },
            )
        }

        val first = onNodeWithTag("ios-student-row-1")
        val second = onNodeWithTag("ios-student-row-2")
        val firstDelete = onNodeWithContentDescription("Delete student First sample student")
        val secondDelete = onNodeWithContentDescription("Delete student Second sample student")

        first.performTouchInput { swipeRight() }
        firstDelete.assertDoesNotExist()
        first.performTouchInput { swipeLeft() }
        val revealed = firstDelete.assertIsDisplayed().getUnclippedBoundsInRoot()
        val row = first.getUnclippedBoundsInRoot()
        val revealedWidth = revealed.right - revealed.left
        assertTrue(abs((revealedWidth - (revealed.bottom - revealed.top)).value) <= 1f)
        assertTrue(abs((revealedWidth - (row.bottom - row.top)).value) <= 1f)
        assertEquals(row.right, revealed.right)
        onNodeWithText("Delete student?").assertDoesNotExist()

        second.performTouchInput { swipeLeft() }
        firstDelete.assertDoesNotExist()
        secondDelete.assertIsDisplayed()
        second.performTouchInput { click(centerLeft + Offset(40.dp.toPx(), 0f)) }
        secondDelete.assertDoesNotExist()

        first.performTouchInput { swipeLeft() }
        second.performTouchInput { click() }
        firstDelete.assertDoesNotExist()

        first.performTouchInput { swipeLeft() }
        onNodeWithText("Refresh").performTouchInput { click() }
        firstDelete.assertDoesNotExist()
        runOnIdle { assertEquals(1, refreshes) }
    }

    @Test
    fun `delete requires confirmation and a failed write can be retried`() = runComposeUiTest {
        var roster by mutableStateOf(studentSwipeRoster())
        val requests = mutableListOf<Int>()
        val response = CompletableDeferred<String?>()
        val deleteStudent: suspend (Int) -> String? = { studentId ->
            requests += studentId
            if (requests.size == 1) {
                response.await()
            } else {
                roster = roster.copy(
                    attendance = Attendance(roster.attendance.entries.filterNot { it.student.id == studentId }),
                )
                null
            }
        }
        setContent {
            AttendanceRosterScreen(
                state = AttendanceRosterState.Content(roster),
                now = Instant.parse("2026-10-02T10:00:00Z"),
                onRefresh = {},
                onSignOut = {},
                onEdit = { null },
                onDeleteStudent = deleteStudent,
            )
        }

        val first = onNodeWithTag("ios-student-row-1")
        val delete = onNodeWithContentDescription("Delete student First sample student")
        val modal = hasAnyAncestor(isDialog())
        val cancel = onNode(modal and hasText("Cancel"))
        val confirm = onNode(modal and hasText("Delete"))
        first.performTouchInput { swipeLeft() }
        delete.performTouchInput { click() }
        onNode(modal and hasText("Delete \"First sample student\"?")).assertIsDisplayed()
        cancel.performClick()
        runOnIdle { assertTrue(requests.isEmpty()) }
        first.assertIsDisplayed()
        delete.assertDoesNotExist()

        first.performTouchInput { swipeLeft() }
        delete.performTouchInput { click() }
        confirm.performClick()
        onNode(modal and hasText("Deleting…")).assertIsNotEnabled()
        cancel.assertIsNotEnabled()
        response.complete("Couldn't delete the student. Try again.")
        onNode(modal and hasText("Couldn't delete the student. Try again.")).assertIsDisplayed()
        first.assertIsDisplayed()
        confirm.performClick()
        onNode(modal and hasText("Delete student?")).assertDoesNotExist()
        first.assertDoesNotExist()
        onNodeWithTag("ios-student-row-2").assertIsDisplayed()
        runOnIdle { assertEquals(listOf(1, 1), requests) }
    }
}

private fun studentSwipeRoster(): AttendanceRoster {
    val firstGroup = StudentGroup(1, "Monday class")
    val secondGroup = StudentGroup(2, "Friday class")
    val students = listOf(
        AttendanceTestData.student.copy(id = 1, fullName = "First sample student", groupId = firstGroup.id),
        AttendanceTestData.student.copy(id = 2, fullName = "Second sample student", groupId = secondGroup.id),
    )
    return AttendanceRoster(
        attendance = Attendance(students.flatMap { testStudentAttendanceUiState(student = it).attendance.entries }),
        groupSchedules = emptyMap(),
        studentGroups = listOf(firstGroup, secondGroup),
    )
}
