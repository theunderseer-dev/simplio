package com.theunderseer.simplio.feature.attendance.presentation.screen

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupRequest
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testAttendanceContentUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class)
class AttendanceScreenGroupCreationUiTest {
    @Test
    fun `add group opens form and forwards create request while add student remains available`() = runComposeUiTest {
        var request: CreateStudentGroupRequest? = null
        setContent {
            AttendanceScreenForTest(
                state = testAttendanceContentUiState(
                    Attendance(emptyList()),
                    AttendanceTestData.availablePackages,
                    AttendanceTestData.currentDate,
                ),
                onCreateStudentGroup = { request = it },
            )
        }
        onNodeWithText("Add Student").assertIsDisplayed()
        onNodeWithText("Add Group").performClick()
        onNodeWithTag("group-name").performTextInput("Private")
        onNodeWithText("Create").performClick()
        assertEquals("Private", assertNotNull(request).name)
    }
}
