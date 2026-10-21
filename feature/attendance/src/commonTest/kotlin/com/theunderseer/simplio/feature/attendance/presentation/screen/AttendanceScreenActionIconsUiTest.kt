package com.theunderseer.simplio.feature.attendance.presentation.screen

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.firstClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.student
import com.theunderseer.simplio.feature.attendance.fixtures.testPackageRenewalState
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentAttendanceUiState
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AttendanceScreenActionIconsUiTest {
    @Test
    fun `pending checked and renewal actions align with the delete action`() {
        val states = listOf(
            testStudentAttendanceUiState() to "Mark ${student.fullName} present",
            testStudentAttendanceUiState(currentDate = firstClassDate) to "Mark ${student.fullName} present",
            testPackageRenewalState() to "Renew package for ${student.fullName}",
        )
        for ((state, actionDescription) in states) {
            runComposeUiTest {
                setContent { AttendanceScreenForTest(state = state) }

                val action = onNodeWithContentDescription(actionDescription)
                    .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val delete = onNodeWithContentDescription("Delete ${student.fullName}")
                    .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertEquals(delete.width, action.width, 1f)
                assertEquals(delete.height, action.height, 1f)
                assertEquals(delete.center.y, action.center.y, 1f)
                assertEquals(action.width, action.height, 1f)
                onNodeWithText("♻️").assertDoesNotExist()
                onNodeWithText("✓").assertDoesNotExist()
            }
        }
    }
}
