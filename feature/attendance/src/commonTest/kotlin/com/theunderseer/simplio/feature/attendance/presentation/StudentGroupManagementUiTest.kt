package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.repository.DeleteStudentGroupResult
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentAttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.screen.AttendanceScreenForTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class StudentGroupManagementUiTest {
    @Test
    fun `occupied groups show a reason and empty groups require explicit confirmation`() = runComposeUiTest {
        var removed: Int? = null
        setContent { StudentGroupManagementDialog(testStudentAttendanceUiState(), { removed = it }, {}, {}) }
        val occupied = AttendanceTestData.studentGroups.first().id
        val empty = AttendanceTestData.studentGroups.last().id
        onNodeWithTag("delete-group-$occupied").assertIsNotEnabled()
        onNodeWithText("Move or remove the students before deleting this group.").assertExists()
        onNodeWithTag("delete-group-$empty").assertIsEnabled().performClick()
        assertEquals(null, removed)
        onNodeWithText("Cancel").performClick()
        assertEquals(null, removed)
        onNodeWithTag("delete-group-$empty").performClick()
        onNodeWithTag("confirm-group-delete").performClick()
        assertEquals(empty, removed)
    }

    @Test
    fun `student added during confirmation disables deletion and explains why`() = runComposeUiTest {
        val empty = AttendanceTestData.studentGroups.last().id
        setContent {
            var error by remember { mutableStateOf<DeleteStudentGroupUiState>(DeleteStudentGroupUiState.Idle) }
            StudentGroupManagementDialog(
                testStudentAttendanceUiState().copy(deleteStudentGroupState = error),
                { error = DeleteStudentGroupUiState.Error(it, DeleteStudentGroupResult.NOT_EMPTY) },
                {},
                {},
            )
        }
        onNodeWithTag("delete-group-$empty").performClick()
        onNodeWithTag("confirm-group-delete").performClick()
        onNodeWithTag("confirm-group-delete").assertIsNotEnabled()
        onNodeWithText("Move or remove the students before deleting this group.").assertExists()
    }

    @Test
    fun `group options on the main screen open management without changing registration`() = runComposeUiTest {
        setContent { AttendanceScreenForTest(testStudentAttendanceUiState()) }
        onNodeWithContentDescription("Group options").performClick()
        onNodeWithText("Manage groups").performClick()
        onNodeWithTag("manage-groups-dialog").assertExists()
        onNodeWithText("Close").performClick()
        onNodeWithText("Add Student").assertExists()
    }
}
